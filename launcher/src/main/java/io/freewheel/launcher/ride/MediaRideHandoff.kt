package io.freewheel.launcher.ride

import android.content.Context
import io.freewheel.launcher.data.RideSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates the two sessions that make up a media ride: the launcher's
 * [RideSessionManager] (drives WorkoutActiveScreen) and RideOverlayService
 * (stats bar + END button over the media app). Both live in the launcher
 * process and are linked only through the bridge's workoutActive flag, so
 * without this each side would log the ride and the overlay's summary would
 * never reach the launcher.
 *
 * Every media ride gets a token (its launch time). Whichever side ends the
 * ride records it here; the other side then skips logging/summarizing it.
 * The overlay's summary is also persisted to SharedPreferences so it survives
 * the launcher process being killed while the media app is in front.
 *
 * All methods are called on the main thread.
 */
object MediaRideHandoff {

    private const val PREFS = "ride_overlay"
    private const val KEY_TIMESTAMP = "summary_timestamp"
    private const val KEY_TOKEN = "summary_ride_token"
    private const val KEY_LAST_SHOWN = "summary_last_shown"
    private val SUMMARY_KEYS = listOf(
        "summary_duration", "summary_calories", "summary_avg_power", "summary_max_power",
        "summary_avg_rpm", "summary_avg_resistance", "summary_avg_hr", "summary_avg_speed",
        "summary_distance", "summary_workout_name", KEY_TIMESTAMP, KEY_TOKEN,
    )

    /** Overlay summaries older than this are dropped instead of shown. */
    const val SUMMARY_MAX_AGE_MS = 10 * 60 * 1000L

    private var overlayActiveToken = 0L
    private var endedByOverlayToken = 0L
    private var endedByLauncherToken = 0L

    // Bumped whenever the overlay publishes a summary, so the launcher can pick
    // it up immediately if it is already running/resumed.
    private val _summaryPublished = MutableStateFlow(0L)
    val summaryPublished: StateFlow<Long> = _summaryPublished.asStateFlow()

    // --- Ride ownership ---

    fun overlayStarted(token: Long) {
        overlayActiveToken = token
    }

    fun overlayReleased(token: Long) {
        if (overlayActiveToken == token) overlayActiveToken = 0L
    }

    fun markEndedByOverlay(token: Long) {
        endedByOverlayToken = token
    }

    fun markEndedByLauncher(token: Long) {
        endedByLauncherToken = token
    }

    fun isEndedByLauncher(token: Long): Boolean = token != 0L && endedByLauncherToken == token

    /**
     * True if the overlay is (still) responsible for logging and summarizing
     * this ride: either it is running it, or it already ended it. Used by the
     * launcher when the bridge ends the workout underneath it.
     */
    fun overlayOwns(token: Long): Boolean =
        token != 0L && (overlayActiveToken == token || endedByOverlayToken == token)

    // --- Summary handoff ---

    fun publishSummary(context: Context, token: Long, summary: RideSummary, now: Long = System.currentTimeMillis()) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("summary_duration", summary.durationSeconds)
                .putInt("summary_calories", summary.calories)
                .putInt("summary_avg_power", summary.avgPower)
                .putInt("summary_max_power", summary.maxPower)
                .putInt("summary_avg_rpm", summary.avgRpm)
                .putInt("summary_avg_resistance", summary.avgResistance)
                .putInt("summary_avg_hr", summary.avgHeartRate)
                .putFloat("summary_avg_speed", summary.avgSpeedMph)
                .putFloat("summary_distance", summary.distanceMiles)
                .putString("summary_workout_name", summary.workoutName)
                .putLong(KEY_TOKEN, token)
                .putLong(KEY_TIMESTAMP, now)
                .apply()
        } catch (_: Exception) {}
        _summaryPublished.value = _summaryPublished.value + 1
    }

    /**
     * Returns the pending overlay summary if it should be shown, and clears it
     * either way so a summary is never shown twice or long after the ride.
     */
    fun consumeSummary(context: Context, now: Long = System.currentTimeMillis()): RideSummary? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
            if (timestamp == 0L) return null
            val deliver = shouldDeliverSummary(
                summaryTimestamp = timestamp,
                summaryToken = prefs.getLong(KEY_TOKEN, 0L),
                now = now,
                lastShownTimestamp = prefs.getLong(KEY_LAST_SHOWN, 0L),
                launcherSummarizedToken = endedByLauncherToken,
            )
            val summary = if (deliver) RideSummary(
                durationSeconds = prefs.getInt("summary_duration", 0),
                calories = prefs.getInt("summary_calories", 0),
                avgPower = prefs.getInt("summary_avg_power", 0),
                maxPower = prefs.getInt("summary_max_power", 0),
                avgRpm = prefs.getInt("summary_avg_rpm", 0),
                avgResistance = prefs.getInt("summary_avg_resistance", 0),
                avgHeartRate = prefs.getInt("summary_avg_hr", 0),
                avgSpeedMph = prefs.getFloat("summary_avg_speed", 0f),
                distanceMiles = prefs.getFloat("summary_distance", 0f),
                workoutName = prefs.getString("summary_workout_name", null),
            ) else null
            val edit = prefs.edit()
            SUMMARY_KEYS.forEach { edit.remove(it) }
            if (deliver) edit.putLong(KEY_LAST_SHOWN, timestamp)
            edit.apply()
            summary
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pure decision for [consumeSummary]: show the overlay summary only if it is
     * recent, newer than the last one shown, and the launcher has not already
     * shown its own summary for the same ride. A clock that moved backwards by
     * less than the max age still counts as recent.
     */
    fun shouldDeliverSummary(
        summaryTimestamp: Long,
        summaryToken: Long,
        now: Long,
        lastShownTimestamp: Long,
        launcherSummarizedToken: Long,
    ): Boolean {
        if (summaryTimestamp <= 0L) return false
        if (summaryTimestamp <= lastShownTimestamp) return false
        val age = now - summaryTimestamp
        if (age > SUMMARY_MAX_AGE_MS || age < -SUMMARY_MAX_AGE_MS) return false
        if (summaryToken != 0L && summaryToken == launcherSummarizedToken) return false
        return true
    }

    /** Test hook: forget all in-memory ride ownership. */
    internal fun resetForTest() {
        overlayActiveToken = 0L
        endedByOverlayToken = 0L
        endedByLauncherToken = 0L
        _summaryPublished.value = 0L
    }
}
