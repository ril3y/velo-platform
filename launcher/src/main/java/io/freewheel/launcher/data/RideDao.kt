package io.freewheel.launcher.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insert(ride: RideRecord): Long

    @Query("SELECT * FROM rides ORDER BY startTime DESC LIMIT :limit")
    fun getRecentRides(limit: Int = 5): Flow<List<RideRecord>>

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<RideRecord>>

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRidesCursor(): Cursor

    @Query("SELECT * FROM rides WHERE id = :id")
    fun getRideCursor(id: Long): Cursor

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM rides")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM rides")
    suspend fun count(): Int
}
