package io.freewheel.launcher.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object BridgeHealthCheck {
    private const val HOST = "127.0.0.1"
    private const val PORT = 9999
    private const val TIMEOUT_MS = 2000

    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST, PORT), TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
