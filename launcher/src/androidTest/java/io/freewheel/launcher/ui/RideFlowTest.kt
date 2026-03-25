package io.freewheel.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.freewheel.launcher.data.MediaApp
import io.freewheel.launcher.data.RideSummary
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.data.WorkoutSegment
import io.freewheel.launcher.ui.util.setThemedContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Test data helpers ──────────────────────────────────────────────

    private fun sampleWorkout() = Workout(
        id = "test-workout",
        name = "Test Hill Climb",
        description = "A test workout",
        durationMinutes = 10,
        type = "cycling",
        category = "Endurance",
        coach = "Test Coach",
        optionalMedia = true,
        color = "#22D3EE",
        segments = listOf(
            WorkoutSegment(label = "Warm Up", durationSeconds = 120, resistance = 5),
            WorkoutSegment(label = "Climb", durationSeconds = 300, resistance = 15),
            WorkoutSegment(label = "Cool Down", durationSeconds = 180, resistance = 3),
        ),
    )

    private fun sampleSummary() = RideSummary(
        durationSeconds = 600,
        calories = 250,
        avgPower = 150,
        maxPower = 280,
        avgRpm = 75,
        avgResistance = 10,
        avgHeartRate = 135,
        avgSpeedMph = 15.5f,
        distanceMiles = 2.8f,
        workoutName = "Test Hill Climb",
    )

    // ── FreeRideScreen tests ───────────────────────────────────────────

    @Test
    fun freeRideScreen_displaysAllMetrics() {
        composeTestRule.setThemedContent {
            FreeRideScreen(
                power = 150,
                rpm = 80,
                resistance = 12,
                calories = 200,
                elapsedSeconds = 300,
                speedMph = 18.5f,
                distanceMiles = 3.2f,
                heartRate = 140,
                isConnected = true,
                onStop = {},
            )
        }

        // Primary metrics
        composeTestRule.onNodeWithText("POWER").assertIsDisplayed()
        composeTestRule.onNodeWithText("CADENCE").assertIsDisplayed()
        composeTestRule.onNodeWithText("ELAPSED").assertIsDisplayed()

        // Secondary metrics
        composeTestRule.onNodeWithText("200").assertIsDisplayed()        // calories
        composeTestRule.onNodeWithText("CAL").assertIsDisplayed()
        composeTestRule.onNodeWithText("18.5").assertIsDisplayed()       // speed
        composeTestRule.onNodeWithText("MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.2").assertIsDisplayed()        // distance
        composeTestRule.onNodeWithText("MI").assertIsDisplayed()
        composeTestRule.onNodeWithText("LVL 12").assertIsDisplayed()     // resistance
        composeTestRule.onNodeWithText("RES").assertIsDisplayed()
        composeTestRule.onNodeWithText("140").assertIsDisplayed()        // heart rate
        composeTestRule.onNodeWithText("BPM").assertIsDisplayed()

        // Timer (05:00)
        composeTestRule.onNodeWithText("05:00").assertIsDisplayed()
    }

    @Test
    fun freeRideScreen_stopButton_callsCallback() {
        var stopClicked = false
        composeTestRule.setThemedContent {
            FreeRideScreen(
                power = 100,
                rpm = 70,
                resistance = 8,
                calories = 50,
                elapsedSeconds = 60,
                speedMph = 12.0f,
                distanceMiles = 0.5f,
                heartRate = 120,
                isConnected = true,
                onStop = { stopClicked = true },
            )
        }

        composeTestRule.onNodeWithText("END WORKOUT").performClick()
        assertTrue("onStop callback should have been invoked", stopClicked)
    }

    @Test
    fun freeRideScreen_disconnected_showsStatus() {
        composeTestRule.setThemedContent {
            FreeRideScreen(
                power = 0,
                rpm = 0,
                resistance = 0,
                calories = 0,
                elapsedSeconds = 0,
                speedMph = 0f,
                distanceMiles = 0f,
                heartRate = 0,
                isConnected = false,
                onStop = {},
            )
        }

        composeTestRule.onNodeWithText("CONNECTING...").assertIsDisplayed()
    }

    // ── WorkoutRideScreen tests ────────────────────────────────────────

    @Test
    fun workoutRideScreen_displaysWorkoutName() {
        val workout = sampleWorkout()
        composeTestRule.setThemedContent {
            WorkoutRideScreen(
                workout = workout,
                power = 120,
                rpm = 75,
                resistance = 10,
                calories = 100,
                elapsedSeconds = 60,
                speedMph = 14.0f,
                distanceMiles = 0.4f,
                heartRate = 130,
                isConnected = true,
                powerHistory = listOf(100, 110, 120),
                ftp = 200,
                onEndRide = {},
            )
        }

        composeTestRule.onNodeWithText("Test Hill Climb").assertIsDisplayed()
    }

    @Test
    fun workoutRideScreen_displaysSegmentInfo() {
        val workout = sampleWorkout()
        composeTestRule.setThemedContent {
            WorkoutRideScreen(
                workout = workout,
                power = 120,
                rpm = 75,
                resistance = 10,
                calories = 100,
                elapsedSeconds = 60,
                speedMph = 14.0f,
                distanceMiles = 0.4f,
                heartRate = 130,
                isConnected = true,
                powerHistory = listOf(100, 110, 120),
                ftp = 200,
                onEndRide = {},
            )
        }

        // At 60s elapsed we are in segment 1 (Warm Up, 0-120s), so next is "Climb"
        composeTestRule.onNodeWithText("Climb").assertIsDisplayed()
        composeTestRule.onNodeWithText("UPCOMING").assertIsDisplayed()
        // Segment progress text: "segment 1 of 3"
        composeTestRule.onNodeWithText("segment 1 of 3", substring = true).assertIsDisplayed()
    }

    @Test
    fun workoutRideScreen_endRideButton_callsCallback() {
        var endRideClicked = false
        val workout = sampleWorkout()
        composeTestRule.setThemedContent {
            WorkoutRideScreen(
                workout = workout,
                power = 120,
                rpm = 75,
                resistance = 10,
                calories = 100,
                elapsedSeconds = 60,
                speedMph = 14.0f,
                distanceMiles = 0.4f,
                heartRate = 130,
                isConnected = true,
                powerHistory = listOf(100, 110, 120),
                ftp = 200,
                onEndRide = { endRideClicked = true },
            )
        }

        composeTestRule.onNodeWithText("End Ride").performClick()
        assertTrue("onEndRide callback should have been invoked", endRideClicked)
    }

    // ── RideSummaryScreen tests ────────────────────────────────────────

    @Test
    fun rideSummaryScreen_displaysSummaryStats() {
        val summary = sampleSummary()
        composeTestRule.setThemedContent {
            RideSummaryScreen(
                summary = summary,
                onDone = {},
            )
        }

        // Duration: 600s = 10:00
        composeTestRule.onNodeWithText("10:00").assertIsDisplayed()
        // Calories
        composeTestRule.onNodeWithText("250").assertIsDisplayed()
        // Avg power
        composeTestRule.onNodeWithText("150").assertIsDisplayed()
        // Max power
        composeTestRule.onNodeWithText("280").assertIsDisplayed()
        // Avg RPM
        composeTestRule.onNodeWithText("75").assertIsDisplayed()
        // Labels
        composeTestRule.onNodeWithText("AVG POWER").assertIsDisplayed()
        composeTestRule.onNodeWithText("AVG CADENCE").assertIsDisplayed()
        composeTestRule.onNodeWithText("CALORIES").assertIsDisplayed()
    }

    @Test
    fun rideSummaryScreen_doneButton_callsCallback() {
        var doneClicked = false
        composeTestRule.setThemedContent {
            RideSummaryScreen(
                summary = sampleSummary(),
                onDone = { doneClicked = true },
            )
        }

        composeTestRule.onNodeWithText("DONE").performClick()
        assertTrue("onDone callback should have been invoked", doneClicked)
    }

    @Test
    fun rideSummaryScreen_showsWorkoutName() {
        composeTestRule.setThemedContent {
            RideSummaryScreen(
                summary = sampleSummary(),
                onDone = {},
            )
        }

        composeTestRule.onNodeWithText("Test Hill Climb").assertIsDisplayed()
        composeTestRule.onNodeWithText("RIDE COMPLETE").assertIsDisplayed()
    }

    // ── WorkoutDetailScreen integration tests ──────────────────────────

    @Test
    fun workoutDetailScreen_startWithMedia_buttonExists() {
        composeTestRule.setThemedContent {
            WorkoutDetailScreen(
                workout = sampleWorkout(),
                mediaApps = listOf(
                    MediaApp("Netflix", "com.netflix.mediaclient", Color(0xFFE50914), "N"),
                ),
                selectedMedia = MediaApp("Netflix", "com.netflix.mediaclient", Color(0xFFE50914), "N"),
                onMediaSelect = {},
                onStartWithMedia = {},
                onStartStatsOnly = {},
                onBack = {},
                ramUsedMb = 512,
                ramTotalMb = 1024,
                currentTime = System.currentTimeMillis(),
            )
        }

        composeTestRule.onNodeWithText("Start with Media").assertIsDisplayed()
    }

    @Test
    fun workoutDetailScreen_statsOnly_buttonExists() {
        composeTestRule.setThemedContent {
            WorkoutDetailScreen(
                workout = sampleWorkout(),
                mediaApps = emptyList(),
                selectedMedia = null,
                onMediaSelect = {},
                onStartWithMedia = {},
                onStartStatsOnly = {},
                onBack = {},
                ramUsedMb = 512,
                ramTotalMb = 1024,
                currentTime = System.currentTimeMillis(),
            )
        }

        composeTestRule.onNodeWithText("Stats Only").assertIsDisplayed()
    }
}
