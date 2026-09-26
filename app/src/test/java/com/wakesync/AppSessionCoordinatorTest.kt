package com.wakesync

import com.wakesync.ai.RestEvaluationResult
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.WakeSyncState
import com.wakesync.core.session.SessionManager
import com.wakesync.sleep.NapManager
import com.wakesync.transit.TransitManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    // Start-of-nap notice: before the engine's first evaluation the published result is the
    // initial placeholder (isInitial=true), which must not flash "sensor unavailable".

    private fun notEvaluatedYetResult() = RestEvaluationResult(
        score = 0.0f,
        state = RestState.AWAKE,
        consecutiveDeepRestCount = 0,
        isDataValid = false,
        isInitial = true
    )

    @Test
    fun `no evaluation yet during Nap CALIBRATING shows CALIBRATING`() {
        val restState = AppSessionCoordinator.resolveRestState(
            result = notEvaluatedYetResult(),
            sessionType = SessionType.NAP,
            napPhase = NapPhase.CALIBRATING
        )

        assertEquals(RestState.CALIBRATING, restState)
    }

    @Test
    fun `no evaluation yet with Nap in MONITORING is SENSOR_UNAVAILABLE`() {
        val restState = AppSessionCoordinator.resolveRestState(
            result = notEvaluatedYetResult(),
            sessionType = SessionType.NAP,
            napPhase = NapPhase.MONITORING
        )

        assertEquals(RestState.SENSOR_UNAVAILABLE, restState)
    }

    @Test
    fun `no evaluation yet during Transit is SENSOR_UNAVAILABLE`() {
        val restState = AppSessionCoordinator.resolveRestState(
            result = notEvaluatedYetResult(),
            sessionType = SessionType.TRANSIT,
            napPhase = NapPhase.IDLE
        )

        assertEquals(RestState.SENSOR_UNAVAILABLE, restState)
    }

    @Test
    fun `once evaluated, a down sensor during Nap CALIBRATING still shows SENSOR_UNAVAILABLE`() {
        val evaluatedSensorDown = RestEvaluationResult(
            score = 0.0f,
            state = RestState.AWAKE,
            consecutiveDeepRestCount = 0,
            isDataValid = false,
            isCalibrating = false,
            isInitial = false
        )

        val restState = AppSessionCoordinator.resolveRestState(
            result = evaluatedSensorDown,
            sessionType = SessionType.NAP,
            napPhase = NapPhase.CALIBRATING
        )

        assertEquals(RestState.SENSOR_UNAVAILABLE, restState)
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

    /** Records the lifecycle calls made by [AppSessionCoordinator.reconcileSessionLifecycle]. */
    private class RecordingLifecycle : AppSessionCoordinator.SessionLifecycle {
        val calls = mutableListOf<String>()
        override fun startNap(state: WakeSyncState) { calls += "startNap" }
        override fun startTransit(state: WakeSyncState) {
            calls += "startTransit:${state.transitState.destination?.name}"
        }
        override fun cancelTransitWithoutDestination() { calls += "cancelTransit" }
        override fun stopActiveSession() { calls += "stop" }
    }

    private val chosenDestination = GeoPoint(latitude = 6.2442, longitude = -75.5812, name = "Destino elegido")

    @Test
    fun `F17 nap to transit via resolveConflict stops the nap exactly once before starting transit`() = runTest {
        val sessionManager = SessionManager()
        val lifecycle = RecordingLifecycle()
        var active = SessionType.NONE
        // Same loop as AppSessionCoordinator.startSessionObserver, on the test dispatcher: the
        // collector only runs at runCurrent(), so it cannot observe the NONE that resolveConflict
        // passes through.
        val observer = launch {
            sessionManager.state.collectLatest {
                active = AppSessionCoordinator.reconcileSessionLifecycle(active, it, lifecycle)
            }
        }
        runCurrent()
        sessionManager.requestStartSession(SessionType.NAP)
        runCurrent()

        assertFalse(sessionManager.requestStartSession(SessionType.TRANSIT, chosenDestination))
        sessionManager.resolveConflict(proceedWithNew = true)
        runCurrent()
        observer.cancel()

        assertEquals(listOf("startNap", "stop", "startTransit:Destino elegido"), lifecycle.calls)
        assertEquals(1, lifecycle.calls.count { it == "stop" })
        assertEquals(SessionType.TRANSIT, active)
    }

    @Test
    fun `an observed NONE between sessions still stops the nap only once`() = runTest {
        val sessionManager = SessionManager()
        val lifecycle = RecordingLifecycle()
        var active = SessionType.NONE
        val observer = launch {
            sessionManager.state.collectLatest {
                active = AppSessionCoordinator.reconcileSessionLifecycle(active, it, lifecycle)
            }
        }
        runCurrent()
        sessionManager.requestStartSession(SessionType.NAP)
        runCurrent()
        sessionManager.endSession(SessionOutcome.CANCELLED)
        runCurrent()
        sessionManager.requestStartSession(SessionType.TRANSIT, chosenDestination)
        runCurrent()
        observer.cancel()

        assertEquals(listOf("startNap", "stop", "startTransit:Destino elegido"), lifecycle.calls)
    }

    /** Delegates to the REAL managers, exactly like AppSessionCoordinator.sessionLifecycle does. */
    private class RealManagersLifecycle(
        private val napManager: NapManager,
        private val transitManager: TransitManager
    ) : AppSessionCoordinator.SessionLifecycle {
        override fun startNap(state: WakeSyncState) = napManager.startSession(state.napState.destination)
        override fun startTransit(state: WakeSyncState) {
            state.transitState.destination?.let { transitManager.startSession(it) }
        }
        override fun cancelTransitWithoutDestination() = Unit
        override fun stopActiveSession() {
            napManager.stopSession()
            transitManager.stopSession()
        }
    }

    private fun realNapManager(sessionManager: SessionManager, scope: CoroutineScope) = NapManager(
        sessionManager = sessionManager,
        restEvaluationFlow = MutableSharedFlow<RestEvaluationResult>(),
        heartRateFlow = MutableSharedFlow<Int>(),
        locationFlow = MutableSharedFlow<GeoPoint>(),
        alertController = null,
        scope = scope
    )

    @Test
    fun `F17 with real managers the chosen Transit survives the nap teardown`() = runTest {
        var wakeLockReleases = 0
        val records = mutableListOf<SessionRecord>()
        val sessionManager = SessionManager(
            scope = backgroundScope,
            releaseWakeLock = { wakeLockReleases++ },
            persistRecord = { records += it }
        )
        val napManager = realNapManager(sessionManager, backgroundScope)
        val transitManager = TransitManager(
            sessionManager = sessionManager,
            locationFlow = MutableSharedFlow(),
            alertController = null,
            scope = backgroundScope
        )
        val lifecycle = RealManagersLifecycle(napManager, transitManager)
        var active = SessionType.NONE
        backgroundScope.launch {
            sessionManager.state.collectLatest {
                active = AppSessionCoordinator.reconcileSessionLifecycle(active, it, lifecycle)
            }
        }
        runCurrent()
        sessionManager.requestStartSession(SessionType.NAP)
        runCurrent()

        assertFalse(sessionManager.requestStartSession(SessionType.TRANSIT, chosenDestination))
        sessionManager.resolveConflict(proceedWithNew = true)
        val transitStart = sessionManager.activeSessionStartTimestamp(SessionType.TRANSIT)
        // The teardown (NapManager.stopSession / TransitManager.stopSession -> endSession) runs only
        // now, AFTER the Transit already started — the ordering seen on the device (thread 3955).
        runCurrent()

        assertEquals(SessionType.TRANSIT, sessionManager.state.value.sessionType)
        assertEquals(chosenDestination, sessionManager.state.value.transitState.destination)
        assertEquals(transitStart, sessionManager.activeSessionStartTimestamp(SessionType.TRANSIT))
        assertEquals("Wake lock released only by the nap's own end", 1, wakeLockReleases)
        runCurrent()
        assertEquals("No 0 s Transit record", listOf(SessionType.NAP), records.map { it.sessionType })
    }
}
