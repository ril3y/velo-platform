package io.freewheel.launcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val type: String,
    val category: String = "General",
    val coach: String,
    val optionalMedia: Boolean = false,
    val color: String = "#22D3EE",
    val segmentsJson: String, // JSON array of segments
    val source: String = "builtin", // "builtin" for launcher assets, app package for imported
    val sourceLabel: String = "VeloLauncher",
    val createdAt: Long = System.currentTimeMillis(),
)
