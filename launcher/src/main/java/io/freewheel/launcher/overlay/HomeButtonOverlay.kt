package io.freewheel.launcher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Bottom-edge swipe gesture overlay — replaces the old floating HOME button.
 * A thin invisible strip at the bottom of the screen detects upward swipes
 * and navigates home. Works like Android's gesture navigation bar.
 *
 * Always shown when an app is in foreground (including during workouts).
 * Doesn't interfere with app UI since it's invisible and only responds to swipes.
 */
class HomeButtonOverlay : Service() {

    companion object {
        private const val TAG = "HomeGesture"
        private const val SWIPE_THRESHOLD_DP = 60  // minimum upward swipe distance

        fun show(context: Context) {
            context.startService(Intent(context, HomeButtonOverlay::class.java))
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, HomeButtonOverlay::class.java))
        }

        private const val NOTIFICATION_ID = 1002
    }

    private var windowManager: WindowManager? = null
    private var edgeView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Run as foreground service so Android doesn't kill it
        val channelId = "home_gesture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Navigation", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        createEdgeZone()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeEdgeZone()
    }

    private fun createEdgeZone() {
        val dp = resources.displayMetrics.density
        val edgeHeight = (20 * dp).toInt()  // thin strip at bottom
        val swipeThreshold = SWIPE_THRESHOLD_DP * dp

        val zone = View(this).apply {
            // Fully transparent — invisible to the user
            setBackgroundColor(Color.TRANSPARENT)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            edgeHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
        }

        var downY = 0f
        var downTime = 0L

        zone.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaY = downY - event.rawY  // positive = upward swipe
                    val deltaTime = System.currentTimeMillis() - downTime
                    if (deltaY > swipeThreshold && deltaTime < 500) {
                        // Swipe up detected — go home
                        goHome()
                    }
                    true
                }
                else -> true
            }
        }

        try {
            windowManager?.addView(zone, params)
            edgeView = zone
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add edge gesture zone", e)
        }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun removeEdgeZone() {
        edgeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        edgeView = null
    }
}
