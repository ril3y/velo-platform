package io.freewheel.launcher.data

import android.app.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class WorkoutRepository(private val application: Application) {

    private val workoutDao = RideDatabase.getInstance(application).workoutDao()
    private var seeded = false

    /** Ensure built-in workouts from assets are seeded into Room on first run. */
    suspend fun seedIfNeeded() {
        if (seeded) return
        val count = workoutDao.count()
        if (count == 0) {
            val entities = loadFromAssets()
            workoutDao.insertAll(entities)
        }
        seeded = true
    }

    /** Observable flow of all workouts (built-in + imported). */
    fun getAllWorkoutsFlow(): Flow<List<Workout>> {
        return workoutDao.getAll().map { entities -> entities.map { it.toWorkout() } }
    }

    /** Synchronous getter for backward compatibility (used by ContentProvider and UI). */
    fun getWorkouts(): List<Workout> {
        return runBlocking {
            seedIfNeeded()
            workoutDao.getAll().first().map { it.toWorkout() }
        }
    }

    fun getWorkoutById(id: String): Workout? {
        return runBlocking {
            seedIfNeeded()
            workoutDao.getById(id)?.toWorkout()
        }
    }

    suspend fun importWorkout(entity: WorkoutEntity) {
        workoutDao.insert(entity)
    }

    suspend fun deleteWorkout(id: String) {
        workoutDao.delete(id)
    }

    private fun loadFromAssets(): List<WorkoutEntity> {
        val files = application.assets.list("workouts") ?: emptyArray()
        return files
            .filter { it.endsWith(".json") }
            .map { filename ->
                val json = application.assets.open("workouts/$filename")
                    .bufferedReader().use { it.readText() }
                parseWorkoutEntity(JSONObject(json))
            }
    }

    private fun parseWorkoutEntity(obj: JSONObject): WorkoutEntity {
        return WorkoutEntity(
            id = obj.getString("id"),
            name = obj.getString("name"),
            description = obj.getString("description"),
            durationMinutes = obj.getInt("durationMinutes"),
            type = obj.getString("type"),
            category = obj.optString("category", "General"),
            coach = obj.getString("coach"),
            optionalMedia = obj.getBoolean("optionalMedia"),
            color = obj.optString("color", "#22D3EE"),
            segmentsJson = obj.getJSONArray("segments").toString(),
            source = "builtin",
            sourceLabel = "VeloLauncher",
        )
    }

    companion object {
        fun WorkoutEntity.toWorkout(): Workout {
            val segArray = JSONArray(segmentsJson)
            val segments = (0 until segArray.length()).map { i ->
                val seg = segArray.getJSONObject(i)
                WorkoutSegment(
                    label = seg.getString("label"),
                    durationSeconds = seg.getInt("durationSeconds"),
                    resistance = seg.getInt("resistance"),
                    message = seg.optString("message", ""),
                )
            }
            return Workout(
                id = id,
                name = name,
                description = description,
                durationMinutes = durationMinutes,
                type = type,
                category = category,
                coach = coach,
                optionalMedia = optionalMedia,
                color = color,
                segments = segments,
            )
        }
    }
}
