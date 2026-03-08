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
