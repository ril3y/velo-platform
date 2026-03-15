package io.freewheel.freeride

import org.json.JSONObject

data class WorkoutSegment(
    val label: String,
    val durationSeconds: Int,
    val resistance: Int,
    val message: String = "",
)

data class WorkoutData(
    val id: String,
    val name: String,
    val description: String,
    val durationMinutes: Int,
    val type: String,
    val category: String = "General",
    val coach: String,
    val color: String = "#22D3EE",
    val segments: List<WorkoutSegment>,
) {
    companion object {
        fun fromJson(json: String): WorkoutData {
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
            return WorkoutData(
                id = obj.getString("id"),
                name = obj.getString("name"),
                description = obj.getString("description"),
                durationMinutes = obj.getInt("durationMinutes"),
                type = obj.getString("type"),
                category = obj.optString("category", "General"),
                coach = obj.getString("coach"),
                color = obj.optString("color", "#22D3EE"),
                segments = segments,
            )
        }
    }
}
