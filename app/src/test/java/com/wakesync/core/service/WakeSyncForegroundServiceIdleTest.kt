package com.wakesync.core.service

import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.WakeSyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the self-stop predicate of [WakeSyncForegroundService]: the service may only stop once
 * no session is active and no alert is still playing.
 */
class WakeSyncForegroundServiceIdleTest {

    @Test
    fun isIdle_trueWhenNoSessionAndNoAlert() {
        assertTrue(WakeSyncForegroundService.isIdle(WakeSyncState(), AlertLevel.NONE))
        assertTrue(WakeSyncForegroundService.isIdle(WakeSyncState(), null))
    }

    @Test
    fun isIdle_falseWhileSessionIsActive() {
        assertFalse(WakeSyncForegroundService.isIdle(WakeSyncState(sessionType = SessionType.NAP), null))
        assertFalse(WakeSyncForegroundService.isIdle(WakeSyncState(sessionType = SessionType.TRANSIT), null))
    }

    @Test
    fun isIdle_falseWhileTrailingAlertPlaysAfterSessionEnd() {
        // Transit arrival: session already NONE, MODERATE still vibrating (state mirror may lag).
        assertFalse(WakeSyncForegroundService.isIdle(WakeSyncState(), AlertLevel.MODERATE))
        // Nap alarm: URGENT mirrored into state, waiting for the user's dismissal.
        val urgent = WakeSyncState(activeAlertLevel = AlertLevel.URGENT)
        assertFalse(WakeSyncForegroundService.isIdle(urgent, AlertLevel.URGENT))
        assertFalse(WakeSyncForegroundService.isIdle(urgent, null))
    }
}
