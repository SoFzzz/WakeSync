package com.wakesync.sensors

import com.wakesync.core.model.GeoPoint
import com.wakesync.core.session.SessionManager
import com.wakesync.sensors.mock.MockSensorEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorRepositoryTest {

    private lateinit var mockSensorEngine: MockSensorEngine
    private lateinit var fakeRealSource: SensorSource
    private lateinit var repository: SensorRepository

    @Before
    fun setUp() {
        mockSensorEngine = MockSensorEngine()
        fakeRealSource = object : SensorSource {
            override fun getHeartRate(): Flow<Int> = flowOf(85)
            override fun getMotionSvm(): Flow<Float> = flowOf(1.2f)
            override fun getLocation(): Flow<GeoPoint> = flowOf(GeoPoint(6.25, -75.56, "Real Sensor"))
        }
        repository = SensorRepository(fakeRealSource, mockSensorEngine)
    }

    @Test
    fun `toggling simulation switches sensor streams at runtime without restarting`() = runTest {
        // Initially not simulated -> emits from fakeRealSource
        assertFalse(repository.isSimulated.value)
        assertEquals(85, repository.getHeartRate().first())
        assertEquals(1.2f, repository.getMotionSvm().first(), 0.01f)
        assertEquals("Real Sensor", repository.getLocation().first().name)

        // Switch to simulated
        repository.setSimulated(true)
        assertTrue(repository.isSimulated.value)

        mockSensorEngine.emitDirect(hr = 60, svm = 0.05f, location = GeoPoint(6.20, -75.50, "Mock Sensor"))

        assertEquals(60, repository.getHeartRate().first())
        assertEquals(0.05f, repository.getMotionSvm().first(), 0.01f)
        assertEquals("Mock Sensor", repository.getLocation().first().name)

        // Switch back to real
        repository.setSimulated(false)
        assertFalse(repository.isSimulated.value)
        assertEquals(85, repository.getHeartRate().first())
    }

    @Test
    fun `F9 - after a simulated session ends and simulation mode is turned off, the next session uses the real source`() = runTest {
        // Mirrors AppSessionCoordinator.startSimulationModeRelay: sessionManager.state.isSimulated
        // is the single source of truth that drives sensorRepository, instead of an imperative
        // setSimulated(true) that was never paired with a setSimulated(false).
        SessionManager.resetInstanceForTesting()
        val sessionManager = SessionManager()
        val relayJob = launch {
            sessionManager.state.map { it.isSimulated }.distinctUntilChanged().collect { isSimulated ->
                repository.setSimulated(isSimulated)
            }
        }
        runCurrent()
        assertFalse(repository.isSimulated.value)
        assertEquals(85, repository.getHeartRate().first())

        // A simulated nap starts (mirrors startSimulateNap: sessionManager.setSimulationMode(true))
        sessionManager.setSimulationMode(true)
        runCurrent()
        assertTrue(repository.isSimulated.value)
        mockSensorEngine.emitDirect(hr = 52)
        assertEquals(52, repository.getHeartRate().first())

        // The session ends (endSession never touches isSimulated by itself — this is why F9
        // required the caller to explicitly turn simulation mode off, e.g. via the Settings
        // toggle) and the user turns simulation mode off before the next, real session.
        sessionManager.setSimulationMode(false)
        runCurrent()
        assertFalse(repository.isSimulated.value)
        assertEquals(85, repository.getHeartRate().first())

        relayJob.cancel()
    }
}
