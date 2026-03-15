package io.freewheel.launcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RideRecord::class, UserProfile::class, WorkoutEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: RideDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to rides table
                db.execSQL("ALTER TABLE rides ADD COLUMN avgHeartRate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN source TEXT NOT NULL DEFAULT 'launcher'")
                db.execSQL("ALTER TABLE rides ADD COLUMN sourceLabel TEXT NOT NULL DEFAULT 'VeloLauncher'")
                db.execSQL("ALTER TABLE rides ADD COLUMN workoutId TEXT")
                db.execSQL("ALTER TABLE rides ADD COLUMN workoutName TEXT")

                // Create user_profile table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL DEFAULT '',
                        weightLbs INTEGER NOT NULL DEFAULT 0,
                        heightInches INTEGER NOT NULL DEFAULT 0,
                        age INTEGER NOT NULL DEFAULT 0,
                        gender TEXT NOT NULL DEFAULT '',
                        ftp INTEGER NOT NULL DEFAULT 0,
                        maxHeartRate INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workouts (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'General',
                        coach TEXT NOT NULL,
                        optionalMedia INTEGER NOT NULL DEFAULT 0,
                        color TEXT NOT NULL DEFAULT '#22D3EE',
                        segmentsJson TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT 'builtin',
                        sourceLabel TEXT NOT NULL DEFAULT 'VeloLauncher',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): RideDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "ride_history.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
