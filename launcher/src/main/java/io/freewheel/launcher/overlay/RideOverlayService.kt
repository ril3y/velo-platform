package io.freewheel.launcher.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.freewheel.launcher.VeloLauncherApp
import io.freewheel.launcher.bridge.BridgeConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating overlay service that shows compact ride stats on top of media/game apps.
 * Uses TYPE_APPLICATION_OVERLAY (API 26+). Platform-signed apps on API 28 have implicit permission.
 */
class RideOverlayService : Service() {

    companion object {
        private const val TAG = "RideOverlay"

        fun start(context: Context) {
            context.startService(Intent(context, RideOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RideOverlayService::class.java))
        }
    }

    private lateinit var bridge: BridgeConnectionManager
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var expanded = true

    // Text views for stats
    private var tvPower: TextView? = null
    private var tvRpm: TextView? = null
    private var tvResistance: TextView? = null
    private var tvHeartRate: TextView? = null
    private var tvElapsed: TextView? = null

    override fun onCreate() {
        super.onCreate()
        bridge = VeloLauncherApp.get(this).bridgeConnectionManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        startDataCollection()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        removeOverlay()
    }

    private fun createOverlay() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC1A1B2E.toInt()) // semi-transparent dark
            setPadding(24, 16, 24, 16)
        }

        tvPower = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFF22D3EE.toInt()) // cyan accent
            text = "-- W"
        }
        tvRpm = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFE0E0E0.toInt())
            text = "-- RPM"
        }
        tvResistance = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFE0E0E0.toInt())
            text = "R: --"
        }
        tvHeartRate = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFF6B6B.toInt()) // red for HR
            text = "-- bpm"
        }
        tvElapsed = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFE0E0E0.toInt())
            text = "0:00"
        }

        container.addView(tvPower)
        container.addView(tvRpm)
        container.addView(tvResistance)
        container.addView(tvHeartRate)
        container.addView(tvElapsed)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 16
        }

        // Drag support
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dx < 10 && dy < 10) {
                        // Tap — toggle expand/collapse
                        toggleExpanded(container)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            overlayView = container
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun toggleExpanded(container: LinearLayout) {
        expanded = !expanded
        val visibility = if (expanded) View.VISIBLE else View.GONE
        tvRpm?.visibility = visibility
        tvResistance?.visibility = visibility
        tvHeartRate?.visibility = visibility
        tvElapsed?.visibility = visibility
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun startDataCollection() {
        scope.launch {
            bridge.sensorData.collect { data ->
                tvPower?.text = "${data.power.toInt()} W"
                tvRpm?.text = "${data.rpm} RPM"
                tvResistance?.text = "R: ${data.resistanceLevel}"
            }
        }
        scope.launch {
            bridge.heartRate.collect { hr ->
                tvHeartRate?.text = if (hr > 0) "$hr bpm" else "-- bpm"
            }
        }
        scope.launch {
            bridge.sessionState.collect { session ->
                if (session.active) {
                    tvElapsed?.text = formatElapsed(session.elapsedSeconds)
                }
            }
        }
        // Self-stop when workout ends
        scope.launch {
            bridge.workoutActive.collect { active ->
                if (!active) {
                    stopSelf()
                }
            }
        }
    }

    private fun formatElapsed(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
}
