package io.freewheel.freeride

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class RideViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = RideSessionManager(application, viewModelScope)

    val rideActive get() = sessionManager.rideActive
    val ridePower get() = sessionManager.ridePower
    val rideRpm get() = sessionManager.rideRpm
    val rideResistance get() = sessionManager.rideResistance
    val rideCalories get() = sessionManager.rideCalories
    val rideElapsedSeconds get() = sessionManager.rideElapsedSeconds
    val rideSpeedMph get() = sessionManager.rideSpeedMph
    val rideDistanceMiles get() = sessionManager.rideDistanceMiles
    val rideHeartRate get() = sessionManager.rideHeartRate
    val rideConnected get() = sessionManager.rideConnected
    val powerHistory get() = sessionManager.powerHistory

    fun startRide() = sessionManager.startRide()

    fun reconnect() = sessionManager.reconnect()

    fun stopRide(workoutId: String? = null, workoutName: String? = null): RideSummary? =
        sessionManager.stopRide(workoutId, workoutName)

    override fun onCleared() {
        super.onCleared()
        sessionManager.destroy()
    }
}
