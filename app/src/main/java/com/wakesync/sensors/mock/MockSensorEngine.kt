package com.wakesync.sensors.mock

import com.wakesync.core.model.GeoPoint
import com.wakesync.sensors.SensorSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Deterministic and reproducible mock sensor engine for Wear OS emulator testing.
 * Implements [SensorSource] for [Simulate Nap] and [Simulate Route] modes.
 */
class MockSensorEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SensorSource {

    companion object {
        // Nap simulation parameters
        const val NAP_START_HR: Int = 75
        const val NAP_TARGET_HR: Int = 58
        const val NAP_START_SVM: Float = 1.8f
        const val NAP_TARGET_SVM: Float = 0.04f

        // Route simulation parameters
        const val ROUTE_START_DISTANCE_METERS: Double = 2000.0
        const val ROUTE_TARGET_DISTANCE_METERS: Double = 400.0
        const val ROUTE_DURATION_SECONDS: Int = 60
        // Derived from Earth radius 6,371,000m (Haversine spec): 6,371,000 * PI / 180 ≈ 111,194.9266 m/deg
        private const val METERS_PER_LATITUDE_DEGREE: Double = 111_194.9266
    }

    private val hrFlow = MutableSharedFlow<Int>(replay = 1)
    private val svmFlow = MutableSharedFlow<Float>(replay = 1)
    private val locationFlow = MutableSharedFlow<GeoPoint>(replay = 1)

    private var activeSimulationJob: Job? = null

    override fun getHeartRate(): Flow<Int> = hrFlow.asSharedFlow()
    override fun getMotionSvm(): Flow<Float> = svmFlow.asSharedFlow()
    override fun getLocation(): Flow<GeoPoint> = locationFlow.asSharedFlow()

    /**
     * Starts deterministic [Simulate Nap] mode:
     * - Interpolates HR: 75 -> 58 BPM.
     * - Interpolates SVM: 1.8 -> 0.04 m/s².
     * Ensures RestEstimatorEngine sustains score >= 0.60 for >= 2 cycles to achieve DEEP_REST.
     */
    fun startNapSimulation(
        scope: CoroutineScope,
        durationSeconds: Int = 60,
        baseHr: Int = NAP_START_HR
    ) {
        stopSimulation()
        activeSimulationJob = scope.launch(dispatcher) {
            val totalSteps = durationSeconds
            val hrDelta = (NAP_TARGET_HR - baseHr).toFloat()
            val svmDelta = (NAP_TARGET_SVM - NAP_START_SVM)

            // High frequency motion generation (20 Hz -> 50ms interval)
            val motionJob = launch {
                var step = 0
                val motionIntervalMs = 50L
                val totalMotionSteps = durationSeconds * 20
                while (isActive && step <= totalMotionSteps) {
                    val progress = (step.toFloat() / totalMotionSteps).coerceIn(0.0f, 1.0f)
                    val currentSvm = NAP_START_SVM + (svmDelta * progress)
                    svmFlow.emit(currentSvm)
                    delay(motionIntervalMs)
                    step++
                }
                // Once finished, maintain quietude
                while (isActive) {
                    svmFlow.emit(NAP_TARGET_SVM)
                    delay(motionIntervalMs)
                }
            }

            // 1 Hz heart rate emission
            var second = 0
            while (isActive && second <= totalSteps) {
                val progress = (second.toFloat() / totalSteps).coerceIn(0.0f, 1.0f)
                val currentHr = (baseHr + (hrDelta * progress)).roundToInt()
                hrFlow.emit(currentHr)
                delay(1000L)
                second++
            }

            // Maintain target deep rest HR
            while (isActive) {
                hrFlow.emit(NAP_TARGET_HR)
                delay(1000L)
            }
            motionJob.cancel()
        }
    }

    /**
     * Starts deterministic [Simulate Route] mode:
     * - Interpolates GPS distance from 2000m to 400m from destination over 60 seconds.
     * - Emits GPS updates at 1 Hz, crossing the dynamic alert radius (R_alert).
     */
    fun startRouteSimulation(
        scope: CoroutineScope,
        destination: GeoPoint = GeoPoint(6.2518, -75.5684, "Campus UCC"),
        durationSeconds: Int = ROUTE_DURATION_SECONDS
    ) {
        stopSimulation()
        activeSimulationJob = scope.launch(dispatcher) {
            val totalSteps = durationSeconds
            val distanceDelta = ROUTE_TARGET_DISTANCE_METERS - ROUTE_START_DISTANCE_METERS

            var second = 0
            while (isActive && second <= totalSteps) {
                val progress = (second.toDouble() / totalSteps).coerceIn(0.0, 1.0)
                val currentDistanceMeters = ROUTE_START_DISTANCE_METERS + (distanceDelta * progress)

                // Compute coordinate exactly currentDistanceMeters North of destination
                val latOffset = currentDistanceMeters / METERS_PER_LATITUDE_DEGREE
                val interpolatedPoint = GeoPoint(
                    latitude = destination.latitude + latOffset,
                    longitude = destination.longitude,
                    name = "Simulated Approach ($currentDistanceMeters m)"
                )

                locationFlow.emit(interpolatedPoint)
                delay(1000L)
                second++
            }

            // Hold at target proximity
            while (isActive) {
                val latOffset = ROUTE_TARGET_DISTANCE_METERS / METERS_PER_LATITUDE_DEGREE
                locationFlow.emit(
                    GeoPoint(
                        latitude = destination.latitude + latOffset,
                        longitude = destination.longitude,
                        name = "Simulated Proximity (400 m)"
                    )
                )
                delay(1000L)
            }
        }
    }

    /**
     * Stops active simulation and resets flows.
     */
    fun stopSimulation() {
        activeSimulationJob?.cancel()
        activeSimulationJob = null
    }

    /**
     * Directly emits values (useful for synchronous unit test assertions).
     */
    suspend fun emitDirect(hr: Int? = null, svm: Float? = null, location: GeoPoint? = null) {
        hr?.let { hrFlow.emit(it) }
        svm?.let { svmFlow.emit(it) }
        location?.let { locationFlow.emit(it) }
    }
}
