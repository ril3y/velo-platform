package io.freewheel.launcher.data

import org.json.JSONArray
import org.json.JSONObject

data class WorkoutSegment(
    val label: String,
    val durationSeconds: Int,
    val resistance: Int,
    val message: String = "",
)

data class Workout(
    val id: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val type: String,
    val category: String = "General",
    val coach: String,
    val optionalMedia: Boolean,
    val color: String = "#22D3EE",
    val segments: List<WorkoutSegment>,
) {
    companion object {
        fun fromJson(json: String): Workout {
            val obj = JSONObject(json)
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
                optionalMedia = obj.optBoolean("optionalMedia", false),
                color = obj.optString("color", "#22D3EE"),
                segments = segments,
            )
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("description", description)
        obj.put("durationMinutes", durationMinutes)
        obj.put("type", type)
        obj.put("category", category)
        obj.put("coach", coach)
        obj.put("color", color)
        val segArray = JSONArray()
        for (seg in segments) {
            val segObj = JSONObject()
            segObj.put("label", seg.label)
            segObj.put("durationSeconds", seg.durationSeconds)
            segObj.put("resistance", seg.resistance)
            segObj.put("message", seg.message)
            segArray.put(segObj)
        }
        obj.put("segments", segArray)
        return obj.toString()
    }
}
