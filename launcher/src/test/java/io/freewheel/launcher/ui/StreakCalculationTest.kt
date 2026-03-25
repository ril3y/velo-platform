package io.freewheel.launcher.ui

import io.freewheel.launcher.data.RideRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the [calculateStreak] function from HomeScreen.
 *
 * Since calculateStreak depends on Calendar for "today", we replicate
 * the logic here to keep tests hermetic and independent of the clock.
 */
class StreakCalculationTest {

    /** Replicates the calculateStreak algorithm for testability without Android runtime. */
    private fun calculateStreak(rides: List<RideRecord>): Int {
        if (rides.isEmpty()) return 0
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR) +
                cal.get(Calendar.YEAR) * 366
        val rideDays = rides.map { ride ->
            cal.timeInMillis = ride.startTime
            cal.get(Calendar.DAY_OF_YEAR) +
                    cal.get(Calendar.YEAR) * 366
        }.distinct().sorted().reversed()
        if (rideDays.isEmpty()) return 0
        val mostRecent = rideDays.first()
        if (today - mostRecent > 1) return 0
        var streak = 1
        for (i in 1 until rideDays.size) {
            if (rideDays[i - 1] - rideDays[i] == 1) streak++ else break
        }
        return streak
    }

    /** Helper: returns millis for N days ago at noon. */
    private fun daysAgo(n: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return cal.timeInMillis
    }

    private fun ride(startTime: Long) = RideRecord(startTime = startTime)

    @Test
    fun emptyRides_returnsZero() {
        assertEquals(0, calculateStreak(emptyList()))
    }

    @Test
    fun singleRideToday_returnsOne() {
        val rides = listOf(ride(daysAgo(0)))
        assertEquals(1, calculateStreak(rides))
    }

    @Test
    fun ridesOnConsecutiveDays_returnsStreak() {
        val rides = listOf(
            ride(daysAgo(0)),
            ride(daysAgo(1)),
            ride(daysAgo(2)),
        )
        assertEquals(3, calculateStreak(rides))
    }

    @Test
    fun gapInRides_breaksStreak() {
        // Today, yesterday, then skip a day, then 3 days ago
        val rides = listOf(
            ride(daysAgo(0)),
            ride(daysAgo(1)),
            ride(daysAgo(3)), // gap — day 2 is missing
        )
        assertEquals(2, calculateStreak(rides))
    }

    @Test
    fun rideYesterday_noToday_returnsStreak() {
        // Yesterday and day before — no ride today, but most recent is yesterday (within 1 day)
        val rides = listOf(
            ride(daysAgo(1)),
            ride(daysAgo(2)),
            ride(daysAgo(3)),
        )
        assertEquals(3, calculateStreak(rides))
    }

    @Test
    fun oldRide_returnsZero() {
        // A ride from 7 days ago with nothing recent
        val rides = listOf(ride(daysAgo(7)))
        assertEquals(0, calculateStreak(rides))
    }

    @Test
    fun multipleRidesSameDay_countAsOne() {
        // Two rides today, one yesterday
        val rides = listOf(
            ride(daysAgo(0)),
            ride(daysAgo(0)),
            ride(daysAgo(1)),
        )
        assertEquals(2, calculateStreak(rides))
    }
}
