package io.freewheel.launcher.ride

import android.content.Context
import io.freewheel.launcher.data.RideDatabase
import io.freewheel.launcher.data.RideRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideRepository(context: Context) {

    private val db = RideDatabase.getInstance(context)

    fun getRecentRides(limit: Int = 5): Flow<List<RideRecord>> {
        return db.rideDao().getRecentRides(limit)
    }

    fun getAllRides(): Flow<List<RideRecord>> {
        return db.rideDao().getAllRides()
    }

    suspend fun insert(ride: RideRecord): Long {
        return withContext(Dispatchers.IO) {
            db.rideDao().insert(ride)
        }
    }

    suspend fun deleteById(id: Long) {
        withContext(Dispatchers.IO) {
            db.rideDao().deleteById(id)
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            db.rideDao().deleteAll()
        }
    }

    suspend fun count(): Int {
        return withContext(Dispatchers.IO) {
            db.rideDao().count()
        }
    }

    fun exportCsv(rides: List<RideRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Duration (min),Calories,Avg RPM,Avg Power (W),Max Power (W),Avg Speed (mph),Distance (mi),Avg Resistance")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        for (r in rides) {
            sb.appendLine(
                "${fmt.format(Date(r.startTime))},${r.durationSeconds / 60},${r.calories}," +
                "${r.avgRpm},${r.avgPowerWatts},${r.maxPowerWatts}," +
                "${"%.1f".format(r.avgSpeedMph)},${"%.2f".format(r.distanceMiles)},${r.avgResistance}"
            )
        }
        return sb.toString()
    }
}
