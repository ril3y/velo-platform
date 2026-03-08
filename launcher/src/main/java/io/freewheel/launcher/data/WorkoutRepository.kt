package io.freewheel.launcher.data

import android.app.Application
import org.json.JSONObject

class WorkoutRepository(private val application: Application) {

    private var cachedWorkouts: List<Workout>? = null

    fun getWorkouts(): List<Workout> {
        cachedWorkouts?.let { return it }

        val files = application.assets.list("workouts") ?: emptyArray()
        val workouts = files
            .filter { it.endsWith(".json") }
            .map { filename ->
                val json = application.assets.open("workouts/$filename")
                    .bufferedReader().use { it.readText() }
                parseWorkout(JSONObject(json))
            }
            .sortedBy { it.durationMinutes }
        cachedWorkouts = workouts
        return workouts
    }

    fun getWorkoutById(id: String): Workout? = getWorkouts().find { it.id == id }

    private fun parseWorkout(obj: JSONObject): Workout {
        val segArray = obj.getJSONArray("segments")
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
            id = obj.getString("id"),
            name = obj.getString("name"),
            description = obj.getString("description"),
            durationMinutes = obj.getInt("durationMinutes"),
            type = obj.getString("type"),
            category = obj.optString("category", "General"),
            coach = obj.getString("coach"),
            optionalMedia = obj.getBoolean("optionalMedia"),
            color = obj.optString("color", "#22D3EE"),
            segments = segments,
        )
    }
}
