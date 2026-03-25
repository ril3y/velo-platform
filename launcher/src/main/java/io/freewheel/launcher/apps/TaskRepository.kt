package io.freewheel.launcher.apps

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import io.freewheel.launcher.data.RunningApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TaskRepository(private val application: Application) {

    companion object {
        private val PROTECTED_PACKAGES = setOf(
            "io.freewheel.launcher",
            "io.freewheel.bridge",
        )
    }

    private val _runningApps = MutableStateFlow<List<RunningApp>>(emptyList())
    val runningApps: StateFlow<List<RunningApp>> = _runningApps.asStateFlow()

    suspend fun loadRunningApps() {
        withContext(Dispatchers.IO) {
            val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val procs = am.runningAppProcesses ?: return@withContext
            val pm = application.packageManager

            val running = procs.mapNotNull { proc ->
                val pkg = proc.processName.split(":").first()
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }
                am.getProcessMemoryInfo(intArrayOf(proc.pid)).firstOrNull()?.let { mem ->
                    RunningApp(pkg, label, mem.totalPss / 1024, proc.pid)
                }
            }.sortedByDescending { it.memoryMb }

            _runningApps.value = running
        }
    }

    fun killApp(packageName: String) {
        if (packageName in PROTECTED_PACKAGES) return
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)
    }

    fun killAllApps() {
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (app in _runningApps.value) {
            if (app.packageName !in PROTECTED_PACKAGES) {
                am.killBackgroundProcesses(app.packageName)
            }
        }
    }
}
