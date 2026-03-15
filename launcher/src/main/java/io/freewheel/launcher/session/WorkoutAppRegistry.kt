package io.freewheel.launcher.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers and tracks installed workout apps that register with the launcher
 * via meta-data in their AndroidManifest.xml:
 *
 *   <meta-data android:name="io.freewheel.workout_app" android:value="true" />
 *   <meta-data android:name="io.freewheel.workout_description" android:value="..." />
 */
class WorkoutAppRegistry(private val context: Context) {

    companion object {
        private const val TAG = "WorkoutAppRegistry"
        private const val META_WORKOUT_APP = "io.freewheel.workout_app"
        private const val META_WORKOUT_DESC = "io.freewheel.workout_description"
    }

    data class WorkoutAppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val description: String,
        val supportsStructured: Boolean,
    )

    private val _apps = MutableStateFlow<List<WorkoutAppInfo>>(emptyList())
    val apps: StateFlow<List<WorkoutAppInfo>> = _apps.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.d(TAG, "Package change: ${intent.action} ${intent.data}")
            scan()
        }
    }

    fun start() {
        scan()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
    }

    fun stop() {
        try {
            context.unregisterReceiver(packageReceiver)
        } catch (_: Exception) {}
    }

    fun scan() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val workoutApps = mutableListOf<WorkoutAppInfo>()

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            try {
                val appInfo = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                val meta = appInfo.metaData ?: continue
                if (!meta.getBoolean(META_WORKOUT_APP, false)) continue

                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val description = meta.getString(META_WORKOUT_DESC, "")
                val supportsStructured = meta.getBoolean("io.freewheel.supports_structured", false)

                workoutApps.add(WorkoutAppInfo(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    description = description ?: "",
                    supportsStructured = supportsStructured,
                ))
            } catch (e: PackageManager.NameNotFoundException) {
                // Skip
            }
        }

        _apps.value = workoutApps
        Log.d(TAG, "Found ${workoutApps.size} workout apps: ${workoutApps.map { it.packageName }}")
    }
}
