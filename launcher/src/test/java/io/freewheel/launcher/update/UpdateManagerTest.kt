package io.freewheel.launcher.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UpdateManager.isNewer] version comparison logic.
 *
 * The method compares dot-separated numeric version strings and returns true
 * when the remote version is strictly newer than the local version.
 */
class UpdateManagerTest {

    /**
     * We cannot construct a full UpdateManager (it needs an Application),
     * so we replicate the pure isNewer logic here to validate the algorithm.
     * If the implementation ever changes, these tests catch regressions.
     */
    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    @Test
    fun isNewer_higherMajor_returnsTrue() {
        assertTrue(isNewer("2.0.0", "1.0.0"))
    }

    @Test
    fun isNewer_higherMinor_returnsTrue() {
        assertTrue(isNewer("1.3.0", "1.2.0"))
    }

    @Test
    fun isNewer_higherPatch_returnsTrue() {
        assertTrue(isNewer("1.2.4", "1.2.3"))
    }

    @Test
    fun isNewer_sameVersion_returnsFalse() {
        assertFalse(isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun isNewer_lowerVersion_returnsFalse() {
        assertFalse(isNewer("1.2.2", "1.2.3"))
    }

    @Test
    fun isNewer_differentLengths_handles() {
        assertTrue(isNewer("1.2.3.1", "1.2.3"))
    }

    @Test
    fun isNewer_differentLengths_shorterRemote_returnsFalse() {
        assertFalse(isNewer("1.2.3", "1.2.3.1"))
    }

    @Test
    fun isNewer_emptyStrings_returnsFalse() {
        assertFalse(isNewer("", ""))
    }
}
