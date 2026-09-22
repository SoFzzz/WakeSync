package com.wakesync.core

import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests validating SessionManager mutual exclusion, conflict resolution, and lifecycle coordination.
 */
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        // Instantiate with Dispatchers.Unconfined for deterministic synchronous coroutine execution in JVM tests
        sessionManager = SessionManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @Test
    fun startSession_transitionsToRequestedMode() {
        val started = sessionManager.requestStartSession(SessionType.NAP)

        assertTrue(started)
        assertEquals(SessionType.NAP, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.CALIBRATING, sessionManager.state.value.napState.phase)
        assertNull(sessionManager.state.value.pendingConflict)
    }

    @Test
    fun startSession_withDestination_updatesConfirmedDestinationInState() {
        val destination = GeoPoint(6.2518, -75.5684, "Campus UCC")
        val started = sessionManager.requestStartSession(SessionType.TRANSIT, destination)

        assertTrue(started)
        assertEquals(SessionType.TRANSIT, sessionManager.state.value.sessionType)
        assertEquals(destination, sessionManager.state.value.confirmedDestination)
        assertEquals("Campus UCC", sessionManager.state.value.confirmedDestination?.name)
    }

    @Test
    fun mutualExclusion_startingSecondSessionTriggersConflictWithoutOverwriting() {
        // Start first session (NAP)
        sessionManager.requestStartSession(SessionType.NAP)

        // Attempt to start second session (TRANSIT)
        val started = sessionManager.requestStartSession(SessionType.TRANSIT)

        assertFalse("Second session must not start without explicit user resolution", started)
        assertEquals("Active session must remain NAP", SessionType.NAP, sessionManager.state.value.sessionType)

        val conflict = sessionManager.state.value.pendingConflict
        assertNotNull("Conflict state must be present", conflict)
        assertEquals(SessionType.NAP, conflict?.runningSession)
        assertEquals(SessionType.TRANSIT, conflict?.requestedSession)
    }

    @Test
    fun resolveConflict_whenDeclined_keepsCurrentSessionAndClearsConflict() {
        sessionManager.requestStartSession(SessionType.NAP)
        sessionManager.requestStartSession(SessionType.TRANSIT)

        sessionManager.resolveConflict(proceedWithNew = false)

        assertNull("Conflict must be cleared", sessionManager.state.value.pendingConflict)
        assertEquals("Active session must remain NAP", SessionType.NAP, sessionManager.state.value.sessionType)
    }

    @Test
    fun resolveConflict_whenAccepted_cancelsRunningAndStartsRequestedSession() {
        sessionManager.requestStartSession(SessionType.NAP)
        sessionManager.requestStartSession(SessionType.TRANSIT)

        sessionManager.resolveConflict(proceedWithNew = true)

        assertNull("Conflict must be cleared", sessionManager.state.value.pendingConflict)
        assertEquals("Active session must now be TRANSIT", SessionType.TRANSIT, sessionManager.state.value.sessionType)
        assertEquals(TransitPhase.TRACKING, sessionManager.state.value.transitState.phase)
    }

    @Test
    fun endSession_resetsActiveModeAndUpdatesPhase() {
        sessionManager.requestStartSession(SessionType.NAP)
        sessionManager.endSession(SessionOutcome.COMPLETED)

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)
    }

    @Test
    fun startSession_idempotentWhenSameTypeAlreadyRunning() {
        sessionManager.requestStartSession(SessionType.NAP)
        sessionManager.updateNapProgress(NapPhase.MONITORING, 60, 1200, 300)

        // Request starting the same session type again (e.g. from redundant intent or service onStartCommand)
        val secondStart = sessionManager.requestStartSession(SessionType.NAP)

        assertTrue("Redundant start request must return true", secondStart)
        assertEquals(SessionType.NAP, sessionManager.state.value.sessionType)
        assertEquals("Phase must not be reset to CALIBRATING", NapPhase.MONITORING, sessionManager.state.value.napState.phase)
        assertEquals("Elapsed seconds must not be reset", 60, sessionManager.state.value.napState.elapsedSeconds)
    }

    private class TestAlertController : AlertControllerContract {
        val flow = MutableStateFlow(AlertLevel.NONE)
        override val activeAlertLevel: StateFlow<AlertLevel> = flow
        var cancelCalled = false

        override fun triggerAlert(level: AlertLevel) {
            flow.value = level
        }

        override fun cancelAlert() {
            cancelCalled = true
            flow.value = AlertLevel.NONE
        }
    }

    @Test
    fun registerAlertController_updatesWakeSyncStateOnEmission() {
        val mockController = TestAlertController()
        sessionManager.registerAlertController(mockController)

        assertEquals(AlertLevel.NONE, sessionManager.state.value.activeAlertLevel)

        mockController.triggerAlert(AlertLevel.SOFT)
        assertEquals(AlertLevel.SOFT, sessionManager.state.value.activeAlertLevel)

        mockController.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, sessionManager.state.value.activeAlertLevel)
    }

    @Test
    fun registerAlertController_isIdempotentAndCancelsPriorJob() {
        val mockController1 = TestAlertController()
        val mockController2 = TestAlertController()

        sessionManager.registerAlertController(mockController1)
        mockController1.triggerAlert(AlertLevel.MODERATE)
        assertEquals(AlertLevel.MODERATE, sessionManager.state.value.activeAlertLevel)

        // Registering a second controller cancels previous collection
        sessionManager.registerAlertController(mockController2)
        mockController2.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, sessionManager.state.value.activeAlertLevel)

        // Emissions from replaced controller must not overwrite active state
        mockController1.triggerAlert(AlertLevel.SOFT)
        assertEquals(AlertLevel.URGENT, sessionManager.state.value.activeAlertLevel)
    }

    @Test
    fun cancelAlert_delegatesToControllerAndUpdatesStateReactively() {
        val mockController = TestAlertController()
        sessionManager.registerAlertController(mockController)

        mockController.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, sessionManager.state.value.activeAlertLevel)

        sessionManager.cancelAlert()
        assertTrue("Controller's cancelAlert must be invoked", mockController.cancelCalled)
        assertEquals(AlertLevel.NONE, sessionManager.state.value.activeAlertLevel)
    }

    @Test
    fun endSession_cancelsAlertController() {
        val mockController = TestAlertController()
        sessionManager.registerAlertController(mockController)
        sessionManager.requestStartSession(SessionType.NAP)

        mockController.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, sessionManager.state.value.activeAlertLevel)

        sessionManager.endSession(SessionOutcome.COMPLETED)
        assertTrue("Ending session must cancel active alerts", mockController.cancelCalled)
        assertEquals(AlertLevel.NONE, sessionManager.state.value.activeAlertLevel)
    }
}
