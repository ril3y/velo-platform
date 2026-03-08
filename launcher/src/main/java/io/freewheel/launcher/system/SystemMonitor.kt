package io.freewheel.launcher.system

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SystemMonitor(
    private val application: Application,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
    }

    private val _wifiSsid = MutableStateFlow("")
    val wifiSsid: StateFlow<String> = _wifiSsid.asStateFlow()

    private val _ramUsed = MutableStateFlow(0L)
    val ramUsed: StateFlow<Long> = _ramUsed.asStateFlow()

    private val _ramTotal = MutableStateFlow(0L)
    val ramTotal: StateFlow<Long> = _ramTotal.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                updateSystemInfo()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun updateSystemInfo() {
        // WiFi
        try {
            val wm = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            @Suppress("DEPRECATION")
            _wifiSsid.value = info?.ssid?.replace("\"", "") ?: ""
        } catch (_: Exception) {
            _wifiSsid.value = ""
        }

        // RAM
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        _ramTotal.value = memInfo.totalMem / (1024 * 1024)
        _ramUsed.value = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
    }
}
