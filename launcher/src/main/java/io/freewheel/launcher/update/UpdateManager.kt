package io.freewheel.launcher.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.freewheel.launcher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateStatus {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR
}

class UpdateManager(
    private val application: Application,
    private val scope: CoroutineScope,
) {
    private val prefs = application.getSharedPreferences("velolauncher", android.content.Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _latestVersion = MutableStateFlow("")
    val latestVersion: StateFlow<String> = _latestVersion.asStateFlow()

    private val _changelog = MutableStateFlow("")
    val changelog: StateFlow<String> = _changelog.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private var apkDownloadUrl: String? = null
    private var downloadedApk: File? = null

    private val defaultOwner = "ril3y"
    private val defaultRepo = "velo-platform"

    private val owner: String get() = prefs.getString("update_repo_owner", defaultOwner) ?: defaultOwner
    private val repo: String get() = prefs.getString("update_repo", defaultRepo) ?: defaultRepo

    fun checkForUpdate() {
        if (_status.value == UpdateStatus.CHECKING || _status.value == UpdateStatus.DOWNLOADING) return

        _status.value = UpdateStatus.CHECKING
        scope.launch {
            try {
                val (version, changelog, apkUrl) = withContext(Dispatchers.IO) {
                    fetchLatestRelease()
                }

                _latestVersion.value = version
                _changelog.value = changelog
                apkDownloadUrl = apkUrl

                if (isNewer(version, BuildConfig.VERSION_NAME)) {
                    _status.value = UpdateStatus.AVAILABLE
                } else {
                    _status.value = UpdateStatus.UP_TO_DATE
                }
            } catch (_: Exception) {
                _status.value = UpdateStatus.ERROR
            }
        }
    }

    fun downloadUpdate() {
        val url = apkDownloadUrl ?: return
        if (_status.value == UpdateStatus.DOWNLOADING) return

        _status.value = UpdateStatus.DOWNLOADING
        _downloadProgress.value = 0f

        scope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(url)
                }
                downloadedApk = apkFile
                _status.value = UpdateStatus.READY
            } catch (_: Exception) {
                _status.value = UpdateStatus.ERROR
            }
        }
    }

    fun installUpdate() {
        val apk = downloadedApk ?: return

        val uri: Uri = FileProvider.getUriForFile(
            application,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        application.startActivity(intent)
    }

    private fun fetchLatestRelease(): Triple<String, String, String?> {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val conn = URL(apiUrl).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "VeloLauncher/${BuildConfig.VERSION_NAME}")
            val token = prefs.getString("update_github_token", null)
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "token $token")
            }
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val body = json.optString("body", "")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            return Triple(tagName, body, apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(url: String): File {
        val updatesDir = File(application.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "update.apk")

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VeloLauncher/${BuildConfig.VERSION_NAME}")
            val token = prefs.getString("update_github_token", null)
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "token $token")
            }
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

        try {
            val totalBytes = conn.contentLength.toLong()
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            _downloadProgress.value = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        }
                    }
                }
            }

            _downloadProgress.value = 1f
            return apkFile
        } finally {
            conn.disconnect()
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
