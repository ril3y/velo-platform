package io.freewheel.launcher.data

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
)
