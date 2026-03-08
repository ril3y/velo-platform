package io.freewheel.launcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RideRecord::class, UserProfile::class],
    version = 2,
    exportSchema = false,
)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun userProfileDao(): UserProfileDao

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

        fun getInstance(context: Context): RideDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "ride_history.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
