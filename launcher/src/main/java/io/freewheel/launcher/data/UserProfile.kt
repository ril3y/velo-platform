package io.freewheel.launcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // singleton row
    val displayName: String = "",
    val weightLbs: Int = 0,
    val heightInches: Int = 0,
    val age: Int = 0,
    val gender: String = "", // "male", "female", "other"
    val ftp: Int = 0, // functional threshold power
    val maxHeartRate: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
