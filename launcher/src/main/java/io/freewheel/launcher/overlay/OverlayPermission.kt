package io.freewheel.launcher.overlay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * SYSTEM_ALERT_WINDOW ("Display over other apps") helpers. Needed by
 * RideOverlayService and HomeButtonOverlay. The installer normally grants it
 * via appops; these cover devices where that didn't happen.
 */
object OverlayPermission {

    private const val TAG = "OverlayPermission"

    /** True if overlays are allowed. Errs on true so a failing check never nags or blocks. */
    fun canDrawOverlays(context: Context): Boolean {
        return try {
            Settings.canDrawOverlays(context)
        } catch (e: Exception) {
            Log.w(TAG, "canDrawOverlays check failed", e)
            true
        }
    }

    /**
     * Opens the system "Display over other apps" screen for this app. Returns
     * false if the screen doesn't exist (stripped kiosk ROMs) or can't be opened.
     */
    fun openSettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Overlay permission screen not available", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open overlay permission screen", e)
            false
        }
    }
}
