package io.freewheel.launcher.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Floating home button overlay that appears when an app is launched from the launcher.
 * Provides a small draggable pill in the bottom-left corner that returns the user
 * to the VeloLauncher home screen. Auto-dismisses when the launcher resumes.
 */
class HomeButtonOverlay : Service() {

    companion object {
        private const val TAG = "HomeButtonOverlay"

        fun show(context: Context) {
            context.startService(Intent(context, HomeButtonOverlay::class.java))
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, HomeButtonOverlay::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var buttonView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeButton()
    }

    private fun createButton() {
        val dp = resources.displayMetrics.density

        val btn = TextView(this).apply {
            text = "\u25C0 HOME"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC1A1B2E"))
                cornerRadius = 20 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#4422D3EE"))
            }
            alpha = 0.7f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = (16 * dp).toInt()
            y = (16 * dp).toInt()
        }

        // Drag + tap support
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    v.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY - (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(v, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 0.7f
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dx < 10 && dy < 10) {
                        goHome()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(btn, params)
            buttonView = btn
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add home button overlay", e)
        }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun removeButton() {
        buttonView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        buttonView = null
    }
}
