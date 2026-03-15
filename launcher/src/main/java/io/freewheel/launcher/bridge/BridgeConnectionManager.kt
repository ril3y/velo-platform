package io.freewheel.launcher.bridge

import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteCallbackList
import android.util.Log
import io.freewheel.fit.IWorkoutListener
import io.freewheel.launcher.session.SessionState
import io.freewheel.ucb.BikeServiceClient
import io.freewheel.ucb.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-scoped singleton that owns the persistent BikeServiceClient connection.
 * All bridge interaction flows through here — the ViewModel, RideSessionManager,
 * and WorkoutSessionService all read state from this manager.
 */
class BridgeConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "BridgeConnectionMgr"
        private const val CRASH_GRACE_MS = 3000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val client = BikeServiceClient(context)

    // --- Sensor data flows ---
    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _firmwareState = MutableStateFlow(-1)
    val firmwareState: StateFlow<Int> = _firmwareState.asStateFlow()

    private val _firmwareStateName = MutableStateFlow("")
    val firmwareStateName: StateFlow<String> = _firmwareStateName.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _hrmDeviceName = MutableStateFlow<String?>(null)
    val hrmDeviceName: StateFlow<String?> = _hrmDeviceName.asStateFlow()

    private val _workoutActive = MutableStateFlow(false)
    val workoutActive: StateFlow<Boolean> = _workoutActive.asStateFlow()

    // --- Session state ---
    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // --- Raw frame monitoring ---
    private val _rawFrames = MutableStateFlow<Pair<ByteArray, Boolean>?>(null)
    val rawFrames: StateFlow<Pair<ByteArray, Boolean>?> = _rawFrames.asStateFlow()

    private val _rawMonitorEnabled = MutableStateFlow(false)
    val rawMonitorEnabled: StateFlow<Boolean> = _rawMonitorEnabled.asStateFlow()

    // --- OTA state ---
    private val _otaProgress = MutableStateFlow(Triple(0, 0, 0)) // phase, current, total
    val otaProgress: StateFlow<Triple<Int, Int, Int>> = _otaProgress.asStateFlow()

    private val _otaComplete = MutableStateFlow<Pair<Boolean, String?>?>(null)
    val otaComplete: StateFlow<Pair<Boolean, String?>?> = _otaComplete.asStateFlow()

    // --- Calibration state ---
    private val _calibrationProgress = MutableStateFlow<Pair<Int, String>?>(null)
    val calibrationProgress: StateFlow<Pair<Int, String>?> = _calibrationProgress.asStateFlow()

    private val _calibrationComplete = MutableStateFlow<Boolean?>(null)
    val calibrationComplete: StateFlow<Boolean?> = _calibrationComplete.asStateFlow()

    // --- External app listener broadcasting ---
    val externalListeners = RemoteCallbackList<IWorkoutListener>()

    // --- Active workout owner tracking ---
    @Volatile
    private var activeOwnerPackage: String? = null
    private var activeOwnerBinder: IBinder? = null
    private var ownerDeathRecipient: IBinder.DeathRecipient? = null

    private val listener = object : BikeServiceClient.ListenerAdapter() {
        override fun onSensorData(data: SensorData) {
            _sensorData.value = data
            broadcastSensorData(data)
        }

        override fun onFirmwareStateChanged(state: Int, stateName: String) {
            _firmwareState.value = state
            _firmwareStateName.value = stateName
            broadcastFirmwareState(state, stateName)
        }

        override fun onConnectionChanged(connected: Boolean, message: String) {
            _connected.value = connected
            broadcastConnectionChanged(connected, message)
        }

        override fun onWorkoutStateChanged(active: Boolean, reason: String) {
            _workoutActive.value = active
            if (!active) {
                clearActiveOwner()
                _sessionState.value = SessionState()
            }
            broadcastWorkoutState(active, reason)
        }

        override fun onHeartRate(bpm: Int, deviceName: String) {
            _heartRate.value = bpm
            _hrmDeviceName.value = deviceName
            broadcastHeartRate(bpm, deviceName)
        }

        override fun onServiceConnected() {
            _connected.value = true
        }

        override fun onServiceDisconnected() {
            _connected.value = false
        }

        override fun onOtaProgress(phase: Int, blockCurrent: Int, blockTotal: Int) {
            _otaProgress.value = Triple(phase, blockCurrent, blockTotal)
        }

        override fun onOtaComplete(success: Boolean, error: String) {
            _otaComplete.value = Pair(success, error)
        }

        override fun onCalibrationProgress(step: Int, instruction: String) {
            _calibrationProgress.value = Pair(step, instruction)
        }

        override fun onCalibrationComplete(success: Boolean) {
            _calibrationComplete.value = success
        }

        override fun onRawFrame(frame: ByteArray, isOutgoing: Boolean) {
            _rawFrames.value = Pair(frame, isOutgoing)
        }
    }

    fun connect() {
        client.addListener(listener)
        client.bind()
    }

    fun disconnect() {
        client.removeListener(listener)
        client.unbind()
    }

    // --- Workout control ---

    fun startWorkout(): Boolean {
        return client.startWorkout()
    }

    fun stopWorkout(): Boolean {
        clearActiveOwner()
        _sessionState.value = SessionState()
        return client.stopWorkout()
    }

    fun setResistance(level: Int): Boolean {
        return client.setResistance(level)
    }

    // --- Session owner management ---

    fun setActiveOwner(packageName: String, label: String, binder: IBinder?) {
        activeOwnerPackage = packageName
        activeOwnerBinder = binder
        _sessionState.value = _sessionState.value.copy(
            active = true,
            ownerPackage = packageName,
            ownerLabel = label,
            startTime = System.currentTimeMillis(),
        )

        // Watch for app crash
        if (binder != null) {
            val deathRecipient = IBinder.DeathRecipient {
                Log.w(TAG, "Active workout app ($packageName) died — grace period ${CRASH_GRACE_MS}ms")
                handler.postDelayed({
                    if (activeOwnerPackage == packageName) {
                        Log.w(TAG, "Grace period expired, auto-stopping workout")
                        stopWorkout()
                    }
                }, CRASH_GRACE_MS)
            }
            try {
                binder.linkToDeath(deathRecipient, 0)
                ownerDeathRecipient = deathRecipient
            } catch (e: Exception) {
                Log.w(TAG, "Failed to linkToDeath for $packageName", e)
            }
        }
    }

    fun getActiveOwnerPackage(): String? = activeOwnerPackage

    fun isOwner(packageName: String): Boolean = activeOwnerPackage == packageName

    private fun clearActiveOwner() {
        activeOwnerBinder?.let { binder ->
            ownerDeathRecipient?.let { recipient ->
                try {
                    binder.unlinkToDeath(recipient, 0)
                } catch (_: Exception) {}
            }
        }
        activeOwnerPackage = null
        activeOwnerBinder = null
        ownerDeathRecipient = null
    }

    // --- Diagnostics / Calibration / OTA pass-through ---

    fun startCalibration(calibrationType: Int) = client.startCalibration(calibrationType)
    fun cancelCalibration() = client.cancelCalibration()
    fun confirmCalibrationStep() = client.confirmCalibrationStep()
    fun setRawFrameMonitoring(enabled: Boolean) {
        _rawMonitorEnabled.value = enabled
        client.setRawFrameMonitoring(enabled)
    }
    fun sendRawCommand(frame: ByteArray) = client.sendRawCommand(frame)
    fun getService() = client.getService()

    // --- State queries ---

    fun getFirmwareStateValue(): Int = client.getFirmwareState()
    fun getHeartRateValue(): Int = client.getHeartRate()
    fun getConnectedHrmNameValue(): String? = client.getConnectedHrmName()
    fun isWorkoutActiveValue(): Boolean = _workoutActive.value

    // --- External listener broadcasting ---

    private fun broadcastSensorData(data: SensorData) {
        val n = externalListeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                externalListeners.getBroadcastItem(i).onSensorData(
                    data.resistanceLevel, data.rpm, data.tilt, data.power,
                    data.crankRevCount, data.crankEventTime,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast sensor data to listener $i", e)
            }
        }
        externalListeners.finishBroadcast()
    }

    private fun broadcastHeartRate(bpm: Int, deviceName: String) {
        val n = externalListeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                externalListeners.getBroadcastItem(i).onHeartRate(bpm, deviceName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast heart rate to listener $i", e)
            }
        }
        externalListeners.finishBroadcast()
    }

    private fun broadcastWorkoutState(active: Boolean, reason: String) {
        val n = externalListeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                externalListeners.getBroadcastItem(i).onWorkoutStateChanged(active, reason)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast workout state to listener $i", e)
            }
        }
        externalListeners.finishBroadcast()
    }

    private fun broadcastConnectionChanged(connected: Boolean, message: String) {
        val n = externalListeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                externalListeners.getBroadcastItem(i).onConnectionChanged(connected, message)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast connection change to listener $i", e)
            }
        }
        externalListeners.finishBroadcast()
    }

    private fun broadcastFirmwareState(state: Int, stateName: String) {
        val n = externalListeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                externalListeners.getBroadcastItem(i).onFirmwareStateChanged(state, stateName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast firmware state to listener $i", e)
            }
        }
        externalListeners.finishBroadcast()
    }
}
