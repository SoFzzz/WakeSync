package com.wakesync.core

import com.wakesync.core.service.WakeLockManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests validating WakeLockManager domain constants and power management safeguards.
 */
class WakeLockManagerTest {

    @Test
    fun wakeLockTimeout_isAnchoredToDomainWorstCase() {
        // Domain breakdown:
        // - 25 min max pre-rest monitoring timeout before deep rest
        // - 15 min nap countdown
        // - 5 min buffer for progressive alert escalation and user dismissal
        // Total = 45 minutes = 2,700,000 ms
        val expectedTimeoutMs = (25 + 15 + 5) * 60 * 1000L
        assertEquals(expectedTimeoutMs, WakeLockManager.MAX_HARDWARE_WAKE_LOCK_TIMEOUT_MS)
        assertEquals(2_700_000L, WakeLockManager.MAX_HARDWARE_WAKE_LOCK_TIMEOUT_MS)
    }
}
