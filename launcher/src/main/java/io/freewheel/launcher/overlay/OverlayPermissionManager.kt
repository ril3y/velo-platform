// src/main/java/io/freewheel/launcher/overlay/OverlayPermissionManager.kt
package io.freewheel.launcher.overlay

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class OverlayPermissionManager(private val activity: ComponentActivity) {

    private var pendingAction: (() -> Unit)? = null

    // This MUST be initialized before the Activity reaches the STARTED state
    private val launcher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasPermission()) {
            // Start the background home button just in case
            try {
                val serviceIntent = Intent(activity, HomeButtonOverlay::class.java)
                ContextCompat.startForegroundService(activity, serviceIntent)
            } catch (_: Exception) {}

            pendingAction?.invoke()
        }
        pendingAction = null
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true // Older Android versions grant this at install time
        }
    }

    fun requestPermission(onGranted: () -> Unit) {
        if (hasPermission()) {
            onGranted()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingAction = onGranted
            AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage("To show your live ride stats over other apps (like Netflix or YouTube), VeloLauncher needs the 'Display over other apps' permission.\n\nPlease enable it on the next screen.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    launcher.launch(intent)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    pendingAction = null
                }
                .setCancelable(false)
                .show()
        }
    }
}