package com.wakesync.ui.screens

import com.wakesync.core.model.RestState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Monitoring must show a neutral placeholder until its first real rest evaluation arrives. */
class NapScreenTest {

    @Test
    fun `leftover CALIBRATING state is treated as not evaluated yet`() {
        assertTrue(isAwaitingMonitoringEvaluation(RestState.CALIBRATING, 0.0f))
    }

    @Test
    fun `UNKNOWN state or missing score is treated as not evaluated yet`() {
        assertTrue(isAwaitingMonitoringEvaluation(RestState.UNKNOWN, 0.0f))
        assertTrue(isAwaitingMonitoringEvaluation(RestState.AWAKE, null))
    }

    @Test
    fun `genuinely evaluated zero score is displayed`() {
        assertFalse(isAwaitingMonitoringEvaluation(RestState.AWAKE, 0.0f))
    }

    @Test
    fun `evaluated rest states and sensor unavailable are not placeholders`() {
        assertFalse(isAwaitingMonitoringEvaluation(RestState.LIGHT_REST, 0.45f))
        assertFalse(isAwaitingMonitoringEvaluation(RestState.DEEP_REST, 0.72f))
        assertFalse(isAwaitingMonitoringEvaluation(RestState.SENSOR_UNAVAILABLE, 0.0f))
    }
}
