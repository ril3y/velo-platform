package io.freewheel.launcher.system

import android.content.ContentResolver
import android.content.SharedPreferences
import android.provider.Settings
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
    private val contentResolver: ContentResolver,
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
    private var savedBrightness: Int = -1

    fun onUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        // On wake from dimmed/off, cycle the burn-in shift
        if (_screenDimmed.value || _screenOff.value) {
            shiftIndex = (shiftIndex + 1) % shiftPattern.size
            _offsetX.value = shiftPattern[shiftIndex]
            _offsetY.value = shiftPattern[(shiftIndex + 3) % shiftPattern.size]
            restoreBrightness()
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
                        // Don't set system brightness to 0 — the Compose overlay
                        // already covers the screen with an opaque black box.
                        // Setting brightness=0 via Settings makes the device
                        // unrecoverable if the overlay fails to render.
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

    private fun setBrightness(value: Int) {
        try {
            if (savedBrightness < 0) {
                savedBrightness = Settings.System.getInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    128,
                )
            }
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value,
            )
        } catch (_: Exception) {
            // WRITE_SETTINGS not granted — fall back to overlay-only
        }
    }

    private fun restoreBrightness() {
        if (savedBrightness >= 0) {
            try {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    savedBrightness,
                )
            } catch (_: Exception) {}
            savedBrightness = -1
        }
    }

    fun destroy() {
        screenSaverJob?.cancel()
        restoreBrightness()
    }
}
