package io.freewheel.launcher

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.freewheel.launcher.data.HomeTile
import io.freewheel.launcher.data.RideSummary
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.overlay.HomeButtonOverlay
import io.freewheel.launcher.ui.*
import io.freewheel.launcher.update.UpdateStatus
import io.freewheel.launcher.ui.theme.VeloLauncherTheme

class MainActivity : ComponentActivity() {

    private var viewModelRef: LauncherViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on, immersive mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        // Kiosk mode settings (plugged-in bike, not a battery device)
        try {
            android.provider.Settings.Global.putInt(contentResolver, "wifi_sleep_policy", 0) // WiFi never sleeps
            android.provider.Settings.System.putInt(contentResolver, "screen_off_timeout", 2147483647) // Screen never times out
        } catch (_: Exception) {}

        setContent {
            VeloLauncherTheme {
                val vm: LauncherViewModel = viewModel()
                viewModelRef = vm
                LauncherApp(vm)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        viewModelRef?.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        viewModelRef?.onUserInteraction()
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        // Always show the edge swipe zone — it's invisible and doesn't interfere with apps
        // Use startForegroundService so it survives process kills
        try {
            startForegroundService(Intent(this, HomeButtonOverlay::class.java))
        } catch (_: Exception) {
            HomeButtonOverlay.show(this)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        HomeButtonOverlay.hide(this)
        // Safety net: clear immersive mode in case overlay service crashed
        // without calling restoreStatusBar()
        try {
            android.provider.Settings.Global.putString(
                contentResolver, "policy_control", ""
            )
        } catch (_: Exception) {}
        viewModelRef?.loadApps()
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // HOME launcher — back does nothing
    }
}

// Navigation screens
private enum class Screen {
    SETUP, HOME, WORKOUT_PICKER, WORKOUT_DETAIL,
    SETTINGS, TASK_MANAGER, RIDE_HISTORY,
    COUNTDOWN, FREE_RIDE, WORKOUT_RIDE, RIDE_SUMMARY,
}

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val serviceStatus by vm.serviceStatus.collectAsState()
    val allApps by vm.allApps.collectAsState()
    val recentRides by vm.recentRides.collectAsState()
    val screenDimmed by vm.screenDimmed.collectAsState()
    val screenOff by vm.screenOff.collectAsState()
    val wifiSsid by vm.wifiSsid.collectAsState()
    val ramUsed by vm.ramUsed.collectAsState()
    val ramTotal by vm.ramTotal.collectAsState()

    // Workout flow state
    val selectedWorkout by vm.selectedWorkout.collectAsState()
    val selectedMedia by vm.selectedMedia.collectAsState()
    val rideActive by vm.rideActive.collectAsState()

    // App categories
    val fitnessApps by vm.fitnessApps.collectAsState()
    val recentApps by vm.recentApps.collectAsState()

    // Setup wizard
    val setupComplete by vm.setupComplete.collectAsState()

    // Task manager
    val runningApps by vm.runningApps.collectAsState()

    // Burn-in prevention
    val burnInOffsetX by vm.burnInOffsetX.collectAsState()
    val burnInOffsetY by vm.burnInOffsetY.collectAsState()

    // Settings
    val dimTimeoutMinutes by vm.dimTimeoutMinutes.collectAsState()
    val offTimeoutMinutes by vm.offTimeoutMinutes.collectAsState()
    val autoRestartBridge by vm.autoRestartBridge.collectAsState()

    // Update availability
    val updateAvailableCount by vm.updateAvailableCount.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var rideSummary by remember { mutableStateOf<RideSummary?>(null) }
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var postCountdownScreen by remember { mutableStateOf(Screen.HOME) }
    var countdownForMediaOverlay by remember { mutableStateOf(false) }

    // Observe ride navigation events
    val rideNavEvent by vm.rideNavigationEvent.collectAsState()
    LaunchedEffect(rideNavEvent) {
        when (val event = rideNavEvent) {
            is RideNavigationEvent.FreeRide -> {
                countdownForMediaOverlay = false
                postCountdownScreen = Screen.FREE_RIDE
                currentScreen = Screen.COUNTDOWN
                vm.clearRideNavigationEvent()
            }
            is RideNavigationEvent.WorkoutRide -> {
                activeWorkout = event.workout
                countdownForMediaOverlay = false
                postCountdownScreen = Screen.WORKOUT_RIDE
                currentScreen = Screen.COUNTDOWN
                vm.clearRideNavigationEvent()
            }
            is RideNavigationEvent.WorkoutWithMedia -> {
                activeWorkout = event.workout
                countdownForMediaOverlay = true
                currentScreen = Screen.COUNTDOWN
                vm.clearRideNavigationEvent()
            }
            is RideNavigationEvent.ShowSummary -> {
                rideSummary = event.summary
                currentScreen = Screen.RIDE_SUMMARY
                vm.clearRideNavigationEvent()
            }
            null -> {}
        }
    }

    // Observe overlay summary (when returning from media app)
    val overlaySummary by vm.lastOverlaySummary.collectAsState()
    LaunchedEffect(overlaySummary) {
        if (overlaySummary != null) {
            rideSummary = overlaySummary
            currentScreen = Screen.RIDE_SUMMARY
            vm.clearOverlaySummary()
        }
    }

    // Show setup wizard on first run
    LaunchedEffect(setupComplete) {
        if (setupComplete == false) {
            currentScreen = Screen.SETUP
        }
    }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Clock update
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    when {
        // Loading state — still checking if setup is needed
        setupComplete == null -> {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFF0A0B14)),
            )
        }

        // First-run setup wizard
        currentScreen == Screen.SETUP -> {
            SetupWizardScreen(
                onComplete = { profile ->
                    vm.completeSetup(profile)
                    currentScreen = Screen.HOME
                },
                onSkip = {
                    vm.skipSetup()
                    currentScreen = Screen.HOME
                },
            )
        }

        currentScreen == Screen.TASK_MANAGER -> {
            TaskManager(
                runningApps = runningApps,
                ramUsedMb = ramUsed,
                ramTotalMb = ramTotal,
                onKill = { vm.killApp(it) },
                onKillAll = { vm.killAllApps() },
                onDismiss = { currentScreen = Screen.HOME },
            )
        }

        currentScreen == Screen.RIDE_HISTORY -> {
            val allRidesState by vm.allRides.collectAsState()
            RideHistoryScreen(
                rides = allRidesState,
                onBack = { currentScreen = Screen.HOME },
                onDelete = { id -> vm.deleteRide(id) },
            )
        }

        currentScreen == Screen.SETTINGS -> {
            val updateStatusVal by vm.updateStatus.collectAsState()
            val updateLatestVersion by vm.updateLatestVersion.collectAsState()
            val updateChangelog by vm.updateChangelog.collectAsState()
            val updateDownloadProgress by vm.updateDownloadProgress.collectAsState()
            val availableUpdates by vm.availableUpdates.collectAsState()

            // Diagnostics state
            val bridgeConnected by vm.bridgeConnected.collectAsState()
            val bridgeFwState by vm.bridgeFirmwareState.collectAsState()
            val bridgeFwStateName by vm.bridgeFirmwareStateName.collectAsState()
            val bridgeSensor by vm.bridgeSensorData.collectAsState()
            val bridgeHr by vm.bridgeHeartRate.collectAsState()
            val bridgeHrmName by vm.bridgeHrmDeviceName.collectAsState()
            val bridgeRawMonitor by vm.bridgeRawMonitorEnabled.collectAsState()
            val bridgeFwVersion by vm.bridgeFirmwareVersion.collectAsState()
            val bridgeHwId by vm.bridgeHardwareId.collectAsState()

            // Accumulate raw frames into a bounded list for display
            val rawFrameLog = remember { mutableStateListOf<String>() }
            val latestRawFrame by vm.bridgeRawFrames.collectAsState()
            LaunchedEffect(latestRawFrame) {
                latestRawFrame?.let { (data, isInbound) ->
                    val dir = if (isInbound) "RX" else "TX"
                    val hex = data.joinToString(" ") { "%02X".format(it) }
                    rawFrameLog.add(0, "$dir $hex")
                    if (rawFrameLog.size > 200) rawFrameLog.removeRange(200, rawFrameLog.size)
                }
            }

            // OTA state
            val otaProgress by vm.bridgeOtaProgress.collectAsState()
            val otaComplete by vm.bridgeOtaComplete.collectAsState()

            // Calibration state
            val calProgress by vm.bridgeCalibrationProgress.collectAsState()
            val calComplete by vm.bridgeCalibrationComplete.collectAsState()

            SettingsScreen(
                serviceStatus = serviceStatus,
                onBack = { currentScreen = Screen.HOME },
                onRestartBridge = { vm.restartSerialBridge() },
                onExportRides = {
                    val csv = vm.exportRidesCsv()
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_TEXT, csv)
                        putExtra(Intent.EXTRA_SUBJECT, "VeloLauncher Ride History")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Export Rides"))
                },
                onClearRides = { vm.clearRideHistory() },
                onOpenSystemSettings = {
                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                dimTimeoutMinutes = dimTimeoutMinutes,
                offTimeoutMinutes = offTimeoutMinutes,
                onDimTimeoutChange = { vm.setDimTimeoutMinutes(it) },
                onOffTimeoutChange = { vm.setOffTimeoutMinutes(it) },
                pinnedApps = vm.getPinnedApps(),
                onTogglePin = { _, _ -> },
                autoRestartBridge = autoRestartBridge,
                onAutoRestartBridgeChange = { vm.setAutoRestartBridge(it) },
                // Diagnostics
                diagnosticsState = DiagnosticsState(
                    connected = bridgeConnected,
                    firmwareState = bridgeFwState,
                    firmwareStateName = if (bridgeFwStateName.isNotBlank()) bridgeFwStateName else "Unknown",
                    resistance = bridgeSensor.resistanceLevel,
                    rpm = bridgeSensor.rpm,
                    tilt = bridgeSensor.tilt,
                    power = bridgeSensor.power,
                    crankRevCount = bridgeSensor.crankRevCount,
                    crankEventTime = bridgeSensor.crankEventTime,
                    heartRate = bridgeHr,
                    hrmDeviceName = bridgeHrmName,
                    rawFrames = rawFrameLog,
                    rawMonitorEnabled = bridgeRawMonitor,
                ),
                onToggleRawMonitor = { vm.setRawFrameMonitoring(it) },
                // OTA firmware flash
                otaFlashState = OtaFlashState(
                    currentFirmwareVersion = when {
                        bridgeFwVersion != null -> "$bridgeFwVersion (HW$bridgeHwId, $bridgeFwStateName)"
                        bridgeFwState >= 0 -> "$bridgeFwStateName ($bridgeFwState)"
                        else -> "Not connected"
                    },
                    isFlashing = otaProgress.first > 0 && otaComplete == null,
                    phase = otaProgress.first,
                    phaseName = when (otaProgress.first) {
                        1 -> "Erasing"; 2 -> "Writing"; 3 -> "Verifying"; else -> "Idle"
                    },
                    blockCurrent = otaProgress.second,
                    blockTotal = otaProgress.third,
                    completed = otaComplete != null,
                    success = otaComplete?.first == true,
                    error = otaComplete?.second?.takeIf { it.isNotBlank() },
                ),
                onPickFirmwareFile = { /* TODO: file picker integration */ },
                onStartOtaFlash = { /* TODO: OTA flash integration */ },
                onCancelOtaFlash = { /* TODO: cancel OTA */ },
                // Calibration
                calibrationState = CalibrationState(
                    isActive = calProgress != null && calComplete == null,
                    currentStep = calProgress?.first ?: 0,
                    instruction = calProgress?.second ?: "",
                    completed = calComplete != null,
                    success = calComplete == true,
                ),
                onStartCalibration = { vm.startCalibration(0) },
                onConfirmCalibrationStep = { vm.confirmCalibrationStep() },
                onCancelCalibration = { vm.cancelCalibration() },
                // Updates
                updateStatus = updateStatusVal,
                updateLatestVersion = updateLatestVersion,
                updateChangelog = updateChangelog,
                updateDownloadProgress = updateDownloadProgress,
                onCheckForUpdate = { vm.checkForUpdate() },
                onDownloadUpdate = { vm.downloadUpdate() },
                onInstallUpdate = { vm.installUpdate() },
                availableUpdates = availableUpdates,
                onDownloadAppUpdate = { vm.downloadUpdate(it) },
            )
        }

        currentScreen == Screen.WORKOUT_PICKER -> {
            WorkoutPickerScreen(
                workouts = vm.getWorkouts(),
                onSelect = { workout ->
                    vm.selectWorkout(workout)
                    currentScreen = Screen.WORKOUT_DETAIL
                },
                onBack = { currentScreen = Screen.HOME },
                ramUsedMb = ramUsed,
                ramTotalMb = ramTotal,
                currentTime = currentTime,
            )
        }

        currentScreen == Screen.WORKOUT_DETAIL && selectedWorkout != null -> {
            WorkoutDetailScreen(
                workout = selectedWorkout!!,
                mediaApps = vm.getMediaApps(),
                selectedMedia = selectedMedia,
                onMediaSelect = { vm.selectMedia(it) },
                onStartWithMedia = {
                    vm.startWorkoutWithMedia()
                    // No screen change — media app launches
                },
                onStartStatsOnly = {
                    vm.selectMedia(null)
                    vm.startWorkoutStatsOnly()
                    // Screen change handled by rideNavigationEvent
                },
                onBack = { currentScreen = Screen.WORKOUT_PICKER },
                ramUsedMb = ramUsed,
                ramTotalMb = ramTotal,
                currentTime = currentTime,
            )
        }

        currentScreen == Screen.COUNTDOWN -> {
            CountdownScreen(
                workoutName = activeWorkout?.name,
                onComplete = {
                    if (countdownForMediaOverlay) {
                        countdownForMediaOverlay = false
                        vm.launchMediaOverlay()
                        // Don't change screen — media app takes over
                    } else {
                        currentScreen = postCountdownScreen
                    }
                },
            )
        }

        currentScreen == Screen.FREE_RIDE -> {
            val power by vm.ridePower.collectAsState()
            val rpm by vm.rideRpm.collectAsState()
            val resistance by vm.rideResistance.collectAsState()
            val calories by vm.rideCalories.collectAsState()
            val elapsed by vm.rideElapsedSeconds.collectAsState()
            val speed by vm.rideSpeedMph.collectAsState()
            val distance by vm.rideDistanceMiles.collectAsState()
            val hr by vm.rideHeartRate.collectAsState()
            val connected by vm.bridgeConnected.collectAsState()

            FreeRideScreen(
                power = power, rpm = rpm, resistance = resistance,
                calories = calories, elapsedSeconds = elapsed,
                speedMph = speed, distanceMiles = distance,
                heartRate = hr, isConnected = connected,
                onStop = { vm.stopCurrentRide() },
            )
        }

        currentScreen == Screen.WORKOUT_RIDE && activeWorkout != null -> {
            val power by vm.ridePower.collectAsState()
            val rpm by vm.rideRpm.collectAsState()
            val resistance by vm.rideResistance.collectAsState()
            val calories by vm.rideCalories.collectAsState()
            val elapsed by vm.rideElapsedSeconds.collectAsState()
            val speed by vm.rideSpeedMph.collectAsState()
            val distance by vm.rideDistanceMiles.collectAsState()
            val hr by vm.rideHeartRate.collectAsState()
            val connected by vm.bridgeConnected.collectAsState()
            val powerHist by vm.ridePowerHistory.collectAsState()

            WorkoutRideScreen(
                workout = activeWorkout!!,
                power = power, rpm = rpm, resistance = resistance,
                calories = calories, elapsedSeconds = elapsed,
                speedMph = speed, distanceMiles = distance,
                heartRate = hr, isConnected = connected,
                powerHistory = powerHist,
                ftp = 200, // TODO: get from fitness config
                onEndRide = { vm.stopCurrentRide(activeWorkout?.id, activeWorkout?.name) },
            )
        }

        currentScreen == Screen.RIDE_SUMMARY && rideSummary != null -> {
            RideSummaryScreen(
                summary = rideSummary!!,
                onDone = {
                    rideSummary = null
                    activeWorkout = null
                    vm.clearWorkoutSelection()
                    currentScreen = Screen.HOME
                },
            )
        }

        else -> {
            if (rideActive) {
                // Workout is active but user reached home (e.g., physical home button)
                // Show a "workout in progress" screen instead of home
                WorkoutActiveScreen(
                    power = vm.ridePower.collectAsState().value,
                    rpm = vm.rideRpm.collectAsState().value,
                    elapsed = vm.rideElapsedSeconds.collectAsState().value,
                    workoutName = selectedWorkout?.name,
                    onReturnToMedia = {
                        selectedMedia?.let { media ->
                            val app = context as android.app.Activity
                            app.packageManager.getLaunchIntentForPackage(media.packageName)?.let {
                                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(it)
                            }
                        }
                    },
                    onEndWorkout = {
                        vm.stopCurrentRide(selectedWorkout?.id, selectedWorkout?.name)
                        // Stop overlay service
                        context.stopService(
                            android.content.Intent(context, io.freewheel.launcher.overlay.RideOverlayService::class.java)
                        )
                    },
                )
            } else HomeScreen(
                tiles = vm.getHomeTiles(),
                allApps = allApps,
                recentRides = recentRides,
                serviceStatus = serviceStatus,
                wifiSsid = wifiSsid,
                ramUsedMb = ramUsed,
                ramTotalMb = ramTotal,
                lastRidePower = recentRides.firstOrNull()?.avgPowerWatts ?: 0,
                lastRideRpm = recentRides.firstOrNull()?.avgRpm ?: 0,
                fitnessApps = fitnessApps,
                recentApps = recentApps,
                workoutCount = vm.getWorkouts().size,
                workoutCategoryCount = vm.getWorkouts().map { it.category }.distinct().size,
                onTileClick = { tile ->
                    when (tile) {
                        is HomeTile.StartRide -> vm.startFreeRide()
                        is HomeTile.App -> {
                            if (tile.isInstalled) vm.launchApp(tile.packageName)
                        }
                    }
                },
                onAppClick = { vm.launchApp(it) },
                onSettingsClick = { currentScreen = Screen.SETTINGS },
                onBridgeClick = {
                    if (!serviceStatus.serialBridgeRunning && !serviceStatus.serialBridgeTcpAlive) {
                        vm.restartSerialBridge()
                    }
                },
                onTaskManagerClick = {
                    vm.loadRunningApps()
                    currentScreen = Screen.TASK_MANAGER
                },
                onAppInfo = { vm.showAppInfo(it) },
                onUninstall = { vm.uninstallApp(it) },
                onViewAllRides = { currentScreen = Screen.RIDE_HISTORY },
                burnInOffsetX = burnInOffsetX,
                burnInOffsetY = burnInOffsetY,
                onBrowseWorkouts = { currentScreen = Screen.WORKOUT_PICKER },
                onFreeRide = { vm.startFreeRide() },
                defaultFitnessAppLabel = "Free Ride",
                onMediaClick = { currentScreen = Screen.WORKOUT_PICKER },
                onHistoryClick = { currentScreen = Screen.RIDE_HISTORY },
                updateAvailable = updateAvailableCount > 0,
                currentTime = currentTime,
            )
        }
    }

    // Screen saver overlay (on top of everything)
    ScreenSaver(
        isDimmed = screenDimmed,
        isOff = screenOff,
        onTap = { vm.onUserInteraction() },
    )
}
