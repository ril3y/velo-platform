package io.freewheel.launcher.apps

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import io.freewheel.launcher.data.AppInfo
import io.freewheel.launcher.data.HomeTile
import io.freewheel.launcher.data.TileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppRepository(private val application: Application) {

    // Fallback known packages for category detection when meta-data is absent
    private val knownMediaPackages = setOf(
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.hbo.hbonow",
        "com.google.android.youtube",
        "com.amazon.avod.thirdpartyclient",
        "com.hulu.plus",
        "com.spotify.music",
        "com.plexapp.android",
    )

    private val knownFitnessPackages = setOf(
        "com.nautilus.bowflex.usb",
        "io.freewheel.freeride",
        "com.bikearcade.app",
    )

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _fitnessApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val fitnessApps: StateFlow<List<AppInfo>> = _fitnessApps.asStateFlow()

    private val _recentApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentApps: StateFlow<List<AppInfo>> = _recentApps.asStateFlow()

    suspend fun loadApps() {
        withContext(Dispatchers.IO) {
            val pm = application.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            val myPkg = application.packageName

            val apps = resolveInfos
                .filter { it.activityInfo.packageName != myPkg }
                .map { ri ->
                    val pkg = ri.activityInfo.packageName
                    val label = ri.loadLabel(pm).toString()
                    val icon = ri.loadIcon(pm)
                    val category = detectCategory(pm, pkg)
                    AppInfo(pkg, label, icon, true, category)
                }
                .sortedWith(compareBy({ it.category.ordinal }, { it.label.lowercase() }))

            _allApps.value = apps
            _fitnessApps.value = apps.filter { it.category == TileCategory.FITNESS }
            updateRecentApps()
        }
    }

    private fun detectCategory(pm: PackageManager, pkg: String): TileCategory {
        // Check meta-data first (app.category.primary)
        try {
            val appInfo = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            val metaCategory = appInfo.metaData?.getString("app.category.primary")
            if (metaCategory != null) {
                return when (metaCategory.lowercase()) {
                    "fitness" -> TileCategory.FITNESS
                    "media" -> TileCategory.MEDIA
                    "system" -> TileCategory.SYSTEM
                    else -> TileCategory.APP
                }
            }
        } catch (_: PackageManager.NameNotFoundException) {}

        // Fallback to known packages
        return when (pkg) {
            in knownFitnessPackages -> TileCategory.FITNESS
            in knownMediaPackages -> TileCategory.MEDIA
            "com.android.settings" -> TileCategory.SYSTEM
            else -> TileCategory.APP
        }
    }

    private fun updateRecentApps() {
        // Use Android usage stats if available, otherwise track launches ourselves
        val prefs = application.getSharedPreferences("app_launches", 0)
        val recentPkgs = prefs.getString("recent_order", "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val allAppMap = _allApps.value.associateBy { it.packageName }
        _recentApps.value = recentPkgs.mapNotNull { allAppMap[it] }.take(6)
    }

    fun getHomeTiles(): List<HomeTile> {
        val apps = _allApps.value
        val tiles = mutableListOf<HomeTile>()

        // START RIDE tile (always first)
        tiles.add(HomeTile.StartRide)

        // Dynamically discovered fitness apps
        val fitnessApps = apps.filter { it.category == TileCategory.FITNESS }
        for (app in fitnessApps) {
            tiles.add(HomeTile.App(app.packageName, app.label, app.icon, app.isInstalled, TileCategory.FITNESS))
        }

        // Media apps (dynamically detected)
        val mediaApps = apps.filter { it.category == TileCategory.MEDIA }
        for (app in mediaApps) {
            tiles.add(HomeTile.App(app.packageName, app.label, app.icon, app.isInstalled, TileCategory.MEDIA))
        }

        return tiles
    }

    fun launchApp(packageName: String) {
        val pm = application.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
        trackLaunch(packageName)
    }

    private fun trackLaunch(packageName: String) {
        val prefs = application.getSharedPreferences("app_launches", 0)
        val existing = prefs.getString("recent_order", "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()

        existing.remove(packageName)
        existing.add(0, packageName)

        // Keep last 20
        val trimmed = existing.take(20)
        prefs.edit().putString("recent_order", trimmed.joinToString(",")).apply()

        // Update state
        val allAppMap = _allApps.value.associateBy { it.packageName }
        _recentApps.value = trimmed.mapNotNull { allAppMap[it] }.take(6)
    }
}
