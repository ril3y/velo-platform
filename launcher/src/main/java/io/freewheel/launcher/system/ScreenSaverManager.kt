package io.freewheel.launcher.system

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreenSaverManager(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val isRideActive: () -> Boolean,
) {

    private val _screenDimmed = MutableStateFlow(false)
    val screenDimmed: StateFlow<Boolean> = _screenDimmed.asStateFlow()

    private val _screenOff = MutableStateFlow(false)
    val screenOff: StateFlow<Boolean> = _screenOff.asStateFlow()

    // Burn-in prevention: shift content position on each wake
    private val _offsetX = MutableStateFlow(0)
    val offsetX: StateFlow<Int> = _offsetX.asStateFlow()
    private val _offsetY = MutableStateFlow(0)
    val offsetY: StateFlow<Int> = _offsetY.asStateFlow()
    private var shiftIndex = 0
    private val shiftPattern = listOf(0, 5, -5, 10, -10, 3, -8, 7, -3, 8) // px offsets

    private val _dimMinutes = MutableStateFlow(prefs.getInt("burnin_dim_minutes", 5))
    val dimMinutes: StateFlow<Int> = _dimMinutes.asStateFlow()

    private val _offMinutes = MutableStateFlow(prefs.getInt("burnin_off_minutes", 15))
    val offMinutes: StateFlow<Int> = _offMinutes.asStateFlow()

    private var lastInteractionTime = System.currentTimeMillis()
    private var screenSaverJob: Job? = null

    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        // On wake from dimmed/off, cycle the burn-in shift
        if (_screenDimmed.value || _screenOff.value) {
            shiftIndex = (shiftIndex + 1) % shiftPattern.size
            _offsetX.value = shiftPattern[shiftIndex]
            _offsetY.value = shiftPattern[(shiftIndex + 3) % shiftPattern.size]
        }
        _screenDimmed.value = false
        _screenOff.value = false
    }

    fun setDimMinutes(minutes: Int) {
        _dimMinutes.value = minutes
        prefs.edit().putInt("burnin_dim_minutes", minutes).apply()
    }

    fun setOffMinutes(minutes: Int) {
        _offMinutes.value = minutes
        prefs.edit().putInt("burnin_off_minutes", minutes).apply()
    }

    fun start() {
        screenSaverJob = scope.launch {
            while (true) {
                delay(10_000)
                if (isRideActive()) continue // never dim during ride

                val dimTimeoutMs = _dimMinutes.value * 60 * 1000L
                val offTimeoutMs = _offMinutes.value * 60 * 1000L
                val idle = System.currentTimeMillis() - lastInteractionTime
                when {
                    idle >= offTimeoutMs -> {
                        _screenOff.value = true
                        _screenDimmed.value = true
                    }
                    idle >= dimTimeoutMs -> {
                        _screenDimmed.value = true
                        _screenOff.value = false
                    }
                    else -> {
                        _screenDimmed.value = false
                        _screenOff.value = false
                    }
                }
            }
        }
    }

    fun destroy() {
        screenSaverJob?.cancel()
    }
}
