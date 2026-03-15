package io.freewheel.freeride

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.freewheel.freeride.ui.FreeRideScreen
import io.freewheel.freeride.ui.RideSummaryScreen
import io.freewheel.freeride.ui.WorkoutRideScreen
import io.freewheel.freeride.ui.theme.FreeRideTheme

class RideActivity : ComponentActivity() {

    private var rideViewModel: RideViewModel? = null
    private var currentWorkout: WorkoutData? = null
    private var mediaPackage: String? = null
    private var isOverlayMode = false

    override fun onResume() {
        super.onResume()
        rideViewModel?.reconnect()
        hideSystemUI()
    }

    override fun onStop() {
        super.onStop()
        // If user navigated away (home button, launcher, etc.) in full-screen mode,
        // stop the ride and release the bridge so it doesn't stay locked.
        // Don't do this in overlay mode — the activity intentionally goes to background.
        if (!isOverlayMode && !isFinishing) {
            val vm = rideViewModel
            if (vm != null && vm.rideActive.value) {
                val w = currentWorkout
                vm.stopRide(workoutId = w?.id, workoutName = w?.name)
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        // Parse workout from intent extras (if launched by VeloLauncher)
        val workoutJson = intent.getStringExtra("workout_json")
        val workout = workoutJson?.let {
            try { WorkoutData.fromJson(it) } catch (_: Exception) { null }
        }
        currentWorkout = workout
        val isFreeRide = intent.getBooleanExtra("free_ride", workout == null)
        mediaPackage = intent.getStringExtra("media_package")

        // If media app is specified, use overlay mode
        if (mediaPackage != null) {
            isOverlayMode = true
            startOverlayMode(workoutJson, mediaPackage!!)
            return
        }

        // Full-screen ride mode (no media)
        setContent {
            FreeRideTheme {
                val vm: RideViewModel = viewModel()
                rideViewModel = vm

                var rideSummary by remember { mutableStateOf<RideSummary?>(null) }

                // Auto-start ride on launch
                LaunchedEffect(Unit) {
                    vm.startRide()
                }

                if (rideSummary != null) {
                    RideSummaryScreen(
                        summary = rideSummary!!,
                        onDone = { finish() },
                    )
                } else if (workout != null && !isFreeRide) {
                    WorkoutRideScreen(
                        vm = vm,
                        workout = workout,
                        onEndRide = {
                            val summary = vm.stopRide(workoutId = workout.id, workoutName = workout.name)
                            if (summary != null && summary.durationSeconds >= 60) {
                                rideSummary = summary
                            } else {
                                finish()
                            }
                        },
                    )
                } else {
                    FreeRideScreen(
                        vm = vm,
                        onStop = {
                            val summary = vm.stopRide()
                            if (summary != null && summary.durationSeconds >= 60) {
                                rideSummary = summary
                            } else {
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun startOverlayMode(workoutJson: String?, mediaPackage: String) {
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // Request permission
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
            // Store for retry after permission
            pendingWorkoutJson = workoutJson
            pendingMediaPackage = mediaPackage
            return
        }

        // Start overlay service
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
            if (workoutJson != null) putExtra("workout_json", workoutJson)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Launch media app
        val launchIntent = packageManager.getLaunchIntentForPackage(mediaPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }

        // Move to back — keep process alive for the foreground overlay service
        moveTaskToBack(true)
    }

    private var pendingWorkoutJson: String? = null
    private var pendingMediaPackage: String? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // Permission granted — retry
                pendingMediaPackage?.let { startOverlayMode(pendingWorkoutJson, it) }
            } else {
                // Permission denied — fall back to full-screen ride
                finish()
            }
        }
    }

    private var backPressedTime = 0L

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - backPressedTime < 2000) {
            // Confirmed — end workout
            val vm = rideViewModel
            if (vm != null) {
                val w = currentWorkout
                vm.stopRide(workoutId = w?.id, workoutName = w?.name)
            }
            stopMediaPlayback()
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            stopService(stopIntent)
            finish()
        } else {
            backPressedTime = now
            android.widget.Toast.makeText(this, "Press back again to end workout", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Send MEDIA_PAUSE key event to stop any playing media. */
    private fun stopMediaPlayback() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    companion object {
        private const val REQUEST_OVERLAY = 100
    }
}
