package io.freewheel.launcher.ride

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaRideHandoffTest {

    private val now = 1_800_000_000_000L
    private val maxAge = MediaRideHandoff.SUMMARY_MAX_AGE_MS

    @Before
    fun reset() = MediaRideHandoff.resetForTest()

    private fun deliver(
        timestamp: Long = now - 5_000,
        token: Long = 42L,
        lastShown: Long = 0L,
        launcherToken: Long = 0L,
    ) = MediaRideHandoff.shouldDeliverSummary(timestamp, token, now, lastShown, launcherToken)

    // --- Summary freshness ---

    @Test
    fun freshSummary_isDelivered() = assertTrue(deliver())

    @Test
    fun missingSummary_isNotDelivered() = assertFalse(deliver(timestamp = 0L))

    @Test
    fun summaryAtMaxAge_isDelivered() = assertTrue(deliver(timestamp = now - maxAge))

    @Test
    fun staleSummary_isDropped() = assertFalse(deliver(timestamp = now - maxAge - 1))

    @Test
    fun slightlyFutureSummary_clockStepBack_isDelivered() = assertTrue(deliver(timestamp = now + 60_000))

    @Test
    fun farFutureSummary_isDropped() = assertFalse(deliver(timestamp = now + maxAge + 1))

    @Test
    fun alreadyShownSummary_isDropped() {
        assertFalse(deliver(token = 7L, lastShown = 7L))
        assertTrue(deliver(token = 8L, lastShown = 7L))
    }

    @Test
    fun summaryOlderThanLastShown_clockMovedBack_isDelivered() =
        // lastShown is a ride token, so a new ride whose token (wall clock) is
        // below the previous one is still shown.
        assertTrue(deliver(token = now - 3_600_000, lastShown = now))

    @Test
    fun legacySummaryWithoutToken_isNotMatchedToLastShown() =
        assertTrue(deliver(token = 0L, lastShown = 0L))

    @Test
    fun summaryForRideLauncherAlreadySummarized_isDropped() {
        assertFalse(deliver(token = 7L, launcherToken = 7L))
        assertTrue(deliver(token = 7L, launcherToken = 8L))
    }

    @Test
    fun legacySummaryWithoutToken_isNotMatchedToLauncherRide() =
        assertTrue(deliver(token = 0L, launcherToken = 0L))

    // --- Ride ownership ---

    @Test
    fun overlayOwnsRide_whileRunning_andAfterEndingIt() {
        assertFalse(MediaRideHandoff.overlayOwns(5L))
        MediaRideHandoff.overlayStarted(5L)
        assertTrue(MediaRideHandoff.overlayOwns(5L))
        MediaRideHandoff.markEndedByOverlay(5L)
        MediaRideHandoff.overlayReleased(5L)
        assertTrue(MediaRideHandoff.overlayOwns(5L))
        assertFalse(MediaRideHandoff.overlayOwns(6L))
    }

    @Test
    fun overlayReleasedWithoutEnding_handsRideBackToLauncher() {
        // e.g. overlay window couldn't be added, or launcher stopped the service
        MediaRideHandoff.overlayStarted(5L)
        MediaRideHandoff.overlayReleased(5L)
        assertFalse(MediaRideHandoff.overlayOwns(5L))
    }

    @Test
    fun releasingOldRide_doesNotReleaseNewerOne() {
        MediaRideHandoff.overlayStarted(5L)
        MediaRideHandoff.overlayStarted(9L)
        MediaRideHandoff.overlayReleased(5L)
        assertTrue(MediaRideHandoff.overlayOwns(9L))
    }

    @Test
    fun zeroToken_isNeverOwnedOrLauncherEnded() {
        MediaRideHandoff.overlayStarted(0L)
        MediaRideHandoff.markEndedByLauncher(0L)
        assertFalse(MediaRideHandoff.overlayOwns(0L))
        assertFalse(MediaRideHandoff.isEndedByLauncher(0L))
    }

    @Test
    fun endedByLauncher_isPerRide() {
        MediaRideHandoff.markEndedByLauncher(3L)
        assertTrue(MediaRideHandoff.isEndedByLauncher(3L))
        assertFalse(MediaRideHandoff.isEndedByLauncher(4L))
    }
}
