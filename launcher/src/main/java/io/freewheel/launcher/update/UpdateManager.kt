package io.freewheel.launcher.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
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

data class AppUpdate(
    val name: String,
    val url: String,
    val label: String,
    val packageName: String,
    val installedVersion: String?,
    val remoteVersion: String,
)

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

    private val _availableUpdates = MutableStateFlow<List<AppUpdate>>(emptyList())
    val availableUpdates: StateFlow<List<AppUpdate>> = _availableUpdates.asStateFlow()

    private var apkDownloadUrl: String? = null
    private var downloadedApk: File? = null

    private val defaultOwner = "ril3y"
    private val defaultRepo = "velo-platform"

    private val owner: String get() = prefs.getString("update_repo_owner", defaultOwner) ?: defaultOwner
    private val repo: String get() = prefs.getString("update_repo", defaultRepo) ?: defaultRepo

    companion object {
        private const val TAG = "UpdateManager"
        private const val PREF_LAST_UPDATE_CHECK = "last_update_check"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

        /** Maps APK filename prefix (label) to Android package name for all monorepo apps. */
        private val LABEL_TO_PACKAGE = mapOf(
            "launcher" to "io.freewheel.launcher",
            "freeride" to "io.freewheel.freeride",
            "freewheelbridge" to "io.freewheel.bridge",
        )
    }

    /**
     * Checks if an update check is due (more than 24 hours since last check, or never checked).
     * If due, automatically calls [checkForUpdate] and records the timestamp.
     */
    fun checkIfDue() {
        val lastCheck = prefs.getLong(PREF_LAST_UPDATE_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck >= CHECK_INTERVAL_MS) {
            checkForUpdate()
            prefs.edit().putLong(PREF_LAST_UPDATE_CHECK, now).apply()
        }
    }

    fun checkForUpdate() {
        if (_status.value == UpdateStatus.CHECKING || _status.value == UpdateStatus.DOWNLOADING) return

        _status.value = UpdateStatus.CHECKING
        scope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    fetchLatestRelease()
                }

                _latestVersion.value = release.version
                _changelog.value = release.changelog

                // Check each APK against the installed version of its target package.
                // Only include updates for apps that are installed AND outdated.
                val pm = application.packageManager
                val updatable = release.updates.mapNotNull { update ->
                    val pkg = update.packageName
                    val installedVersion = try {
                        pm.getPackageInfo(pkg, 0).versionName
                    } catch (_: PackageManager.NameNotFoundException) {
                        null  // App not installed — skip
                    }
                    if (installedVersion != null && isNewer(update.remoteVersion, installedVersion)) {
                        update.copy(installedVersion = installedVersion)
                    } else null
                }

                // Keep first APK URL for legacy single-download path
                apkDownloadUrl = updatable.firstOrNull()?.url

                if (updatable.isNotEmpty()) {
                    _availableUpdates.value = updatable
                    _status.value = UpdateStatus.AVAILABLE
                    Log.i(TAG, "Updates available: ${updatable.map { "${it.label} ${it.installedVersion} → ${it.remoteVersion}" }}")
                } else {
                    _availableUpdates.value = emptyList()
                    _status.value = UpdateStatus.UP_TO_DATE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _status.value = UpdateStatus.ERROR
            }
        }
    }

    /** Download the first available update (legacy single-APK path). */
    fun downloadUpdate() {
        val url = apkDownloadUrl ?: return
        if (_status.value == UpdateStatus.DOWNLOADING) return

        _status.value = UpdateStatus.DOWNLOADING
        _downloadProgress.value = 0f

        scope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(url, "update.apk")
                }
                downloadedApk = apkFile
                _status.value = UpdateStatus.READY
            } catch (_: Exception) {
                _status.value = UpdateStatus.ERROR
            }
        }
    }

    /** Download a specific APK from the available updates list. */
    fun downloadUpdate(appUpdate: AppUpdate) {
        if (_status.value == UpdateStatus.DOWNLOADING) return

        _status.value = UpdateStatus.DOWNLOADING
        _downloadProgress.value = 0f

        scope.launch {
            try {
                val filename = "${appUpdate.label}-update.apk"
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(appUpdate.url, filename)
                }
                downloadedApk = apkFile
                _status.value = UpdateStatus.READY
            } catch (_: Exception) {
                _status.value = UpdateStatus.ERROR
            }
        }
    }

    /** Install the most recently downloaded APK. */
    fun installUpdate() {
        val apk = downloadedApk ?: return
        installUpdate(apk)
    }

    /** Install any downloaded APK file. */
    fun installUpdate(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            application,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        application.startActivity(intent)
    }

    private data class ReleaseInfo(
        val version: String,
        val changelog: String,
        val updates: List<AppUpdate>,
    )

    private fun fetchLatestRelease(): ReleaseInfo {
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

            val updates = mutableListOf<AppUpdate>()
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        val url = asset.optString("browser_download_url")
                        // Derive label from filename: "launcher-1.0.0.apk" -> "launcher"
                        val label = name.substringBefore("-").ifBlank { name.removeSuffix(".apk") }
                        val packageName = LABEL_TO_PACKAGE[label] ?: "io.freewheel.$label"
                        updates.add(AppUpdate(
                            name = name,
                            url = url,
                            label = label,
                            packageName = packageName,
                            installedVersion = null, // filled in by checkForUpdate()
                            remoteVersion = tagName,
                        ))
                    }
                }
            }

            return ReleaseInfo(version = tagName, changelog = body, updates = updates)
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(url: String, filename: String): File {
        val updatesDir = File(application.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, filename)

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
