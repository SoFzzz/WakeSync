package com.wakesync.sensors

import com.wakesync.ai.RestEstimatorEngine
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.RestState
import com.wakesync.sensors.mock.MockSensorEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class MockSensorEngineTest {

    @Test
    fun `simulate nap mode reaches parameters required for DEEP_REST`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val mockSensorEngine = MockSensorEngine(dispatcher = testDispatcher)

        try {
            mockSensorEngine.startNapSimulation(this, durationSeconds = 60, baseHr = 75)
            testScheduler.runCurrent()

            // Initial emission should be start parameters
            val initialHr = mockSensorEngine.getHeartRate().first()
            val initialSvm = mockSensorEngine.getMotionSvm().first()
            assertEquals(MockSensorEngine.NAP_START_HR, initialHr)
            assertEquals(MockSensorEngine.NAP_START_SVM, initialSvm, 0.01f)

            // Advance time to completion of nap descent
            testScheduler.advanceTimeBy(61_000L)
            testScheduler.runCurrent()

            val targetHr = mockSensorEngine.getHeartRate().first()
            val targetSvm = mockSensorEngine.getMotionSvm().first()
            assertEquals(MockSensorEngine.NAP_TARGET_HR, targetHr)
            assertEquals(MockSensorEngine.NAP_TARGET_SVM, targetSvm, 0.01f)

            // Verify with RestEstimatorEngine that these target values confirm DEEP_REST
            val restEngine = RestEstimatorEngine(mockSensorEngine, dispatcher = testDispatcher)
            val eval1 = restEngine.calculateScoreAndState(
                baseHr = 75,
                currentHr = targetHr,
                meanSvm = targetSvm,
                previousDeepRestCount = 0
            )
            assertTrue(eval1.score >= 0.60f)
            assertEquals(1, eval1.consecutiveDeepRestCount)

            val eval2 = restEngine.calculateScoreAndState(
                baseHr = 75,
                currentHr = targetHr,
                meanSvm = targetSvm,
                previousDeepRestCount = eval1.consecutiveDeepRestCount
            )
            assertTrue(eval2.score >= 0.60f)
            assertEquals(2, eval2.consecutiveDeepRestCount)
            assertEquals(RestState.DEEP_REST, eval2.state)
        } finally {
            // Stop background simulation to prevent UncompletedCoroutinesError
            mockSensorEngine.stopSimulation()
        }
    }

    @Test
    fun `simulate route mode approaches destination from 2000m to 400m`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val mockSensorEngine = MockSensorEngine(dispatcher = testDispatcher)
        val destination = GeoPoint(6.2518, -75.5684, "Campus UCC")

        try {
            mockSensorEngine.startRouteSimulation(
                scope = this,
                destination = destination,
                durationSeconds = 60
            )
            testScheduler.runCurrent()

            val startLocation = mockSensorEngine.getLocation().first()
            val startDistance = haversineDistanceMeters(startLocation, destination)
            assertEquals(2000.0, startDistance, 5.0)

            // Advance 30 seconds (halfway: ~1200m)
            testScheduler.advanceTimeBy(30_000L)
            testScheduler.runCurrent()
            val midLocation = mockSensorEngine.getLocation().first()
            val midDistance = haversineDistanceMeters(midLocation, destination)
            assertEquals(1200.0, midDistance, 25.0)

            // Advance remaining 30 seconds (target: ~400m)
            testScheduler.advanceTimeBy(31_000L)
            testScheduler.runCurrent()
            val targetLocation = mockSensorEngine.getLocation().first()
            val targetDistance = haversineDistanceMeters(targetLocation, destination)
            assertEquals(400.0, targetDistance, 5.0)
        } finally {
            // Stop background simulation to prevent UncompletedCoroutinesError
            mockSensorEngine.stopSimulation()
        }
    }

    private fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }
}
