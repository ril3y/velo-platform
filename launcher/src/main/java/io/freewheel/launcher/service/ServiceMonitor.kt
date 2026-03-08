package io.freewheel.launcher.service

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ServiceStatus(
    val serialBridgeRunning: Boolean = false,
    val serialBridgeTcpAlive: Boolean = false,
    val overlayRunning: Boolean = false,
)

class ServiceMonitor(private val context: Context) {

    companion object {
        private const val SERIAL_BRIDGE_PKG = "com.bowflex.serialbridge"
        private const val SERIAL_BRIDGE_SERVICE = "com.bowflex.serialbridge.SerialBridgeService"
        private const val OVERLAY_PKG = "com.bowflex.jailbreak"
        private const val OVERLAY_SERVICE = "com.bowflex.jailbreak.OverlayService"
        private const val POLL_INTERVAL_MS = 10_000L
    }

    fun statusFlow(): Flow<ServiceStatus> = flow {
        while (true) {
            val status = checkAll()
            emit(status)
            delay(POLL_INTERVAL_MS)
        }
    }

    suspend fun checkAll(): ServiceStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = am.getRunningServices(100)

        var bridgeRunning = false
        var overlayRunning = false

        for (info in services) {
            val cls = info.service.className
            if (cls == SERIAL_BRIDGE_SERVICE) bridgeRunning = true
            if (cls == OVERLAY_SERVICE) overlayRunning = true
        }

        val tcpAlive = BridgeHealthCheck.isAlive()

        return ServiceStatus(
            serialBridgeRunning = bridgeRunning,
            serialBridgeTcpAlive = tcpAlive,
            overlayRunning = overlayRunning,
        )
    }

    fun restartSerialBridge() {
        try {
            Runtime.getRuntime().exec(arrayOf(
                "am", "startservice",
                "-n", "$SERIAL_BRIDGE_PKG/$SERIAL_BRIDGE_SERVICE"
            ))
        } catch (_: Exception) {}
    }

    fun restartOverlay() {
        try {
            Runtime.getRuntime().exec(arrayOf(
                "am", "startservice",
                "-n", "$OVERLAY_PKG/$OVERLAY_SERVICE"
            ))
        } catch (_: Exception) {}
    }
}
