package io.freewheel.launcher.ride

import io.freewheel.launcher.bridge.BridgeConnectionManager
import io.freewheel.launcher.data.RideRecord
import io.freewheel.launcher.data.RideSummary
import io.freewheel.ucb.RidePhysics
import io.freewheel.ucb.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pure data accumulator for ride statistics. Collects sensor data from
 * BridgeConnectionManager's StateFlows and computes derived metrics.
 * No longer creates its own BikeServiceClient.
 */
class RideSessionManager(
    private val bridge: BridgeConnectionManager,
    private val scope: CoroutineScope,
    private val rideRepository: RideRepository,
) {

    private val _rideActive = MutableStateFlow(false)
    val rideActive: StateFlow<Boolean> = _rideActive.asStateFlow()

    private val _ridePaused = MutableStateFlow(false)
    val ridePaused: StateFlow<Boolean> = _ridePaused.asStateFlow()

    private var lastPedalingTime = 0L
    private var totalPausedMs = 0L
    private var pauseStartTime = 0L
    private val RPM_PAUSE_THRESHOLD = 5
    private val PAUSE_DELAY_MS = 3000L

    private val _ridePower = MutableStateFlow(0)
    val ridePower: StateFlow<Int> = _ridePower.asStateFlow()

    private val _rideRpm = MutableStateFlow(0)
    val rideRpm: StateFlow<Int> = _rideRpm.asStateFlow()

    private val _rideResistance = MutableStateFlow(0)
    val rideResistance: StateFlow<Int> = _rideResistance.asStateFlow()

    private val _rideCalories = MutableStateFlow(0)
    val rideCalories: StateFlow<Int> = _rideCalories.asStateFlow()

    private val _rideElapsedSeconds = MutableStateFlow(0)
    val rideElapsedSeconds: StateFlow<Int> = _rideElapsedSeconds.asStateFlow()

    private val _rideSpeedMph = MutableStateFlow(0f)
    val rideSpeedMph: StateFlow<Float> = _rideSpeedMph.asStateFlow()

    private val _rideDistanceMiles = MutableStateFlow(0f)
    val rideDistanceMiles: StateFlow<Float> = _rideDistanceMiles.asStateFlow()

    private val _rideHeartRate = MutableStateFlow(0)
    val rideHeartRate: StateFlow<Int> = _rideHeartRate.asStateFlow()

    private val _powerHistory = MutableStateFlow<List<Int>>(emptyList())
    val powerHistory: StateFlow<List<Int>> = _powerHistory.asStateFlow()

    private val _lastOverlaySummary = MutableStateFlow<RideSummary?>(null)
    val lastOverlaySummary: StateFlow<RideSummary?> = _lastOverlaySummary.asStateFlow()

    fun setOverlaySummary(summary: RideSummary) {
        _lastOverlaySummary.value = summary
    }

    fun clearOverlaySummary() {
        _lastOverlaySummary.value = null
    }

    // Delegate connection state to bridge
    val rideConnected: StateFlow<Boolean> get() = bridge.connected

    // Internal state
    private var rideStartTime = 0L
    private var ridePowerSum = 0L
    private var rideRpmSum = 0L
    private var rideResistanceSum = 0L
    private var rideSampleCount = 0
    private var rideMaxPower = 0
    private var heartRateSum = 0L
    private var heartRateSamples = 0
    private var rideTimerJob: Job? = null
    private var sensorCollectorJob: Job? = null
    private var heartRateCollectorJob: Job? = null
    private var workoutWatcherJob: Job? = null
    private var lastSampleTime = 0L

    fun startRide() {
        if (_rideActive.value) return

        // Start workout on the bridge
        val ok = bridge.startWorkout()
        if (!ok && !bridge.workoutActive.value) {
            // Bridge not connected or couldn't start — still allow tracking
            // (the bridge may connect later)
        }

        _rideActive.value = true
        _ridePaused.value = false
        totalPausedMs = 0L
        rideStartTime = System.currentTimeMillis()
        lastSampleTime = rideStartTime
        lastPedalingTime = rideStartTime
        ridePowerSum = 0
        rideRpmSum = 0
        rideResistanceSum = 0
        rideSampleCount = 0
        rideMaxPower = 0
        heartRateSum = 0
        heartRateSamples = 0
        _powerHistory.value = emptyList()
        _ridePower.value = 0
        _rideRpm.value = 0
        _rideResistance.value = 0
        _rideCalories.value = 0
        _rideElapsedSeconds.value = 0
        _rideSpeedMph.value = 0f
        _rideDistanceMiles.value = 0f
        _rideHeartRate.value = 0

        // Set launcher as the active owner (internal ride, no external app)
        bridge.setActiveOwner("io.freewheel.launcher", "VeloLauncher", null)

        // Collect sensor data from bridge
        sensorCollectorJob = scope.launch {
            bridge.sensorData.collect { data ->
                if (_rideActive.value) {
                    processSensorData(data)
                }
            }
        }

        // Collect heart rate from bridge
        heartRateCollectorJob = scope.launch {
            bridge.heartRate.collect { hr ->
                _rideHeartRate.value = hr
                if (hr > 0) {
                    heartRateSum += hr
                    heartRateSamples++
                }
            }
        }

        // Watch for external workout stop (e.g. watchdog)
        workoutWatcherJob = scope.launch {
            bridge.workoutActive.collect { active ->
                if (!active && _rideActive.value) {
                    stopRide()
                }
            }
        }

        // Timer
        rideTimerJob = scope.launch {
            while (_rideActive.value) {
                if (!_ridePaused.value) {
                    _rideElapsedSeconds.value = ((System.currentTimeMillis() - rideStartTime - totalPausedMs) / 1000).toInt()
                    _powerHistory.value = _powerHistory.value + _ridePower.value
                }
                delay(1000)
            }
        }
    }

    fun stopRide(workoutId: String? = null, workoutName: String? = null): RideSummary? {
        if (!_rideActive.value) return null
        _rideActive.value = false
        rideTimerJob?.cancel()
        rideTimerJob = null
        sensorCollectorJob?.cancel()
        sensorCollectorJob = null
        heartRateCollectorJob?.cancel()
        heartRateCollectorJob = null
        workoutWatcherJob?.cancel()
        workoutWatcherJob = null

        bridge.stopWorkout()

        val elapsed = _rideElapsedSeconds.value
        if (elapsed < 30) return null // don't save rides shorter than 30s

        val avgPower = if (rideSampleCount > 0) (ridePowerSum / rideSampleCount).toInt() else 0
        val avgRpm = if (rideSampleCount > 0) (rideRpmSum / rideSampleCount).toInt() else 0
        val avgRes = if (rideSampleCount > 0) (rideResistanceSum / rideSampleCount).toInt() else 0
        val avgSpeed = if (elapsed > 0) _rideDistanceMiles.value / (elapsed / 3600f) else 0f
        val avgHr = if (heartRateSamples > 0) (heartRateSum / heartRateSamples).toInt() else 0

        scope.launch {
            rideRepository.insert(
                RideRecord(
                    startTime = rideStartTime,
                    durationSeconds = elapsed,
                    calories = _rideCalories.value,
                    avgRpm = avgRpm,
                    avgPowerWatts = avgPower,
                    maxPowerWatts = rideMaxPower,
                    avgSpeedMph = avgSpeed,
                    distanceMiles = _rideDistanceMiles.value,
                    avgResistance = avgRes,
                    avgHeartRate = avgHr,
                )
            )
        }

        return RideSummary(
            durationSeconds = elapsed,
            calories = _rideCalories.value,
            avgPower = avgPower,
            maxPower = rideMaxPower,
            avgRpm = avgRpm,
            avgResistance = avgRes,
            avgHeartRate = avgHr,
            avgSpeedMph = avgSpeed,
            distanceMiles = _rideDistanceMiles.value,
            workoutName = workoutName,
        )
    }

    private fun processSensorData(data: SensorData) {
        val power = data.power.toInt()
        _ridePower.value = power
        _rideRpm.value = data.rpm
        _rideResistance.value = data.resistanceLevel

        // Auto-pause: pause when not pedaling, resume when pedaling
        val now = System.currentTimeMillis()
        if (data.rpm >= RPM_PAUSE_THRESHOLD) {
            lastPedalingTime = now
            if (_ridePaused.value) {
                // Resume
                totalPausedMs += now - pauseStartTime
                _ridePaused.value = false
            }
        } else if (!_ridePaused.value && _rideActive.value && (now - lastPedalingTime > PAUSE_DELAY_MS)) {
            // Pause
            _ridePaused.value = true
            pauseStartTime = now
        }

        if (_ridePaused.value) {
            lastSampleTime = now // don't accumulate distance while paused
            return
        }

        // Speed from power using simplified cycling power model
        val currentSpeedMph = RidePhysics.speedMph(power)
        _rideSpeedMph.value = currentSpeedMph

        // Update distance incrementally
        val deltaHours = (now - lastSampleTime) / 3_600_000.0
        _rideDistanceMiles.value += (currentSpeedMph * deltaHours).toFloat()
        lastSampleTime = now

        ridePowerSum += power
        rideRpmSum += data.rpm
        rideResistanceSum += data.resistanceLevel
        rideSampleCount++
        if (power > rideMaxPower) rideMaxPower = power

        // Calories from average power
        val elapsedHours = (now - rideStartTime - totalPausedMs) / 3_600_000.0
        val avgPower = if (rideSampleCount > 0) ridePowerSum.toDouble() / rideSampleCount else 0.0
        _rideCalories.value = RidePhysics.calories(avgPower, elapsedHours)
    }

    fun destroy() {
        rideTimerJob?.cancel()
        sensorCollectorJob?.cancel()
        heartRateCollectorJob?.cancel()
        workoutWatcherJob?.cancel()
    }
}
