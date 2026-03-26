package io.freewheel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.freewheel.launcher.overlay.HomeButtonOverlay

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)

            // Start bottom-edge swipe gesture zone (so user can always get home)
            try {
                context.startForegroundService(Intent(context, HomeButtonOverlay::class.java))
            } catch (_: Exception) {}

            // Start SerialBridge (our own serial-to-TCP bridge, not Bowflex code)
            try {
                val bridgeIntent = Intent().apply {
                    setClassName("io.freewheel.bridge", "io.freewheel.bridge.BridgeService")
                }
                context.startForegroundService(bridgeIntent)
            } catch (_: Exception) {}
        }
    }
}
