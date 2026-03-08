package io.freewheel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.freewheel.launcher.data.RideDatabase
import io.freewheel.launcher.data.RideRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RideLogReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LOG_RIDE = "io.freewheel.launcher.ACTION_LOG_RIDE"
        private const val TAG = "RideLogReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG_RIDE) return

        val sourcePackage = intent.getStringExtra("source_package") ?: "unknown"
        val sourceLabel = intent.getStringExtra("source_label") ?: sourcePackage
        val durationSeconds = intent.getIntExtra("duration_seconds", 0)
        val calories = intent.getIntExtra("calories", 0)
        val distanceMiles = intent.getFloatExtra("distance_miles", 0f)
        val avgPower = intent.getIntExtra("avg_power", 0)
        val maxPower = intent.getIntExtra("max_power", 0)
        val avgRpm = intent.getIntExtra("avg_rpm", 0)
        val avgResistance = intent.getIntExtra("avg_resistance", 0)
        val avgHeartRate = intent.getIntExtra("avg_heart_rate", 0)
        val avgSpeed = intent.getFloatExtra("avg_speed", 0f)
        val workoutId = intent.getStringExtra("workout_id")
        val workoutName = intent.getStringExtra("workout_name")
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        Log.d(TAG, "Received ride log from $sourcePackage: ${durationSeconds}s, ${calories}cal")

        val ride = RideRecord(
            startTime = timestamp,
            durationSeconds = durationSeconds,
            calories = calories,
            avgRpm = avgRpm,
            avgPowerWatts = avgPower,
            maxPowerWatts = maxPower,
            avgSpeedMph = avgSpeed,
            distanceMiles = distanceMiles,
            avgResistance = avgResistance,
            avgHeartRate = avgHeartRate,
            source = sourcePackage,
            sourceLabel = sourceLabel,
            workoutId = workoutId,
            workoutName = workoutName,
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = RideDatabase.getInstance(context)
                db.rideDao().insert(ride)
                Log.d(TAG, "Ride logged successfully from $sourcePackage")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log ride", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
