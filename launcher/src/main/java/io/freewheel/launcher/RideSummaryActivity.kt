package io.freewheel.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.freewheel.launcher.data.RideSummary
import io.freewheel.launcher.ui.RideSummaryScreen
import io.freewheel.launcher.ui.theme.VeloLauncherTheme

/**
 * Standalone activity that displays a ride summary screen.
 *
 * External apps (e.g. BikeArcade) can launch this via an explicit intent action
 * after completing a workout to give the user a consistent post-ride experience.
 *
 * Intent action: `io.freewheel.launcher.SHOW_RIDE_SUMMARY`
 *
 * Expected extras:
 *  - durationSeconds: Int
 *  - calories: Int
 *  - avgPower: Int
 *  - maxPower: Int
 *  - avgRpm: Int
 *  - avgResistance: Int
 *  - avgHeartRate: Int
 *  - avgSpeedMph: Float
 *  - distanceMiles: Float
 *  - workoutName: String? (optional)
 */
class RideSummaryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        val summary = RideSummary(
            durationSeconds = extras?.getInt("durationSeconds", 0) ?: 0,
            calories = extras?.getInt("calories", 0) ?: 0,
            avgPower = extras?.getInt("avgPower", 0) ?: 0,
            maxPower = extras?.getInt("maxPower", 0) ?: 0,
            avgRpm = extras?.getInt("avgRpm", 0) ?: 0,
            avgResistance = extras?.getInt("avgResistance", 0) ?: 0,
            avgHeartRate = extras?.getInt("avgHeartRate", 0) ?: 0,
            avgSpeedMph = extras?.getFloat("avgSpeedMph", 0f) ?: 0f,
            distanceMiles = extras?.getFloat("distanceMiles", 0f) ?: 0f,
            workoutName = extras?.getString("workoutName"),
        )

        setContent {
            VeloLauncherTheme {
                RideSummaryScreen(
                    summary = summary,
                    onDone = { finish() },
                )
            }
        }
    }
}
