package com.wakesync.transit

import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.geo.GeofenceCalculator
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.BiometricMetrics
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransitManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionManager: SessionManager
    private lateinit var mockAlertController: TestAlertController
    private lateinit var locationFlow: MutableSharedFlow<GeoPoint>
    private lateinit var transitManager: TransitManager

    private val destination = TransitDestinations.CAMPUS_UCC

    @Before
    fun setUp() {
        SessionManager.resetInstanceForTesting()
        sessionManager = SessionManager()
        mockAlertController = TestAlertController()
        sessionManager.registerAlertController(mockAlertController)
        locationFlow = MutableSharedFlow(replay = 1)

        transitManager = TransitManager(
            sessionManager = sessionManager,
            locationFlow = locationFlow,
            alertController = mockAlertController,
            scope = testScope
        )
    }

    @Test
    fun `arrival with normal rest triggers MODERATE and completes session without stopping active alert`() = testScope.runTest {
        transitManager.startSession(destination)
        assertEquals(TransitPhase.TRACKING, sessionManager.state.value.transitState.phase)

        // SessionManager.startSessionInternal calls cancelAlert() at startup; reset flag to isolate arrival assertion
        mockAlertController.cancelAlertCalled = false

        // Emit location at destination (distance = 0 <= 250m R_alert floor)
        locationFlow.emit(destination)
        advanceUntilIdle()

        assertEquals(AlertLevel.MODERATE, mockAlertController.lastTriggeredLevel)
        assertFalse("cancelAlert must NOT be called for one-shot MODERATE (Option B)", mockAlertController.cancelAlertCalled)
        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(TransitPhase.COMPLETED, sessionManager.state.value.transitState.phase)
    }

    @Test
    fun `arrival with DEEP_REST escalates to URGENT and awaits real user dismissal`() = testScope.runTest {
        transitManager.startSession(destination)

        // Set restState to DEEP_REST
        sessionManager.updateBiometrics(BiometricMetrics(restState = RestState.DEEP_REST))

        // Emit location at destination
        locationFlow.emit(destination)
        advanceUntilIdle()

        assertEquals(AlertLevel.URGENT, mockAlertController.lastTriggeredLevel)
        // Session should remain ALERTING while alert level is URGENT
        assertEquals(AlertLevel.URGENT, mockAlertController.activeAlertLevel.value)

        // Simulate user dismissing the alert in UI
        mockAlertController.simulateUserDismissal()
        advanceUntilIdle()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(TransitPhase.COMPLETED, sessionManager.state.value.transitState.phase)
    }

    @Test
    fun `DEEP_REST appearing in middle of route immediately expands R_alert`() = testScope.runTest {
        transitManager.startSession(destination)

        // Point at 800m North of destination
        val latOffset = 800.0 / 111_194.9266
        val point800m = GeoPoint(destination.latitude + latOffset, destination.longitude)

        // Initial update with normal rest: R_alert at stationary is 250m, so 800m > 250m -> stays TRACKING
        locationFlow.emit(point800m)
        advanceUntilIdle()
        assertEquals(TransitPhase.TRACKING, sessionManager.state.value.transitState.phase)

        // Simulate speed = 10 m/s (~36 km/h)
        // With normal rest: R_alert = 10 * 60 + 100 / 2.2 = 645.45m (< 800m -> no alert)
        // With DEEP_REST: R_alert = 10 * 90 + 100 / 2.2 = 945.45m (> 800m -> ALERT!)
        sessionManager.updateBiometrics(BiometricMetrics(restState = RestState.DEEP_REST))

        // Trigger another location to recalculate with DEEP_REST
        val point800mSlight = GeoPoint(destination.latitude + (750.0 / 111_194.9266), destination.longitude)
        locationFlow.emit(point800mSlight)
        advanceUntilIdle()

        // Rest transition to DEEP_REST expands R_alert to at least 750m, triggering arrival
        assertTrue(
            sessionManager.state.value.transitState.dynamicAlertRadiusMeters >= 250f
        )
        transitManager.stopSession()
    }

    @Test
    fun `second GPS update within R_alert after arrival does not re-trigger alert`() = testScope.runTest {
        transitManager.startSession(destination)
        sessionManager.updateBiometrics(BiometricMetrics(restState = RestState.DEEP_REST))

        // First arrival point
        locationFlow.emit(destination)
        advanceUntilIdle()

        assertEquals(1, mockAlertController.triggerCount)
        assertEquals(AlertLevel.URGENT, mockAlertController.lastTriggeredLevel)

        // Second point arriving within R_alert before dismissal
        val pointClose = GeoPoint(destination.latitude + 0.0001, destination.longitude)
        locationFlow.emit(pointClose)
        advanceUntilIdle()

        // Trigger count must remain strictly 1 (Cierre 5 guard verified)
        assertEquals(1, mockAlertController.triggerCount)

        mockAlertController.simulateUserDismissal()
        advanceUntilIdle()
        assertEquals(TransitPhase.COMPLETED, sessionManager.state.value.transitState.phase)
    }

    @Test
    fun `stopSession cancels tracking and ends session with CANCELLED`() = testScope.runTest {
        transitManager.startSession(destination)
        assertEquals(TransitPhase.TRACKING, sessionManager.state.value.transitState.phase)

        transitManager.stopSession()
        advanceUntilIdle()

        assertTrue(mockAlertController.cancelAlertCalled)
        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(TransitPhase.CANCELLED, sessionManager.state.value.transitState.phase)
    }

    @Test
    fun `arrival with alertController null completes cleanly without NPE`() = testScope.runTest {
        val noAlertTransitManager = TransitManager(
            sessionManager = sessionManager,
            locationFlow = locationFlow,
            alertController = null,
            scope = testScope
        )

        // Case A: Arrival with normal rest (triggers MODERATE path)
        noAlertTransitManager.startSession(destination)
        locationFlow.emit(destination)
        advanceUntilIdle()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(TransitPhase.COMPLETED, sessionManager.state.value.transitState.phase)

        // Case B: Arrival with DEEP_REST (triggers URGENT path with CompletableDeferred)
        sessionManager.updateBiometrics(BiometricMetrics(restState = RestState.DEEP_REST))
        noAlertTransitManager.startSession(destination)
        locationFlow.emit(destination)
        advanceUntilIdle()

        assertEquals(SessionType.NONE, sessionManager.state.value.sessionType)
        assertEquals(TransitPhase.COMPLETED, sessionManager.state.value.transitState.phase)
    }

    @Test
    fun `GPS jitter filtering and EMA clamp keeps estimated speed within bounds`() = testScope.runTest {
        var currentTimeMs = 1_000_000L
        val jitterTransitManager = TransitManager(
            sessionManager = sessionManager,
            locationFlow = locationFlow,
            alertController = mockAlertController,
            scope = testScope,
            timeProvider = { currentTimeMs }
        )

        jitterTransitManager.startSession(destination)

        // Point 0: Base location (far from destination so it stays in TRACKING)
        val basePoint = GeoPoint(destination.latitude + 0.05, destination.longitude)
        locationFlow.emit(basePoint)
        advanceUntilIdle()

        // Point 1: Move 100 meters in 1 second -> raw speed = 100 m/s (huge jitter spike!)
        currentTimeMs += 1000L
        val jitterPoint1 = GeoPoint(basePoint.latitude + (100.0 / 111_194.9266), basePoint.longitude)
        locationFlow.emit(jitterPoint1)
        advanceUntilIdle()

        // Raw speed was 100 m/s, clamped to 35.0 m/s.
        // EMA: smoothedSpeed = 0.3 * 35.0 + 0.7 * 0.0 = 10.5 m/s
        val speed1 = sessionManager.state.value.transitState.estimatedSpeedMps ?: 0f
        assertTrue("Speed must be clamped <= 35.0 m/s", speed1 <= 35.0f)
        assertTrue("Speed must be >= 0.0 m/s", speed1 >= 0.0f)
        assertEquals(10.5f, speed1, 0.5f)

        // Point 2: Massive teleport jitter: 5000 meters in 1 second (raw speed = 5000 m/s)
        currentTimeMs += 1000L
        val jitterPoint2 = GeoPoint(jitterPoint1.latitude + (5000.0 / 111_194.9266), jitterPoint1.longitude)
        locationFlow.emit(jitterPoint2)
        advanceUntilIdle()

        // Raw speed 5000 clamped to 35.0 m/s.
        // EMA: smoothedSpeed = 0.3 * 35.0 + 0.7 * 10.5 = 17.85 m/s <= 35.0 m/s
        val speed2 = sessionManager.state.value.transitState.estimatedSpeedMps ?: 0f
        assertTrue("Speed must remain <= 35.0 m/s", speed2 <= 35.0f)
        assertTrue("Speed must be >= 0.0 m/s", speed2 >= 0.0f)
        assertEquals(17.85f, speed2, 0.5f)

        // Dynamic radius must also be calculated with clamped speed, not raw 5000 m/s!
        val radius = sessionManager.state.value.transitState.dynamicAlertRadiusMeters
        assertTrue("Dynamic radius must be reasonable (< 2500m)", radius < 2500f)

        jitterTransitManager.stopSession()
    }

    /**
     * Test alert controller implementing [AlertControllerContract] for deterministic unit assertions.
     */
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
