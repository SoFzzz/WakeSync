package com.wakesync.transit

import android.os.SystemClock
import android.util.Log
import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.alerts.AlertControllerProvider
import com.wakesync.core.geo.GeofenceCalculator
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.session.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinator for TransitNudge operating mode (RF-TRAN-01 through RF-TRAN-06).
 *
 * Responsibilities:
 * - Continuously consumes injected [locationFlow] without importing com.wakesync.sensors.
 * - Computes straight-line Haversine distance and dynamic alert radius (R_alert).
 * - Estimates vehicle speed using Exponential Moving Average (EMA) and clamp [0, 35] m/s.
 * - Modulates arrival alerts according to RestState (RF-TRAN-05: DEEP_REST escalates to URGENT).
 * - Manages reactive two-phase dismissal for URGENT and decoupled one-shot for MODERATE.
 * - Provides immediate manual cancellation via [stopSession] (RF-TRAN-06).
 */
class TransitManager(
    private val sessionManager: SessionManager,
    private val locationFlow: Flow<GeoPoint>,
    private val alertController: AlertControllerContract? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    companion object {
        private const val TAG = "GeofenceTracker"
        private const val SPEED_EMA_ALPHA: Double = 0.3
        private const val MAX_SPEED_CLAMP_MPS: Double = 35.0 // ~126 km/h upper vehicular bound
        private const val MIN_GPS_TIME_DELTA_SEC: Double = 0.5
    }

    private var trackingJob: Job? = null
    private var dismissalCollectorJob: Job? = null
    private val isTerminating = AtomicBoolean(false)

    private var currentTransitPhase: TransitPhase = TransitPhase.IDLE
    private var smoothedSpeedMps: Double = 0.0
    private var lastLocation: GeoPoint? = null
    private var lastTimestampMs: Long = 0L

    private fun resolveAlertController(): AlertControllerContract? {
        val controller = alertController ?: AlertControllerProvider.get()
        if (controller == null) {
            Log.w(TAG, "AlertControllerContract is null (not yet registered in AlertControllerProvider)")
        }
        return controller
    }

    /**
     * Starts tracking progression toward the selected [destination].
     */
    fun startSession(destination: GeoPoint) {
        isTerminating.set(false)
        currentTransitPhase = TransitPhase.TRACKING
        smoothedSpeedMps = 0.0
        lastLocation = null
        lastTimestampMs = 0L

        sessionManager.requestStartSession(SessionType.TRANSIT, destination)

        trackingJob?.cancel()
        trackingJob = scope.launch {
            locationFlow.collect { location ->
                // Cierre 5: Guard against re-entry once phase transitioned to ALERTING or session is terminating
                if (currentTransitPhase == TransitPhase.ALERTING || isTerminating.get()) {
                    return@collect
                }

                val nowMs = timeProvider()
                updateSmoothedSpeed(location, nowMs)

                val distanceMeters = GeofenceCalculator.haversineMeters(location, destination)
                val currentRestState = sessionManager.state.value.biometricMetrics.restState
                val isDeepRest = (currentRestState == RestState.DEEP_REST)
                val dynamicRadiusMeters = GeofenceCalculator.calculateDynamicAlertRadius(smoothedSpeedMps, isDeepRest)

                sessionManager.updateTransitProgress(
                    TransitPhase.TRACKING,
                    distanceMeters.toFloat(),
                    dynamicRadiusMeters.toFloat(),
                    smoothedSpeedMps.toFloat()
                )

                if (distanceMeters <= dynamicRadiusMeters) {
                    currentTransitPhase = TransitPhase.ALERTING
                    handleArrival(currentRestState, distanceMeters, dynamicRadiusMeters)
                }
            }
        }
        Log.i(TAG, "Transit tracking session started towards ${destination.name ?: destination.latitude}")
    }

    private fun updateSmoothedSpeed(currentLocation: GeoPoint, currentTimestampMs: Long) {
        val prevLoc = lastLocation
        val prevTime = lastTimestampMs
        if (prevLoc != null && prevTime > 0L) {
            val deltaSeconds = (currentTimestampMs - prevTime) / 1000.0
            if (deltaSeconds >= MIN_GPS_TIME_DELTA_SEC) {
                val rawSpeed = GeofenceCalculator.estimateSpeedMps(prevLoc, prevTime, currentLocation, currentTimestampMs)
                val clampedSpeed = rawSpeed.coerceIn(0.0, MAX_SPEED_CLAMP_MPS)
                smoothedSpeedMps = (SPEED_EMA_ALPHA * clampedSpeed) + ((1.0 - SPEED_EMA_ALPHA) * smoothedSpeedMps)
                lastLocation = currentLocation
                lastTimestampMs = currentTimestampMs
            }
        } else {
            lastLocation = currentLocation
            lastTimestampMs = currentTimestampMs
        }
    }

    /**
     * Handles arrival at destination within dynamic R_alert radius.
     */
    private suspend fun handleArrival(restState: RestState, distanceMeters: Double, radiusMeters: Double) {
        val targetLevel = if (restState == RestState.DEEP_REST) AlertLevel.URGENT else AlertLevel.MODERATE
        sessionManager.updateTransitProgress(
            TransitPhase.ALERTING,
            distanceMeters.toFloat(),
            radiusMeters.toFloat(),
            smoothedSpeedMps.toFloat()
        )

        val controller = resolveAlertController()
        if (controller == null) {
            terminateSession(SessionOutcome.COMPLETED, stopActiveAlert = false)
            return
        }

        if (targetLevel == AlertLevel.URGENT) {
            // Cierre 1 & 4: Guaranteed subscription before trigger via CompletableDeferred + onSubscription
            val subscribed = CompletableDeferred<Unit>()
            dismissalCollectorJob = scope.launch {
                controller.activeAlertLevel
                    .onSubscription { subscribed.complete(Unit) }
                    .dropWhile { it != AlertLevel.URGENT }
                    .first { it == AlertLevel.NONE }
                terminateSession(SessionOutcome.COMPLETED, stopActiveAlert = false)
            }
            subscribed.await()
            controller.triggerAlert(AlertLevel.URGENT)
        } else {
            // Decoupled one-shot (Option B): trigger MODERATE and complete without stopping active vibration
            controller.triggerAlert(AlertLevel.MODERATE)
            terminateSession(SessionOutcome.COMPLETED, stopActiveAlert = false)
        }
    }

    /**
     * Cancels the active transit session manually (RF-TRAN-06 [Detener]).
     */
    fun stopSession() {
        dismissalCollectorJob?.cancel()
        dismissalCollectorJob = null
        trackingJob?.cancel()
        trackingJob = null
        resolveAlertController()?.cancelAlert()
        terminateSession(SessionOutcome.CANCELLED, stopActiveAlert = true)
    }

    private fun terminateSession(outcome: SessionOutcome, stopActiveAlert: Boolean) {
        if (isTerminating.compareAndSet(false, true)) {
            currentTransitPhase = if (outcome == SessionOutcome.CANCELLED) TransitPhase.CANCELLED else TransitPhase.COMPLETED
            dismissalCollectorJob?.cancel()
            dismissalCollectorJob = null
            trackingJob?.cancel()
            trackingJob = null
            sessionManager.endSession(outcome, stopActiveAlert = stopActiveAlert)
            Log.i(TAG, "Transit session terminated with outcome: $outcome (stopActiveAlert=$stopActiveAlert)")
        }
    }
}
