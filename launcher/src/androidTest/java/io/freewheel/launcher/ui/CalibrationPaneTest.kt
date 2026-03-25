package io.freewheel.launcher.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.freewheel.launcher.ui.util.setThemedContent
import org.junit.Rule
import org.junit.Test

class CalibrationPaneTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun idleStateShowsStartButton() {
        composeTestRule.setThemedContent {
            CalibrationPane(
                state = CalibrationState(),
                onStartCalibration = {},
                onConfirmStep = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithText("RESISTANCE CALIBRATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Calibration").assertIsDisplayed()
    }

    @Test
    fun activeStateShowsInstruction() {
        composeTestRule.setThemedContent {
            CalibrationPane(
                state = CalibrationState(
                    isActive = true,
                    currentStep = 0,
                    totalSteps = 3,
                    instruction = "Turn the resistance knob fully left to zero.",
                ),
                onStartCalibration = {},
                onConfirmStep = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Turn the resistance knob fully left to zero.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun completedSuccessShowsResult() {
        composeTestRule.setThemedContent {
            CalibrationPane(
                state = CalibrationState(
                    completed = true,
                    success = true,
                ),
                onStartCalibration = {},
                onConfirmStep = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithText("Calibration Complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resistance knob is now calibrated. Values will take effect immediately.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Recalibrate").assertIsDisplayed()
    }

    @Test
    fun completedCancelledShowsCancelledMessage() {
        composeTestRule.setThemedContent {
            CalibrationPane(
                state = CalibrationState(
                    completed = true,
                    success = false,
                ),
                onStartCalibration = {},
                onConfirmStep = {},
                onCancel = {},
            )
        }

        composeTestRule.onNodeWithText("Calibration Cancelled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recalibrate").assertIsDisplayed()
    }
}
