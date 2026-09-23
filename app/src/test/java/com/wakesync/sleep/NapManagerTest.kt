package com.wakesync.sleep

import com.wakesync.ai.RestEvaluationResult
import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.session.SessionManager
import com.wakesync.sensors.mock.MockSensorEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NapManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionManager: SessionManager
    private lateinit var mockAlertController: TestAlertController
    private lateinit var restEvaluationFlow: MutableSharedFlow<RestEvaluationResult>
    private lateinit var heartRateFlow: MutableSharedFlow<Int>
    private lateinit var locationFlow: MutableSharedFlow<GeoPoint>
    private lateinit var napManager: NapManager

    @Before
    fun setUp() {
        SessionManager.resetInstanceForTesting()
        sessionManager = SessionManager()
        mockAlertController = TestAlertController()
        sessionManager.registerAlertController(mockAlertController)

        restEvaluationFlow = MutableSharedFlow(replay = 1)
        heartRateFlow = MutableSharedFlow(replay = 1)
        locationFlow = MutableSharedFlow(replay = 1)

        napManager = NapManager(
            sessionManager = sessionManager,
            restEvaluationFlow = restEvaluationFlow,
            heartRateFlow = heartRateFlow,
            locationFlow = locationFlow,
            alertController = mockAlertController,
            scope = testScope
        )
    }

    @Test
    fun `calibration completes in 20s and applies defensive fallback when no readings captured`() = testScope.runTest {
        napManager.startSession()
        assertEquals(NapPhase.CALIBRATING, sessionManager.state.value.napState.phase)

        // Advance 20 seconds of calibration without emitting HR
        advanceTimeBy(21_000L)
        runCurrent()

        // Phase transits to MONITORING with 70 BPM fallback
        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)
        napManager.stopSession()
    }

    @Test
    fun `confirmation of 2 consecutive DEEP_REST starts 15-min countdown and triggers SOFT pre-warning`() = testScope.runTest {
        napManager.startSession()
        advanceTimeBy(21_000L) // Completes calibration
        runCurrent()
        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)

        // First DEEP_REST evaluation
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 1, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)

        // Second consecutive DEEP_REST evaluation -> Confirm rest!
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.70f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()

        assertEquals(NapPhase.REST_CONFIRMED, sessionManager.state.value.napState.phase)
        assertEquals(AlertLevel.SOFT, mockAlertController.lastTriggeredLevel)
        napManager.stopSession()
    }

    @Test
    fun `15-minute normal expiration triggers URGENT and waits for real dismissal before ending session`() = testScope.runTest {
        napManager.startSession()
        advanceTimeBy(21_000L) // Calibration
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(NapPhase.REST_CONFIRMED, sessionManager.state.value.napState.phase)

        // Advance 15 minutes (900s)
        advanceTimeBy(901_000L)
        runCurrent()

        assertEquals(AlertLevel.URGENT, mockAlertController.lastTriggeredLevel)
        assertEquals(AlertLevel.URGENT, mockAlertController.activeAlertLevel.value)

        // Dismissal from user
        mockAlertController.simulateUserDismissal()
        runCurrent()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `25-minute safety timeout triggers SOFT and ends session with TIMED_OUT`() = testScope.runTest {
        napManager.startSession()
        advanceTimeBy(21_000L) // Calibration
        runCurrent()
        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)

        // Advance past 25 minutes (1500s) without DEEP_REST
        advanceTimeBy(1500_000L)
        runCurrent()

        assertEquals(AlertLevel.SOFT, mockAlertController.lastTriggeredLevel)
        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.TIMED_OUT, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `proximity interrupt triggers URGENT alert even though it cancels the nap countdown job`() = testScope.runTest {
        val destination = GeoPoint(6.2518, -75.5684, "Campus UCC")
        napManager.startSession(destination)
        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(NapPhase.REST_CONFIRMED, sessionManager.state.value.napState.phase)

        // Location arrives exactly at destination (distance = 0 <= dynamicRadius)
        locationFlow.emit(destination)
        runCurrent()

        // URGENT alert MUST be triggered (Cierre 4 verified)
        assertEquals(AlertLevel.URGENT, mockAlertController.lastTriggeredLevel)

        mockAlertController.simulateUserDismissal()
        runCurrent()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `second GPS update within R_alert after interrupt does not re-trigger alert or create a second dismissalCollectorJob`() = testScope.runTest {
        val destination = GeoPoint(6.2518, -75.5684, "Campus UCC")
        napManager.startSession(destination)
        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()

        // First GPS fix triggering proximity arrival
        locationFlow.emit(destination)
        runCurrent()
        // 1 trigger for SOFT (rest confirmed pre-warning) + 1 for URGENT (proximity arrival) = 2
        assertEquals(2, mockAlertController.triggerCount)
        assertEquals(AlertLevel.URGENT, mockAlertController.lastTriggeredLevel)

        // Second GPS fix while alert is active before user dismissal
        locationFlow.emit(destination)
        runCurrent()

        // Trigger count must remain 2 (Cierre 5 guard verified: no duplicate alert or collector)
        assertEquals(2, mockAlertController.triggerCount)

        mockAlertController.simulateUserDismissal()
        runCurrent()
    }

    @Test
    fun `two consecutive nap sessions both terminate correctly and reset initial state`() = testScope.runTest {
        // --- SESSION 1 ---
        napManager.startSession()
        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        advanceTimeBy(901_000L) // Expiration
        runCurrent()
        mockAlertController.simulateUserDismissal()
        runCurrent()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)

        // Reset replay cache so Session 1's DEEP_REST emission does not leak into Session 2
        restEvaluationFlow.resetReplayCache()

        // --- SESSION 2 (Reusing same napManager instance) ---
        napManager.startSession()
        // Hygiene check: Phase is CALIBRATING and initial state is clean
        assertEquals(NapPhase.CALIBRATING, sessionManager.state.value.napState.phase)

        advanceTimeBy(21_000L)
        runCurrent()
        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)

        // Cancel session 2
        napManager.stopSession()
        runCurrent()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.CANCELLED, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `absolute 40-minute cap fires TIMED_OUT when nap countdown has not yet expired`() = testScope.runTest {
        napManager.startSession()
        advanceTimeBy(20_000L) // Calibration (20s)
        runCurrent()

        // Confirm DEEP_REST at minute 24:50 (1490s into monitoring, total 1510s from start)
        advanceTimeBy(1490_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(NapPhase.REST_CONFIRMED, sessionManager.state.value.napState.phase)

        // Advance time to reach 40 minutes from session start (2400s total)
        // 1511s + 890s = 2401s (past 2400s cap). Remaining nap seconds is ~10s > 0, so cap fires TIMED_OUT!
        advanceTimeBy(890_000L)
        runCurrent()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.TIMED_OUT, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `DEEP_REST confirmed at minute 24 completes at minute 39 without interference from 40-minute absolute cap`() = testScope.runTest {
        napManager.startSession()
        advanceTimeBy(20_000L)
        runCurrent()

        // Confirm at minute 24 (1440s from start)
        advanceTimeBy(1420_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(NapPhase.REST_CONFIRMED, sessionManager.state.value.napState.phase)

        // Advance 15 minutes of nap -> reaches minute 39 (2340s from start)
        advanceTimeBy(15 * 60 * 1000L + 1000L)
        runCurrent()

        // Normal expiration occurs at min 39
        assertEquals(AlertLevel.URGENT, mockAlertController.lastTriggeredLevel)
        mockAlertController.simulateUserDismissal()
        runCurrent()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)

        // Advance past minute 40 -> Cap does NOT fire because session already ended
        advanceTimeBy(2 * 60 * 1000L)
        runCurrent()
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `manual cancellation during REST_CONFIRMED cancels napJob and stops progress updates`() = testScope.runTest {
        napManager.startSession()
        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(NapPhase.REST_CONFIRMED, sessionManager.state.value.napState.phase)

        val remainingBeforeCancel = sessionManager.state.value.napState.remainingNapSeconds

        napManager.stopSession()
        runCurrent()

        assertEquals(NapPhase.CANCELLED, sessionManager.state.value.napState.phase)

        // Advance further time: remainingNapSeconds must NOT change
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(NapPhase.CANCELLED, sessionManager.state.value.napState.phase)
        assertEquals(remainingBeforeCancel, sessionManager.state.value.napState.remainingNapSeconds)
    }

    @Test
    fun `null alertController terminates session cleanly without NPE or blocking on both expiration and proximity interrupt`() = testScope.runTest {
        val noAlertSessionManager = SessionManager()
        val noAlertNapManager = NapManager(
            sessionManager = noAlertSessionManager,
            restEvaluationFlow = restEvaluationFlow,
            heartRateFlow = heartRateFlow,
            locationFlow = locationFlow,
            alertController = null,
            scope = testScope
        )

        // Case A: 15-minute expiration with null alertController
        noAlertNapManager.startSession()
        advanceTimeBy(21_000L) // Calibration
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        advanceTimeBy(901_000L) // Expiration
        runCurrent()

        assertEquals(SessionType.NONE, noAlertSessionManager.state.value.sessionType)
        assertEquals(NapPhase.COMPLETED, noAlertSessionManager.state.value.napState.phase)

        // Case B: Proximity interrupt with null alertController
        restEvaluationFlow.resetReplayCache()
        val destination = GeoPoint(6.2518, -75.5684, "Campus UCC")
        noAlertNapManager.startSession(destination)
        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()

        locationFlow.emit(destination)
        runCurrent()

        assertEquals(SessionType.NONE, noAlertSessionManager.state.value.sessionType)
        assertEquals(NapPhase.COMPLETED, noAlertSessionManager.state.value.napState.phase)
    }

    @Test
    fun `early cancellation of absoluteCapJob across all normal exit routes`() = testScope.runTest {
        // Route 1: 15-minute normal expiration cancels absoluteCapJob
        napManager.startSession()
        val capJob1 = napManager.absoluteCapJob
        assertNotNull(capJob1)
        assertTrue(capJob1!!.isActive)

        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()
        advanceTimeBy(901_000L)
        runCurrent()
        mockAlertController.simulateUserDismissal()
        runCurrent()

        assertTrue("Cap job must be cancelled after normal expiration", capJob1.isCancelled)
        assertNull("NapManager reference must be cleared", napManager.absoluteCapJob)
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)

        // Route 2: Manual stopSession() cancels absoluteCapJob
        restEvaluationFlow.resetReplayCache()
        napManager.startSession()
        val capJob2 = napManager.absoluteCapJob
        assertNotNull(capJob2)
        assertTrue(capJob2!!.isActive)

        napManager.stopSession()
        runCurrent()

        assertTrue("Cap job must be cancelled after stopSession()", capJob2.isCancelled)
        assertNull("NapManager reference must be cleared", napManager.absoluteCapJob)
        assertEquals(NapPhase.CANCELLED, sessionManager.state.value.napState.phase)

        // Route 3: Proximity interrupt cancels absoluteCapJob
        val destination = GeoPoint(6.2518, -75.5684, "Campus UCC")
        napManager.startSession(destination)
        val capJob3 = napManager.absoluteCapJob
        assertNotNull(capJob3)
        assertTrue(capJob3!!.isActive)

        advanceTimeBy(21_000L)
        runCurrent()
        restEvaluationFlow.emit(RestEvaluationResult(score = 0.65f, state = RestState.DEEP_REST, consecutiveDeepRestCount = 2, isDataValid = true))
        advanceTimeBy(1000L)
        runCurrent()

        locationFlow.emit(destination)
        runCurrent()
        mockAlertController.simulateUserDismissal()
        runCurrent()

        assertTrue("Cap job must be cancelled after proximity interrupt", capJob3.isCancelled)
        assertNull("NapManager reference must be cleared", napManager.absoluteCapJob)
        assertEquals(NapPhase.COMPLETED, sessionManager.state.value.napState.phase)
    }

    @Test
    fun `calibration hands the averaged HR_base to the rest estimator callback`() = testScope.runTest {
        val calibrated = mutableListOf<Int>()
        val wiredNapManager = NapManager(
            sessionManager = sessionManager,
            restEvaluationFlow = restEvaluationFlow,
            heartRateFlow = heartRateFlow,
            alertController = mockAlertController,
            scope = testScope,
            onBaseHeartRateCalibrated = { calibrated.add(it) }
        )

        wiredNapManager.startSession()
        heartRateFlow.emit(80)
        advanceTimeBy(5_000L)
        heartRateFlow.emit(90)
        advanceTimeBy(16_000L)
        runCurrent()

        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)
        assertEquals(listOf(85), calibrated)
        wiredNapManager.stopSession()
    }

    @Test
    fun `calibration without readings keeps the fallback away from the rest estimator callback`() = testScope.runTest {
        val calibrated = mutableListOf<Int>()
        val wiredNapManager = NapManager(
            sessionManager = sessionManager,
            restEvaluationFlow = restEvaluationFlow,
            alertController = mockAlertController,
            scope = testScope,
            onBaseHeartRateCalibrated = { calibrated.add(it) }
        )

        wiredNapManager.startSession()
        advanceTimeBy(21_000L)
        runCurrent()

        // Phase still advances with the internal 70 BPM fallback, but the estimator never receives it
        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)
        assertTrue("Callback must not be invoked without real HR readings", calibrated.isEmpty())
        wiredNapManager.stopSession()
    }

    @Test
    fun `F18 - simulated nap started during calibration hands the nominal baseHr to the callback`() = testScope.runTest {
        // Mirrors AppSessionCoordinator.startSimulateNap: the mock's ramp only begins once the
        // phase leaves CALIBRATING, so a real-time-shortened ramp (B2a) can never bleed into the
        // calibration window and drag the calibrated base HR below the nominal 75.
        val calibrated = mutableListOf<Int>()
        val mockSensorEngine = MockSensorEngine(dispatcher = testDispatcher)
        val wiredNapManager = NapManager(
            sessionManager = sessionManager,
            restEvaluationFlow = restEvaluationFlow,
            heartRateFlow = mockSensorEngine.getHeartRate(),
            locationFlow = locationFlow,
            alertController = mockAlertController,
            scope = testScope,
            onBaseHeartRateCalibrated = { calibrated.add(it) }
        )

        wiredNapManager.startSession()
        mockSensorEngine.startNapSimulation(
            testScope,
            durationSeconds = 10,
            baseHr = 75,
            awaitRampStart = { sessionManager.state.first { it.napState.phase != NapPhase.CALIBRATING } }
        )
        advanceTimeBy(21_000L)
        runCurrent()

        assertEquals(NapPhase.MONITORING, sessionManager.state.value.napState.phase)
        assertEquals(listOf(75), calibrated)

        wiredNapManager.stopSession()
        mockSensorEngine.stopSimulation()
    }

    class TestAlertController : AlertControllerContract {
        private val _activeAlertLevel = MutableStateFlow(AlertLevel.NONE)
        override val activeAlertLevel: StateFlow<AlertLevel> = _activeAlertLevel.asStateFlow()

        var lastTriggeredLevel: AlertLevel = AlertLevel.NONE
        var triggerCount: Int = 0
        var cancelAlertCalled: Boolean = false

        override fun triggerAlert(level: AlertLevel) {
            lastTriggeredLevel = level
            triggerCount++
            _activeAlertLevel.value = level
        }

        override fun cancelAlert() {
            cancelAlertCalled = true
            _activeAlertLevel.value = AlertLevel.NONE
        }

        fun simulateUserDismissal() {
            _activeAlertLevel.value = AlertLevel.NONE
        }
    }
}
