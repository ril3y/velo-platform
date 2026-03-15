package io.freewheel.launcher.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.freewheel.launcher.VeloLauncherApp
import org.json.JSONArray
import org.json.JSONObject

class VeloContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.freewheel.launcher.provider"
        private const val DEFAULT_FTP = 120  // ~80kg × 1.5 W/kg

        private const val WORKOUTS = 1
        private const val WORKOUT_BY_ID = 2
        private const val RIDES = 3
        private const val RIDE_BY_ID = 4
        private const val PROFILE = 5
        private const val TARGET_POWER = 6
        private const val FITNESS_CONFIG = 7
        private const val SESSION = 8
        private const val SESSION_SENSOR = 9

        val URI_WORKOUTS: Uri = Uri.parse("content://$AUTHORITY/workouts")
        val URI_RIDES: Uri = Uri.parse("content://$AUTHORITY/rides")
        val URI_PROFILE: Uri = Uri.parse("content://$AUTHORITY/profile")
        val URI_TARGET_POWER: Uri = Uri.parse("content://$AUTHORITY/target_power")
        val URI_FITNESS_CONFIG: Uri = Uri.parse("content://$AUTHORITY/fitness_config")
        val URI_SESSION: Uri = Uri.parse("content://$AUTHORITY/session")
        val URI_SESSION_SENSOR: Uri = Uri.parse("content://$AUTHORITY/session/sensor")

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "workouts", WORKOUTS)
            addURI(AUTHORITY, "workouts/*", WORKOUT_BY_ID)
            addURI(AUTHORITY, "rides", RIDES)
            addURI(AUTHORITY, "rides/#", RIDE_BY_ID)
            addURI(AUTHORITY, "profile", PROFILE)
            addURI(AUTHORITY, "target_power/#", TARGET_POWER)  // /target_power/{resistance}
            addURI(AUTHORITY, "fitness_config", FITNESS_CONFIG)
            addURI(AUTHORITY, "session", SESSION)
            addURI(AUTHORITY, "session/sensor", SESSION_SENSOR)
        }
    }

    private lateinit var db: RideDatabase
    private lateinit var workoutRepo: WorkoutRepository

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        db = RideDatabase.getInstance(ctx)
        workoutRepo = WorkoutRepository(ctx.applicationContext as android.app.Application)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            WORKOUTS -> queryWorkouts()
            WORKOUT_BY_ID -> queryWorkoutById(uri.lastPathSegment ?: "")
            RIDES -> db.rideDao().getAllRidesCursor()
            RIDE_BY_ID -> {
                val id = ContentUris.parseId(uri)
                db.rideDao().getRideCursor(id)
            }
            PROFILE -> queryProfile()
            TARGET_POWER -> {
                val resistance = uri.lastPathSegment?.toIntOrNull() ?: 1
                queryTargetPower(resistance)
            }
            FITNESS_CONFIG -> queryFitnessConfig()
            SESSION -> querySession()
            SESSION_SENSOR -> querySessionSensor()
            else -> null
        }
    }

    private fun queryWorkouts(): Cursor {
        val workouts = workoutRepo.getWorkouts()
        val cursor = MatrixCursor(arrayOf(
            "id", "name", "description", "durationMinutes", "type",
            "category", "coach", "optionalMedia", "color", "segmentCount",
        ))
        for (w in workouts) {
            cursor.addRow(arrayOf(
                w.id, w.name, w.description, w.durationMinutes, w.type,
                w.category, w.coach, if (w.optionalMedia) 1 else 0, w.color,
                w.segments.size,
            ))
        }
        return cursor
    }

    private fun queryWorkoutById(id: String): Cursor {
        val workout = workoutRepo.getWorkoutById(id)
        val cursor = MatrixCursor(arrayOf(
            "id", "name", "description", "durationMinutes", "type",
            "category", "coach", "optionalMedia", "color", "segments_json",
        ))
        if (workout != null) {
            val segJson = JSONArray()
            for (seg in workout.segments) {
                segJson.put(JSONObject().apply {
                    put("label", seg.label)
                    put("durationSeconds", seg.durationSeconds)
                    put("resistance", seg.resistance)
                    put("message", seg.message)
                })
            }
            cursor.addRow(arrayOf(
                workout.id, workout.name, workout.description,
                workout.durationMinutes, workout.type, workout.category,
                workout.coach, if (workout.optionalMedia) 1 else 0,
                workout.color, segJson.toString(),
            ))
        }
        return cursor
    }

    private fun queryProfile(): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "displayName", "weightLbs", "heightInches", "age",
            "gender", "ftp", "maxHeartRate",
        ))
        // Profile query is synchronous via a direct SQL query
        val profileDb = db.openHelper.readableDatabase
        val c = profileDb.query("SELECT * FROM user_profile WHERE id = 1")
        if (c.moveToFirst()) {
            cursor.addRow(arrayOf(
                c.getString(c.getColumnIndexOrThrow("displayName")),
                c.getInt(c.getColumnIndexOrThrow("weightLbs")),
                c.getInt(c.getColumnIndexOrThrow("heightInches")),
                c.getInt(c.getColumnIndexOrThrow("age")),
                c.getString(c.getColumnIndexOrThrow("gender")),
                c.getInt(c.getColumnIndexOrThrow("ftp")),
                c.getInt(c.getColumnIndexOrThrow("maxHeartRate")),
            ))
        }
        c.close()
        return cursor
    }

    /**
     * Returns the target power range for a given resistance level based on user profile.
     * Maps resistance 1-25 to a fraction of FTP with ±15% tolerance band.
     */
    private fun queryTargetPower(resistance: Int): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "resistance", "ftp", "targetLow", "targetHigh", "centerPower",
        ))
        val ftp = getProfileFtp()
        val effortFraction = 0.10f + (resistance.coerceIn(1, 25) - 1) * (0.90f / 24f)
        val centerPower = ftp * effortFraction
        val lo = (centerPower * 0.85f).toInt()
        val hi = (centerPower * 1.15f).toInt()
        cursor.addRow(arrayOf(resistance, ftp.toInt(), lo, hi, centerPower.toInt()))
        return cursor
    }

    /**
     * Returns the full fitness config: FTP, max HR, weight, default power ranges.
     * Apps use this to configure their effort bars and graphs without duplicating logic.
     */
    private fun queryFitnessConfig(): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "ftp", "maxHeartRate", "weightLbs", "age",
        ))
        val profileDb = db.openHelper.readableDatabase
        val c = profileDb.query("SELECT * FROM user_profile WHERE id = 1")
        if (c.moveToFirst()) {
            val ftp = c.getInt(c.getColumnIndexOrThrow("ftp"))
            val maxHr = c.getInt(c.getColumnIndexOrThrow("maxHeartRate"))
            val weight = c.getInt(c.getColumnIndexOrThrow("weightLbs"))
            val age = c.getInt(c.getColumnIndexOrThrow("age"))
            val effectiveFtp = if (ftp > 0) ftp else defaultFtp(weight)
            val effectiveMaxHr = if (maxHr > 0) maxHr else (208 - (0.7f * age).toInt())
            cursor.addRow(arrayOf(effectiveFtp, effectiveMaxHr, weight, age))
        } else {
            // No profile — return safe defaults
            cursor.addRow(arrayOf(DEFAULT_FTP, 180, 170, 35))
        }
        c.close()
        return cursor
    }

    private fun getProfileFtp(): Float {
        val profileDb = db.openHelper.readableDatabase
        val c = profileDb.query("SELECT ftp, weightLbs FROM user_profile WHERE id = 1")
        val ftp = if (c.moveToFirst()) {
            val stored = c.getInt(c.getColumnIndexOrThrow("ftp"))
            if (stored > 0) stored.toFloat()
            else {
                val weight = c.getInt(c.getColumnIndexOrThrow("weightLbs"))
                defaultFtp(weight).toFloat()
            }
        } else {
            DEFAULT_FTP.toFloat()
        }
        c.close()
        return ftp
    }

    /** Default FTP when user hasn't set one: ~1.5 W/kg (recreational rider) */
    private fun defaultFtp(weightLbs: Int): Int {
        val weightKg = if (weightLbs > 0) weightLbs * 0.4536f else 80f
        return (weightKg * 1.5f).toInt().coerceAtLeast(80)
    }

    private fun querySession(): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "active", "ownerPackage", "ownerLabel", "startTime", "elapsedSeconds",
        ))
        try {
            val bridge = VeloLauncherApp.get(context!!).bridgeConnectionManager
            val session = bridge.sessionState.value
            cursor.addRow(arrayOf(
                if (session.active) 1 else 0,
                session.ownerPackage ?: "",
                session.ownerLabel ?: "",
                session.startTime,
                session.elapsedSeconds,
            ))
        } catch (_: Exception) {
            cursor.addRow(arrayOf(0, "", "", 0L, 0))
        }
        return cursor
    }

    private fun querySessionSensor(): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "resistance", "rpm", "tilt", "power",
            "crankRevCount", "crankEventTime", "heartRate", "hrmDeviceName",
        ))
        try {
            val bridge = VeloLauncherApp.get(context!!).bridgeConnectionManager
            val data = bridge.sensorData.value
            val hr = bridge.heartRate.value
            val hrmName = bridge.hrmDeviceName.value ?: ""
            cursor.addRow(arrayOf(
                data.resistanceLevel, data.rpm, data.tilt, data.power,
                data.crankRevCount, data.crankEventTime, hr, hrmName,
            ))
        } catch (_: Exception) {
            cursor.addRow(arrayOf(0, 0, 0, 0f, 0L, 0, 0, ""))
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (values == null) return null

        return when (uriMatcher.match(uri)) {
            RIDES -> insertRide(values)
            WORKOUTS -> insertWorkout(values)
            else -> null
        }
    }

    private fun insertWorkout(values: ContentValues): Uri? {
        val entity = WorkoutEntity(
            id = values.getAsString("id") ?: java.util.UUID.randomUUID().toString(),
            name = values.getAsString("name") ?: "Imported Workout",
            description = values.getAsString("description") ?: "",
            durationMinutes = values.getAsInteger("durationMinutes") ?: 0,
            type = values.getAsString("type") ?: "custom",
            category = values.getAsString("category") ?: "Imported",
            coach = values.getAsString("coach") ?: "",
            optionalMedia = (values.getAsInteger("optionalMedia") ?: 0) != 0,
            color = values.getAsString("color") ?: "#22D3EE",
            segmentsJson = values.getAsString("segmentsJson") ?: "[]",
            source = values.getAsString("source") ?: "external",
            sourceLabel = values.getAsString("sourceLabel") ?: "External App",
        )

        // Insert synchronously via raw SQL (ContentProvider handles threading)
        val sqlDb = db.openHelper.writableDatabase
        val cv = android.content.ContentValues().apply {
            put("id", entity.id)
            put("name", entity.name)
            put("description", entity.description)
            put("durationMinutes", entity.durationMinutes)
            put("type", entity.type)
            put("category", entity.category)
            put("coach", entity.coach)
            put("optionalMedia", if (entity.optionalMedia) 1 else 0)
            put("color", entity.color)
            put("segmentsJson", entity.segmentsJson)
            put("source", entity.source)
            put("sourceLabel", entity.sourceLabel)
            put("createdAt", entity.createdAt)
        }
        sqlDb.insert("workouts", 0, cv)

        context?.contentResolver?.notifyChange(URI_WORKOUTS, null)
        return Uri.withAppendedPath(URI_WORKOUTS, entity.id)
    }

    private fun insertRide(values: ContentValues): Uri? {

        val ride = RideRecord(
            startTime = values.getAsLong("startTime") ?: System.currentTimeMillis(),
            durationSeconds = values.getAsInteger("durationSeconds") ?: 0,
            calories = values.getAsInteger("calories") ?: 0,
            avgRpm = values.getAsInteger("avgRpm") ?: 0,
            avgPowerWatts = values.getAsInteger("avgPowerWatts") ?: 0,
            maxPowerWatts = values.getAsInteger("maxPowerWatts") ?: 0,
            avgSpeedMph = values.getAsFloat("avgSpeedMph") ?: 0f,
            distanceMiles = values.getAsFloat("distanceMiles") ?: 0f,
            avgResistance = values.getAsInteger("avgResistance") ?: 0,
            avgHeartRate = values.getAsInteger("avgHeartRate") ?: 0,
            source = values.getAsString("source") ?: "external",
            sourceLabel = values.getAsString("sourceLabel") ?: "External App",
            workoutId = values.getAsString("workoutId"),
            workoutName = values.getAsString("workoutName"),
        )

        // Insert synchronously on the calling thread (ContentProvider handles threading)
        val id = db.openHelper.writableDatabase.let { sqlDb ->
            val cv = android.content.ContentValues().apply {
                put("startTime", ride.startTime)
                put("durationSeconds", ride.durationSeconds)
                put("calories", ride.calories)
                put("avgRpm", ride.avgRpm)
                put("avgPowerWatts", ride.avgPowerWatts)
                put("maxPowerWatts", ride.maxPowerWatts)
                put("avgSpeedMph", ride.avgSpeedMph)
                put("distanceMiles", ride.distanceMiles)
                put("avgResistance", ride.avgResistance)
                put("avgHeartRate", ride.avgHeartRate)
                put("source", ride.source)
                put("sourceLabel", ride.sourceLabel)
                put("workoutId", ride.workoutId)
                put("workoutName", ride.workoutName)
            }
            sqlDb.insert("rides", 0, cv)
        }

        context?.contentResolver?.notifyChange(URI_RIDES, null)
        return ContentUris.withAppendedId(URI_RIDES, id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        WORKOUTS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.workouts"
        WORKOUT_BY_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.workouts"
        RIDES -> "vnd.android.cursor.dir/vnd.$AUTHORITY.rides"
        RIDE_BY_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.rides"
        PROFILE -> "vnd.android.cursor.item/vnd.$AUTHORITY.profile"
        TARGET_POWER -> "vnd.android.cursor.item/vnd.$AUTHORITY.target_power"
        FITNESS_CONFIG -> "vnd.android.cursor.item/vnd.$AUTHORITY.fitness_config"
        SESSION -> "vnd.android.cursor.item/vnd.$AUTHORITY.session"
        SESSION_SENSOR -> "vnd.android.cursor.item/vnd.$AUTHORITY.session_sensor"
        else -> null
    }
}
