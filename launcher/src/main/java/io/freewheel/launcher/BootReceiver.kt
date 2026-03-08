package io.freewheel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)

            // Start SerialBridge
            try {
                val bridgeIntent = Intent().apply {
                    setClassName("com.bowflex.serialbridge", "com.bowflex.serialbridge.SerialBridgeService")
                }
                context.startForegroundService(bridgeIntent)
            } catch (e: Exception) {
                // SerialBridge may not be installed
            }
        }
    }
}
