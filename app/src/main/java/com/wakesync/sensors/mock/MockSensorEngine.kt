package com.wakesync.sensors.mock

import com.wakesync.core.model.GeoPoint
import com.wakesync.sensors.SensorSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        // With the calibration-aware hold in startNapSimulation (F18/B2a), the calibrated base HR
        // is always ~75 (NAP_START_HR), so the steady-state score_reposo is a comfortable
        // 0.5*(75-52)/75 + 0.492 ≈ 0.65 — well above the 0.60 DEEP_REST threshold.
        const val NAP_TARGET_HR: Int = 52
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
     * - Holds HR at [baseHr] and SVM at resting quietude ([NAP_TARGET_SVM]) until
     *   [awaitRampStart] resolves, so a real-time-shortened ramp (B2a) can never bleed into
     *   NapManager's calibration window and pull the calibrated base HR down (F18).
     * - Once [awaitRampStart] resolves (immediately if the caller's condition already holds),
     *   interpolates HR: [baseHr] -> [NAP_TARGET_HR] over [durationSeconds].
     * Ensures RestEstimatorEngine sustains score >= 0.60 for >= 2 cycles to achieve DEEP_REST.
     */
    fun startNapSimulation(
        scope: CoroutineScope,
        durationSeconds: Int = 60,
        baseHr: Int = NAP_START_HR,
        awaitRampStart: suspend () -> Unit = {}
    ) {
        stopSimulation()
        activeSimulationJob = scope.launch(dispatcher) {
            val totalSteps = durationSeconds
            val hrDelta = (NAP_TARGET_HR - baseHr).toFloat()

            // Motion is already "resting" the moment the user lies down: constant at target
            // quietude for the whole simulation, independent of the HR hold/ramp below.
            val motionJob = launch {
                val motionIntervalMs = 50L
                while (isActive) {
                    svmFlow.emit(NAP_TARGET_SVM)
                    delay(motionIntervalMs)
                }
            }

            // Hold HR at baseHr while the caller's calibration window is still open.
            val holdJob = launch {
                while (isActive) {
                    hrFlow.emit(baseHr)
                    delay(1000L)
                }
            }
            awaitRampStart()
            holdJob.cancel()

            // 1 Hz heart rate ramp down to the target, starting from baseHr (same value the hold
            // just held, so there is no discontinuity at the handoff).
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
     *
     * @param destination The destination confirmed by the user (RF-SIM-02); there is no default.
     */
    fun startRouteSimulation(
        scope: CoroutineScope,
        destination: GeoPoint,
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
     * Stops the active simulation and clears the replay cache of every flow, so a later session
     * never receives a stale HR/SVM/location left over by a previous simulation (F26: a replayed
     * NAP_TARGET_HR became the calibrated HR_base; a replayed route point skewed Transit's speed).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stopSimulation() {
        activeSimulationJob?.cancel()
        activeSimulationJob = null
        hrFlow.resetReplayCache()
        svmFlow.resetReplayCache()
        locationFlow.resetReplayCache()
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
