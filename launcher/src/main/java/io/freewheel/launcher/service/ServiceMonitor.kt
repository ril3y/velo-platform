package io.freewheel.launcher.service

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ServiceStatus(
    val serialBridgeRunning: Boolean = false,
    val serialBridgeTcpAlive: Boolean = false,
)

class ServiceMonitor(private val context: Context) {

    companion object {
        private const val SERIAL_BRIDGE_PKG = "io.freewheel.bridge"
        private const val SERIAL_BRIDGE_SERVICE = "io.freewheel.bridge.BridgeService"
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

        for (info in services) {
            val cls = info.service.className
            if (cls == SERIAL_BRIDGE_SERVICE) bridgeRunning = true
        }

        val tcpAlive = BridgeHealthCheck.isAlive()

        return ServiceStatus(
            serialBridgeRunning = bridgeRunning,
            serialBridgeTcpAlive = tcpAlive,
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
}
