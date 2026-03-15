package io.freewheel.freeride

import android.content.Context
import io.freewheel.fit.RideStats
import io.freewheel.fit.VeloFitnessClient
import io.freewheel.fit.WorkoutSessionClient
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
) {
    companion object {
        private const val TAG = "FreeRide"
    }

    private val fitnessClient = VeloFitnessClient(context)

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

    private val _powerHistory = MutableStateFlow<List<Int>>(emptyList())
    val powerHistory: StateFlow<List<Int>> = _powerHistory.asStateFlow()

    // Internal state
    private var bikeClient: WorkoutSessionClient? = null
    private var rideStartTime = 0L
    private var ridePowerSum = 0L
    private var rideRpmSum = 0L
    private var rideResistanceSum = 0L
    private var rideSampleCount = 0
    private var rideMaxPower = 0
    private var rideTimerJob: Job? = null
    private var lastSampleTime = 0L
    private var heartRateSum = 0L

    fun startRide() {
        if (_rideActive.value) return
        _rideActive.value = true
        rideStartTime = System.currentTimeMillis()
        lastSampleTime = rideStartTime
        ridePowerSum = 0
        rideRpmSum = 0
        rideResistanceSum = 0
        heartRateSum = 0
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
        _powerHistory.value = emptyList()

        connectBike()
    }

    /**
     * Reconnect after resume from background.
     * WorkoutSessionClient's AIDL binding persists across activity lifecycle,
     * so just check if still bound and re-bind if needed.
     */
    fun reconnect() {
        val client = bikeClient
        if (client == null) {
            android.util.Log.i(TAG, "reconnect: no client, creating new connection")
            if (_rideActive.value) connectBike()
            return
        }
        if (!client.isBound) {
            android.util.Log.i(TAG, "reconnect: not bound, re-binding")
            client.bind()
        }
    }

    private fun connectBike() {
        bikeClient?.unbind()

        val client = WorkoutSessionClient(context)
        bikeClient = client
        client.addListener(object : WorkoutSessionClient.ListenerAdapter() {
            override fun onSensorData(resistance: Int, rpm: Int, tilt: Int, power: Float,
                                      crankRevCount: Long, crankEventTime: Int) {
                val powerInt = power.toInt()
                _ridePower.value = powerInt
                _rideRpm.value = rpm
                _rideResistance.value = resistance

                val speedMps = if (powerInt > 0) Math.cbrt(powerInt.toDouble() / 4.0) else 0.0
                val currentSpeedMph = (speedMps * 2.24).toFloat().coerceAtMost(45f)
                _rideSpeedMph.value = currentSpeedMph

                val now = System.currentTimeMillis()
                val deltaHours = (now - lastSampleTime) / 3_600_000.0
                _rideDistanceMiles.value += (currentSpeedMph * deltaHours).toFloat()
                lastSampleTime = now

                ridePowerSum += powerInt
                rideRpmSum += rpm
                rideResistanceSum += resistance
                rideSampleCount++
                if (powerInt > rideMaxPower) rideMaxPower = powerInt

                val elapsedHours = (now - rideStartTime) / 3_600_000.0
                val avgPower = if (rideSampleCount > 0) ridePowerSum.toDouble() / rideSampleCount else 0.0
                _rideCalories.value = ((avgPower / 0.25) * elapsedHours / 1.163).toInt()
            }

            override fun onHeartRate(bpm: Int, deviceName: String?) {
                if (bpm > 0) {
                    _rideHeartRate.value = bpm
                    heartRateSum += bpm
                }
            }

            override fun onConnectionChanged(connected: Boolean, message: String?) {
                android.util.Log.d(TAG, "Session connection: $connected ($message)")
                _rideConnected.value = connected
            }

            override fun onServiceConnected() {
                android.util.Log.i(TAG, "WorkoutSession service connected, requesting start")
                client.requestStart()
            }

            override fun onWorkoutStateChanged(active: Boolean, reason: String?) {
                if (!active && _rideActive.value) {
                    android.util.Log.w(TAG, "Workout stopped externally: $reason")
                    scope.launch { stopRide() }
                }
            }
        })
        client.bind()

        rideTimerJob = scope.launch {
            while (_rideActive.value) {
                _rideElapsedSeconds.value = ((System.currentTimeMillis() - rideStartTime) / 1000).toInt()
                _powerHistory.value = _powerHistory.value + _ridePower.value
                delay(1000)
            }
        }
    }

    fun stopRide(workoutId: String? = null, workoutName: String? = null): RideSummary? {
        if (!_rideActive.value) return null
        _rideActive.value = false
        rideTimerJob?.cancel()
        rideTimerJob = null

        bikeClient?.requestStop()
        bikeClient?.unbind()
        bikeClient = null

        val elapsed = _rideElapsedSeconds.value
        val avgPower = if (rideSampleCount > 0) (ridePowerSum / rideSampleCount).toInt() else 0
        val avgRpm = if (rideSampleCount > 0) (rideRpmSum / rideSampleCount).toInt() else 0
        val avgRes = if (rideSampleCount > 0) (rideResistanceSum / rideSampleCount).toInt() else 0
        val avgHr = if (rideSampleCount > 0) (heartRateSum / rideSampleCount).toInt() else 0
        val avgSpeed = if (elapsed > 0) _rideDistanceMiles.value / (elapsed / 3600f) else 0f

        val summary = RideSummary(
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

        // Only log rides >= 1 minute
        if (elapsed >= 60) {
            val stats = RideStats.Builder()
                .startTime(rideStartTime)
                .durationSeconds(elapsed)
                .calories(_rideCalories.value)
                .avgPower(avgPower)
                .avgRpm(avgRpm)
                .avgResistance(avgRes)
                .maxPower(rideMaxPower)
                .distanceMiles(_rideDistanceMiles.value)
                .avgSpeedMph(avgSpeed)
                .avgHeartRate(avgHr)
                .source("io.freewheel.freeride", "FreeRide")
                .apply {
                    if (workoutId != null) workout(workoutId!!, workoutName ?: "")
                }
                .build()
            fitnessClient.logRide(stats)
        }

        return summary
    }

    fun destroy() {
        bikeClient?.requestStop()
        bikeClient?.unbind()
        bikeClient = null
    }
}
