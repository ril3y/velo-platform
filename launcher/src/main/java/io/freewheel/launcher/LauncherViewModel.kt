package io.freewheel.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.freewheel.launcher.apps.AppRepository
import io.freewheel.launcher.apps.TaskRepository
import io.freewheel.launcher.bridge.BridgeConnectionManager
import io.freewheel.launcher.data.HomeTile
import io.freewheel.launcher.data.MediaApp
import io.freewheel.launcher.data.MediaApps
import io.freewheel.launcher.data.RideDatabase
import io.freewheel.launcher.data.UserProfile
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.data.WorkoutRepository
import io.freewheel.launcher.ride.RideRepository
import io.freewheel.launcher.ride.RideSessionManager
import io.freewheel.launcher.service.ServiceMonitor
import io.freewheel.launcher.service.ServiceStatus
import io.freewheel.launcher.session.SessionState
import io.freewheel.launcher.session.WorkoutAppRegistry
import io.freewheel.launcher.system.ScreenSaverManager
import io.freewheel.launcher.system.SystemMonitor
import io.freewheel.launcher.update.AppUpdate
import io.freewheel.launcher.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("velolauncher", Context.MODE_PRIVATE)

    // --- Bridge connection (application-scoped singleton) ---
    private val bridgeConnectionManager: BridgeConnectionManager =
        VeloLauncherApp.get(application).bridgeConnectionManager

    // --- Repositories & managers ---
    private val rideRepository = RideRepository(application)
    private val workoutRepository = WorkoutRepository(application)
    private val appRepository = AppRepository(application)
    private val taskRepository = TaskRepository(application)
    private val serviceMonitor = ServiceMonitor(application)
    private val systemMonitor = SystemMonitor(application, viewModelScope)
    private val rideSessionManager = RideSessionManager(bridgeConnectionManager, viewModelScope, rideRepository)
    private val screenSaverManager = ScreenSaverManager(prefs, viewModelScope, application.contentResolver) {
        rideSessionManager.rideActive.value
    }
    private val userProfileDao = RideDatabase.getInstance(application).userProfileDao()
    private val workoutAppRegistry = WorkoutAppRegistry(application)
    private val updateManager = UpdateManager(application, viewModelScope)

    // --- Exposed state: Service status ---
    private val _serviceStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    // --- Exposed state: Apps ---
    val allApps get() = appRepository.allApps
    val fitnessApps get() = appRepository.fitnessApps
    val recentApps get() = appRepository.recentApps

    // --- Exposed state: Workout apps ---
    val workoutApps get() = workoutAppRegistry.apps

    // --- Exposed state: Ride history ---
    val recentRides: StateFlow<List<io.freewheel.launcher.data.RideRecord>> =
        rideRepository.getRecentRides(5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRides: StateFlow<List<io.freewheel.launcher.data.RideRecord>> =
        rideRepository.getAllRides()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Exposed state: Ride session (delegated) ---
    val rideActive get() = rideSessionManager.rideActive
    val ridePower get() = rideSessionManager.ridePower
    val rideRpm get() = rideSessionManager.rideRpm
    val rideResistance get() = rideSessionManager.rideResistance
    val rideCalories get() = rideSessionManager.rideCalories
    val rideElapsedSeconds get() = rideSessionManager.rideElapsedSeconds
    val rideSpeedMph get() = rideSessionManager.rideSpeedMph
    val rideDistanceMiles get() = rideSessionManager.rideDistanceMiles
    val rideHeartRate get() = rideSessionManager.rideHeartRate
    val rideConnected get() = rideSessionManager.rideConnected

    // --- Exposed state: Bridge (for diagnostics, calibration, OTA) ---
    val bridgeConnected get() = bridgeConnectionManager.connected
    val bridgeSensorData get() = bridgeConnectionManager.sensorData
    val bridgeFirmwareState get() = bridgeConnectionManager.firmwareState
    val bridgeFirmwareStateName get() = bridgeConnectionManager.firmwareStateName
    val bridgeFirmwareVersion get() = bridgeConnectionManager.firmwareVersion
    val bridgeHardwareId get() = bridgeConnectionManager.hardwareId
    val bridgeHeartRate get() = bridgeConnectionManager.heartRate
    val bridgeHrmDeviceName get() = bridgeConnectionManager.hrmDeviceName
    val bridgeWorkoutActive get() = bridgeConnectionManager.workoutActive
    val bridgeRawFrames get() = bridgeConnectionManager.rawFrames
    val bridgeRawMonitorEnabled get() = bridgeConnectionManager.rawMonitorEnabled
    val bridgeOtaProgress get() = bridgeConnectionManager.otaProgress
    val bridgeOtaComplete get() = bridgeConnectionManager.otaComplete
    val bridgeCalibrationProgress get() = bridgeConnectionManager.calibrationProgress
    val bridgeCalibrationComplete get() = bridgeConnectionManager.calibrationComplete
    val sessionState get() = bridgeConnectionManager.sessionState

    // --- Exposed state: Screen saver (delegated) ---
    val screenDimmed get() = screenSaverManager.screenDimmed
    val screenOff get() = screenSaverManager.screenOff
    val burnInOffsetX get() = screenSaverManager.offsetX
    val burnInOffsetY get() = screenSaverManager.offsetY
    val dimTimeoutMinutes get() = screenSaverManager.dimMinutes
    val offTimeoutMinutes get() = screenSaverManager.offMinutes

    // --- Exposed state: Settings (auto-restart) ---
    private val _autoRestartBridge = MutableStateFlow(prefs.getBoolean("auto_restart_bridge", true))
    val autoRestartBridge: StateFlow<Boolean> = _autoRestartBridge.asStateFlow()

    // --- Exposed state: Default fitness app ---
    private val _defaultFitnessApp = MutableStateFlow(
        prefs.getString("default_fitness_app", "io.freewheel.freeride") ?: "io.freewheel.freeride"
    )
    val defaultFitnessApp: StateFlow<String> = _defaultFitnessApp.asStateFlow()

    // --- Exposed state: Task manager (delegated) ---
    val runningApps get() = taskRepository.runningApps

    // --- Exposed state: System info (delegated) ---
    val wifiSsid get() = systemMonitor.wifiSsid
    val ramUsed get() = systemMonitor.ramUsed
    val ramTotal get() = systemMonitor.ramTotal

    // --- Exposed state: Update ---
    val updateStatus get() = updateManager.status
    val updateLatestVersion get() = updateManager.latestVersion
    val updateChangelog get() = updateManager.changelog
    val updateDownloadProgress get() = updateManager.downloadProgress
    val availableUpdates get() = updateManager.availableUpdates
    val updateAvailableCount: StateFlow<Int> = updateManager.availableUpdates
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Exposed state: User profile ---
    private val _setupComplete = MutableStateFlow<Boolean?>(null) // null = loading
    val setupComplete: StateFlow<Boolean?> = _setupComplete.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = userProfileDao.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Exposed state: Workout flow ---
    private val _selectedWorkout = MutableStateFlow<Workout?>(null)
    val selectedWorkout: StateFlow<Workout?> = _selectedWorkout.asStateFlow()

    private val _selectedMedia = MutableStateFlow<MediaApp?>(null)
    val selectedMedia: StateFlow<MediaApp?> = _selectedMedia.asStateFlow()

    private val _workoutRideActive = MutableStateFlow(false)
    val workoutRideActive: StateFlow<Boolean> = _workoutRideActive.asStateFlow()

    init {
        loadApps()
        startServiceMonitor()
        systemMonitor.start()
        screenSaverManager.start()
        checkSetupComplete()
        workoutAppRegistry.start()
        updateManager.checkIfDue()
    }

    // --- App operations ---

    fun loadApps() {
        viewModelScope.launch {
            appRepository.loadApps()
        }
    }

    fun getHomeTiles(): List<HomeTile> = appRepository.getHomeTiles()

    fun launchApp(packageName: String) = appRepository.launchApp(packageName)

    // --- Service operations ---

    private fun startServiceMonitor() {
        viewModelScope.launch {
            serviceMonitor.statusFlow().collect { status ->
                _serviceStatus.value = status
            }
        }
    }

    fun restartSerialBridge() = serviceMonitor.restartSerialBridge()

    // --- Ride operations ---

    fun startRide() {
        val app = getApplication<Application>()
        val pkg = _defaultFitnessApp.value
        val launchIntent = app.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent != null) {
            try {
                app.startActivity(launchIntent)
            } catch (_: Exception) {
                // App not launchable — fall back to internal session
                rideSessionManager.startRide()
            }
        } else {
            // App not installed — fall back to internal session
            rideSessionManager.startRide()
        }
    }

    fun stopRide() = rideSessionManager.stopRide()

    // --- Bridge pass-through for diagnostics/calibration/OTA ---

    fun startCalibration(calibrationType: Int) = bridgeConnectionManager.startCalibration(calibrationType)
    fun cancelCalibration() = bridgeConnectionManager.cancelCalibration()
    fun confirmCalibrationStep() = bridgeConnectionManager.confirmCalibrationStep()
    fun setRawFrameMonitoring(enabled: Boolean) = bridgeConnectionManager.setRawFrameMonitoring(enabled)
    fun sendRawCommand(frame: ByteArray) = bridgeConnectionManager.sendRawCommand(frame)
    fun getBridgeService() = bridgeConnectionManager.getService()

    // --- Task manager operations ---

    fun loadRunningApps() {
        viewModelScope.launch {
            taskRepository.loadRunningApps()
        }
    }

    fun killApp(packageName: String) {
        taskRepository.killApp(packageName)
        loadRunningApps()
    }

    fun killAllApps() {
        taskRepository.killAllApps()
        loadRunningApps()
    }

    // --- Screen saver ---

    fun onUserInteraction() = screenSaverManager.onUserInteraction()

    fun setDimTimeoutMinutes(minutes: Int) = screenSaverManager.setDimMinutes(minutes)

    fun setOffTimeoutMinutes(minutes: Int) = screenSaverManager.setOffMinutes(minutes)

    // --- Settings: auto-restart ---

    fun setAutoRestartBridge(enabled: Boolean) {
        _autoRestartBridge.value = enabled
        prefs.edit().putBoolean("auto_restart_bridge", enabled).apply()
    }

    fun setDefaultFitnessApp(packageName: String) {
        _defaultFitnessApp.value = packageName
        prefs.edit().putString("default_fitness_app", packageName).apply()
    }

    fun getPinnedApps(): List<Pair<String, String>> {
        return getHomeTiles().filterIsInstance<HomeTile.App>().map { it.packageName to it.label }
    }

    // --- App management ---

    fun showAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    // --- Workout flow ---

    fun getWorkouts(): List<Workout> = workoutRepository.getWorkouts()

    fun getMediaApps(): List<MediaApp> = MediaApps.all

    fun selectWorkout(workout: Workout) {
        _selectedWorkout.value = workout
    }

    fun selectMedia(media: MediaApp?) {
        _selectedMedia.value = media
    }

    fun startWorkoutRide() {
        val workout = _selectedWorkout.value ?: return
        _workoutRideActive.value = true
        // Start the ride through the bridge (launcher is the owner)
        rideSessionManager.startRide()
    }

    fun stopWorkoutRide() {
        _workoutRideActive.value = false
        rideSessionManager.stopRide()
    }

    fun clearWorkoutSelection() {
        _selectedWorkout.value = null
        _selectedMedia.value = null
        _workoutRideActive.value = false
    }

    // --- Setup wizard ---

    private fun checkSetupComplete() {
        viewModelScope.launch {
            val hasProfile = userProfileDao.count() > 0
            _setupComplete.value = hasProfile
        }
    }

    fun completeSetup(profile: UserProfile) {
        viewModelScope.launch {
            userProfileDao.upsert(profile)
            _setupComplete.value = true
        }
    }

    fun skipSetup() {
        viewModelScope.launch {
            // Create minimal profile so wizard doesn't show again
            userProfileDao.upsert(UserProfile(displayName = "Rider"))
            _setupComplete.value = true
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            userProfileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // --- Update operations ---

    fun checkForUpdate() = updateManager.checkForUpdate()
    fun downloadUpdate() = updateManager.downloadUpdate()
    fun downloadUpdate(appUpdate: AppUpdate) = updateManager.downloadUpdate(appUpdate)
    fun installUpdate() = updateManager.installUpdate()

    // --- Ride history ---

    fun exportRidesCsv(): String = rideRepository.exportCsv(allRides.value)

    fun deleteRide(id: Long) {
        viewModelScope.launch {
            rideRepository.deleteById(id)
        }
    }

    fun clearRideHistory() {
        viewModelScope.launch {
            rideRepository.deleteAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        rideSessionManager.destroy()
        screenSaverManager.destroy()
        workoutAppRegistry.stop()
    }
}
