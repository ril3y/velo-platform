package io.freewheel.freeride

import android.content.Intent

data class RideSummary(
    val durationSeconds: Int,
    val calories: Int,
    val avgPower: Int,
    val maxPower: Int,
    val avgRpm: Int,
    val avgResistance: Int,
    val avgHeartRate: Int,
    val avgSpeedMph: Float,
    val distanceMiles: Float,
    val workoutName: String?,
) {
    fun toIntent(intent: Intent): Intent = intent.apply {
        putExtra("duration", durationSeconds)
        putExtra("calories", calories)
        putExtra("avgPower", avgPower)
        putExtra("maxPower", maxPower)
        putExtra("avgRpm", avgRpm)
        putExtra("avgResistance", avgResistance)
        putExtra("avgHeartRate", avgHeartRate)
        putExtra("avgSpeed", avgSpeedMph)
        putExtra("distance", distanceMiles)
        putExtra("workoutName", workoutName)
    }

    companion object {
        fun fromIntent(intent: Intent): RideSummary = RideSummary(
            durationSeconds = intent.getIntExtra("duration", 0),
            calories = intent.getIntExtra("calories", 0),
            avgPower = intent.getIntExtra("avgPower", 0),
            maxPower = intent.getIntExtra("maxPower", 0),
            avgRpm = intent.getIntExtra("avgRpm", 0),
            avgResistance = intent.getIntExtra("avgResistance", 0),
            avgHeartRate = intent.getIntExtra("avgHeartRate", 0),
            avgSpeedMph = intent.getFloatExtra("avgSpeed", 0f),
            distanceMiles = intent.getFloatExtra("distance", 0f),
            workoutName = intent.getStringExtra("workoutName"),
        )
    }
}
