package io.freewheel.launcher.ride

import android.content.Context
import io.freewheel.ucb.BikeServiceClient
import io.freewheel.ucb.SensorData
import io.freewheel.launcher.data.RideRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RideSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rideRepository: RideRepository,
) {

    private val _rideActive = MutableStateFlow(false)
    val rideActive: StateFlow<Boolean> = _rideActive.asStateFlow()

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

    private val _rideConnected = MutableStateFlow(false)
    val rideConnected: StateFlow<Boolean> = _rideConnected.asStateFlow()

    // Internal state
    private var bikeClient: BikeServiceClient? = null
    private var rideStartTime = 0L
    private var ridePowerSum = 0L
    private var rideRpmSum = 0L
    private var rideResistanceSum = 0L
    private var rideSampleCount = 0
    private var rideMaxPower = 0
    private var rideTimerJob: Job? = null
    private var lastSampleTime = 0L

    fun startRide() {
        if (_rideActive.value) return
        _rideActive.value = true
        rideStartTime = System.currentTimeMillis()
        lastSampleTime = rideStartTime
        ridePowerSum = 0
        rideRpmSum = 0
        rideResistanceSum = 0
        rideSampleCount = 0
        rideMaxPower = 0
        _ridePower.value = 0
        _rideRpm.value = 0
        _rideResistance.value = 0
        _rideCalories.value = 0
        _rideElapsedSeconds.value = 0
        _rideSpeedMph.value = 0f
        _rideDistanceMiles.value = 0f
        _rideHeartRate.value = 0
        _rideConnected.value = false

        val client = BikeServiceClient(context)
        bikeClient = client
        client.addListener(object : BikeServiceClient.ListenerAdapter() {
            override fun onSensorData(data: SensorData) {
                val power = data.power.toInt()
                _ridePower.value = power
                _rideRpm.value = data.rpm
                _rideResistance.value = data.resistanceLevel

                // Speed from power using simplified cycling power model
                val speedMps = if (power > 0) Math.cbrt(power.toDouble() / 4.0) else 0.0
                val currentSpeedMph = (speedMps * 2.24).toFloat().coerceAtMost(45f)
                _rideSpeedMph.value = currentSpeedMph

                // Update distance incrementally
                val now = System.currentTimeMillis()
                val deltaHours = (now - lastSampleTime) / 3_600_000.0
                _rideDistanceMiles.value += (currentSpeedMph * deltaHours).toFloat()
                lastSampleTime = now

                ridePowerSum += power
                rideRpmSum += data.rpm
                rideResistanceSum += data.resistanceLevel
                rideSampleCount++
                if (power > rideMaxPower) rideMaxPower = power

                // Calories: metabolic cost = power / 0.25, kcal = cost * hours / 1.163
                val elapsedHours = (now - rideStartTime) / 3_600_000.0
                val avgPower = if (rideSampleCount > 0) ridePowerSum.toDouble() / rideSampleCount else 0.0
                _rideCalories.value = ((avgPower / 0.25) * elapsedHours / 1.163).toInt()
            }

            override fun onConnectionChanged(connected: Boolean, message: String) {
                _rideConnected.value = connected
            }

            override fun onServiceConnected() {
                _rideConnected.value = true
                // Service is connected, start the workout on SerialBridge
                client.startWorkout()
            }

            override fun onServiceDisconnected() {
                _rideConnected.value = false
            }

            override fun onWorkoutStateChanged(active: Boolean, reason: String) {
                // If watchdog stopped the workout, end the ride
                if (!active && _rideActive.value && reason != "client_requested") {
                    stopRide()
                }
            }
        })
        client.bind()

        // Timer
        rideTimerJob = scope.launch {
            while (_rideActive.value) {
                _rideElapsedSeconds.value = ((System.currentTimeMillis() - rideStartTime) / 1000).toInt()
                delay(1000)
            }
        }
    }

    fun stopRide() {
        if (!_rideActive.value) return
        _rideActive.value = false
        rideTimerJob?.cancel()
        rideTimerJob = null

        bikeClient?.stopWorkout()
        bikeClient?.unbind()
        bikeClient = null

        val elapsed = _rideElapsedSeconds.value
        if (elapsed < 30) return // don't save rides shorter than 30s

        val avgPower = if (rideSampleCount > 0) (ridePowerSum / rideSampleCount).toInt() else 0
        val avgRpm = if (rideSampleCount > 0) (rideRpmSum / rideSampleCount).toInt() else 0
        val avgRes = if (rideSampleCount > 0) (rideResistanceSum / rideSampleCount).toInt() else 0
        val avgSpeed = if (elapsed > 0) _rideDistanceMiles.value / (elapsed / 3600f) else 0f

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
                )
            )
        }
    }

    fun destroy() {
        bikeClient?.stopWorkout()
        bikeClient?.unbind()
        bikeClient = null
    }
}
