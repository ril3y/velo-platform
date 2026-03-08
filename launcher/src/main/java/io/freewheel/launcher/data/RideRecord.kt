package io.freewheel.launcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val calories: Int = 0,
    val avgRpm: Int = 0,
    val avgPowerWatts: Int = 0,
    val maxPowerWatts: Int = 0,
    val avgSpeedMph: Float = 0f,
    val distanceMiles: Float = 0f,
    val avgResistance: Int = 0,
    val avgHeartRate: Int = 0,
    // Source tracking — which app logged this ride
    val source: String = "launcher", // "launcher", "bikearcade", "jrny_import", etc.
    val sourceLabel: String = "VeloLauncher", // display name
    // Workout link
    val workoutId: String? = null, // links to workout JSON id
    val workoutName: String? = null, // display name even if workout deleted
)
