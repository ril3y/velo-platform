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
import io.freewheel.launcher.ui.*
import io.freewheel.launcher.ui.theme.VeloLauncherTheme

class MainActivity : ComponentActivity() {

    private var viewModelRef: LauncherViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on, immersive mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

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

    override fun onResume() {
        super.onResume()
        hideSystemUI()
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
    val autoRestartOverlay by vm.autoRestartOverlay.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }

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
            SettingsScreen(
                serviceStatus = serviceStatus,
                onBack = { currentScreen = Screen.HOME },
                onRestartBridge = { vm.restartSerialBridge() },
                onRestartOverlay = { vm.restartOverlay() },
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
                autoRestartOverlay = autoRestartOverlay,
                onAutoRestartBridgeChange = { vm.setAutoRestartBridge(it) },
                onAutoRestartOverlayChange = { vm.setAutoRestartOverlay(it) },
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
                    vm.startWorkoutRide()
                    currentScreen = Screen.HOME
                },
                onStartStatsOnly = {
                    vm.selectMedia(null)
                    vm.startWorkoutRide()
                    currentScreen = Screen.HOME
                },
                onBack = { currentScreen = Screen.WORKOUT_PICKER },
                ramUsedMb = ramUsed,
                ramTotalMb = ramTotal,
                currentTime = currentTime,
            )
        }

        else -> {
            HomeScreen(
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
                        is HomeTile.StartRide -> vm.startRide()
                        is HomeTile.App -> {
                            if (tile.isInstalled) vm.launchApp(tile.packageName)
                        }
                    }
                },
                onAppClick = { vm.launchApp(it) },
                onSettingsClick = { currentScreen = Screen.SETTINGS },
                onBridgeClick = {
                    if (!serviceStatus.serialBridgeRunning || !serviceStatus.serialBridgeTcpAlive) {
                        vm.restartSerialBridge()
                    }
                },
                onOverlayClick = {
                    if (!serviceStatus.overlayRunning) {
                        vm.restartOverlay()
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
                onFreeRide = { vm.startRide() },
                onMediaClick = { currentScreen = Screen.WORKOUT_PICKER },
                onHistoryClick = { currentScreen = Screen.RIDE_HISTORY },
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
