package com.wakesync

import com.wakesync.ai.RestEvaluationResult
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F24: unit tests for [AppSessionCoordinator.resolveRestState], the pure function that decides
 * whether a raw `RestEvaluationResult.isCalibrating` signal is actually shown as
 * `RestState.CALIBRATING`, gated on the caller's own session phase.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionCoordinatorTest {

    private fun invalidCalibratingResult() = RestEvaluationResult(
        score = 0.0f,
        state = RestState.AWAKE,
        consecutiveDeepRestCount = 0,
        isDataValid = false,
        isCalibrating = true
    )

    private fun invalidNonCalibratingResult() = RestEvaluationResult(
        score = 0.0f,
        state = RestState.AWAKE,
        consecutiveDeepRestCount = 0,
        isDataValid = false,
        isCalibrating = false
    )

    @Test
    fun `Transit session with fresh HR and no HR_base never shows CALIBRATING`() {
        // (a) F8: Transit never calibrates, so the engine has no basal HR the whole trip —
        // isCalibrating is true from the engine's point of view, but there's no Nap phase to gate it.
        val restState = AppSessionCoordinator.resolveRestState(
            result = invalidCalibratingResult(),
            sessionType = SessionType.TRANSIT,
            napPhase = NapPhase.IDLE
        )

        assertEquals(RestState.SENSOR_UNAVAILABLE, restState)
    }

    @Test
    fun `Nap already in MONITORING with no HR_base stays SENSOR_UNAVAILABLE`() {
        // (b) F18: calibration had 0 readings, onBaseHeartRateCalibrated was never called, and the
        // session has already moved on to MONITORING — must not show "Calibrando" retroactively.
        val restState = AppSessionCoordinator.resolveRestState(
            result = invalidCalibratingResult(),
            sessionType = SessionType.NAP,
            napPhase = NapPhase.MONITORING
        )

        assertEquals(RestState.SENSOR_UNAVAILABLE, restState)
    }

    @Test
    fun `Nap in CALIBRATING with fresh HR and no HR_base shows CALIBRATING`() {
        // (c) The actual F24 fix: fresh, in-range HR with no basal HR yet, while the Nap session
        // is genuinely in its CALIBRATING phase.
        val restState = AppSessionCoordinator.resolveRestState(
            result = invalidCalibratingResult(),
            sessionType = SessionType.NAP,
            napPhase = NapPhase.CALIBRATING
        )

        assertEquals(RestState.CALIBRATING, restState)
    }

    @Test
    fun `a genuinely unavailable sensor during Nap CALIBRATING is still SENSOR_UNAVAILABLE`() {
        // isCalibrating=false means the engine itself saw no fresh HR at all (off-wrist, etc.) —
        // being in the CALIBRATING phase must not override that.
        val restState = AppSessionCoordinator.resolveRestState(
            result = invalidNonCalibratingResult(),
            sessionType = SessionType.NAP,
            napPhase = NapPhase.CALIBRATING
        )

        assertEquals(RestState.SENSOR_UNAVAILABLE, restState)
    }

    @Test
    fun `valid data always uses the engine's own state regardless of session phase`() {
        val validResult = RestEvaluationResult(
            score = 0.7f,
            state = RestState.DEEP_REST,
            consecutiveDeepRestCount = 2,
            isDataValid = true,
            isCalibrating = false
        )

        val restState = AppSessionCoordinator.resolveRestState(
            result = validResult,
            sessionType = SessionType.NAP,
            napPhase = NapPhase.MONITORING
        )

        assertEquals(RestState.DEEP_REST, restState)
    }

    // F26: ⚡ ("start descent") gate — released only by ⚡ AND the end of calibration, any order.

    @Test
    fun `ramp gate stays closed when ⚡ is tapped but the nap is still CALIBRATING`() = runTest {
        val trigger = CompletableDeferred<Unit>()
        val phase = MutableStateFlow(NapPhase.CALIBRATING)
        val gate = async { AppSessionCoordinator.awaitNapRampGate(trigger, phase) }

        trigger.complete(Unit)
        runCurrent()

        assertFalse(gate.isCompleted)
        gate.cancel()
    }

    @Test
    fun `ramp gate stays closed in MONITORING until ⚡ is tapped`() = runTest {
        val trigger = CompletableDeferred<Unit>()
        val phase = MutableStateFlow(NapPhase.MONITORING)
        val gate = async { AppSessionCoordinator.awaitNapRampGate(trigger, phase) }
        runCurrent()

        assertFalse(gate.isCompleted)
        gate.cancel()
    }

    @Test
    fun `ramp gate opens with ⚡ before calibration ends`() = runTest {
        val trigger = CompletableDeferred<Unit>()
        val phase = MutableStateFlow(NapPhase.CALIBRATING)
        val gate = async { AppSessionCoordinator.awaitNapRampGate(trigger, phase) }

        trigger.complete(Unit)
        runCurrent()
        phase.value = NapPhase.MONITORING
        runCurrent()

        assertTrue(gate.isCompleted)
    }

    @Test
    fun `ramp gate opens with ⚡ after calibration ended`() = runTest {
        val trigger = CompletableDeferred<Unit>()
        val phase = MutableStateFlow(NapPhase.MONITORING)
        val gate = async { AppSessionCoordinator.awaitNapRampGate(trigger, phase) }
        runCurrent()

        trigger.complete(Unit)
        runCurrent()

        assertTrue(gate.isCompleted)
    }
}
