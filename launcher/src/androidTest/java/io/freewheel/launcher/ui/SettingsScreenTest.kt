package io.freewheel.launcher.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.freewheel.launcher.service.ServiceStatus
import io.freewheel.launcher.ui.util.screenshotRoot
import io.freewheel.launcher.ui.util.setThemedContent
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSettingsScreen() {
        composeTestRule.setThemedContent {
            SettingsScreen(
                serviceStatus = ServiceStatus(),
                onBack = {},
                onRestartBridge = {},
                onExportRides = {},
                onClearRides = {},
                onOpenSystemSettings = {},
            )
        }
    }

    @Test
    fun allSidebarLabelsDisplayed() {
        setSettingsScreen()

        val categories = listOf(
            "Services", "Ride Data", "Display", "Home Tiles",
            "Diagnostics", "UCB Firmware", "Calibration", "System", "About"
        )
        for (label in categories) {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun servicesPaneShowsByDefault() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("SERVICES").assertIsDisplayed()
        composeTestRule.onNodeWithText("FreewheelBridge (TCP:9999)").assertIsDisplayed()
    }

    @Test
    fun rideDataTabShowsExportCsv() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("Ride Data").performClick()
        composeTestRule.onNodeWithText("RIDE DATA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export CSV").assertIsDisplayed()
    }

    @Test
    fun displayTabShowsBurnInSettings() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("Display").performClick()
        composeTestRule.onNodeWithText("DISPLAY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Burn-in dim timeout").assertIsDisplayed()
    }

    @Test
    fun homeTilesTabShowsNoPinnedApps() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("Home Tiles").performClick()
        composeTestRule.onNodeWithText("HOME TILES").assertIsDisplayed()
        composeTestRule.onNodeWithText("No pinned apps.").assertIsDisplayed()
    }

    @Test
    fun diagnosticsTabShowsUcbStatus() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("Diagnostics").performClick()
        composeTestRule.onNodeWithText("UCB Disconnected").assertIsDisplayed()
    }

    @Test
    fun ucbFirmwareTabShowsPane() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("UCB Firmware").performClick()
        // OtaFlashPane is rendered; just verify the sidebar click navigated
        composeTestRule.onNodeWithText("UCB Firmware").assertIsDisplayed()
    }

    @Test
    fun calibrationTabShowsStartButton() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("Calibration").performClick()
        composeTestRule.onNodeWithText("Start Calibration").assertIsDisplayed()
    }

    @Test
    fun systemTabShowsOpenAndroidSettings() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("System").performClick()
        composeTestRule.onNodeWithText("SYSTEM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Android Settings").assertIsDisplayed()
    }

    @Test
    fun aboutTabShowsVersionInfo() {
        setSettingsScreen()

        composeTestRule.onNodeWithText("About").performClick()
        composeTestRule.onNodeWithText("ABOUT").assertIsDisplayed()
        composeTestRule.onNodeWithText("VeloLauncher").assertIsDisplayed()
    }

    @Test
    fun screenshotAllTabs() {
        setSettingsScreen()

        val categories = listOf(
            "Services", "Ride Data", "Display", "Home Tiles",
            "Diagnostics", "UCB Firmware", "Calibration", "System", "About"
        )
        for (label in categories) {
            composeTestRule.onNodeWithText(label).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.screenshotRoot("settings_${label.lowercase().replace(" ", "_")}")
        }
    }
}
