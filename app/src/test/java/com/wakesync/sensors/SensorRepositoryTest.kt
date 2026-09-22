package com.wakesync.sensors

import com.wakesync.core.model.GeoPoint
import com.wakesync.sensors.mock.MockSensorEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
}
