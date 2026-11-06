package com.wakesync.sleep

import android.util.Log
import com.wakesync.ai.RestEvaluationResult
import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.alerts.AlertControllerProvider
import com.wakesync.core.geo.GeofenceCalculator
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.session.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinator for Nap (MicroNap) operating mode (RF-NAP-01 through RF-NAP-06).
 *
 * Lifecycle:
 * 1. Basal calibration (20s, extended up to 60s while real PPG has not produced data) -> computes baseline HR.
 * 2. Continuous monitoring -> evaluates RestEvaluationResult until DEEP_REST is confirmed (2x >= 0.60).
 * 3. 15-minute countdown -> decrements remainingNapSeconds once DEEP_REST is confirmed.
 * 4. Safety timeout (25 min) -> fires SOFT alert if DEEP_REST is not reached within 25 min.
 * 5. Proximity interrupt (RF-NAP-04) -> absolute priority: arrival within dynamic R_alert interrupts countdown.
 * 6. Absolute session cap (40 min) -> strict safety ceiling from session start.
 */
class NapManager(
    private val sessionManager: SessionManager,
    private val restEvaluationFlow: Flow<RestEvaluationResult>,
    private val heartRateFlow: Flow<Int>? = null,
    private val locationFlow: Flow<GeoPoint>? = null,
    private val alertController: AlertControllerContract? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    /** Receives HR_base once calibration ends so the rest estimator can compute ΔHR_relativa. */
    private val onBaseHeartRateCalibrated: (Int) -> Unit = {}
) {

    companion object {
        private const val TAG = "NapManager"

        const val CALIBRATION_DURATION_SECONDS: Int = 20

        /** Real PPG can take longer than 20 s for its first reading; wait for data up to this cap. */
        const val CALIBRATION_MAX_WAIT_SECONDS: Int = 60

        /** Minimum span of real HR data (from the first valid reading) before HR_base is computed. */
        const val CALIBRATION_MIN_DATA_SECONDS: Int = 10

        /** Valid readings averaged for a late HR_base (calibration cap reached with no data). */
        const val LATE_BASELINE_SAMPLE_COUNT: Int = 5
        const val NAP_COUNTDOWN_SECONDS: Int = 15 * 60 // 15 minutes (900s)
        const val SAFETY_TIMEOUT_SECONDS: Int = 25 * 60 // 25 minutes (1500s)
        const val ABSOLUTE_SESSION_CAP_MS: Long = 40 * 60 * 1000L // 40 minutes

        // DEFENSIVE_ENGINEERING_FALLBACK: Unspecified default (70 BPM) used only if 20s calibration produces 0 valid readings
        const val DEFENSIVE_FALLBACK_HR_BPM: Int = 70
        const val HR_MIN_VALID: Int = 35
        const val HR_MAX_VALID: Int = 220
    }

    private var napJob: Job? = null
    private var locationTrackingJob: Job? = null
    internal var absoluteCapJob: Job? = null
    private var dismissalCollectorJob: Job? = null
    private var lateBaselineJob: Job? = null
    private val isTerminating = AtomicBoolean(false)

    /**
     * F17: start timestamp of the Nap session this manager started, captured right after
     * requestStartSession. Passed to SessionManager.endSession as expectedStartTimestamp so a late
     * terminateSession (e.g. stopSession() from the coordinator's teardown thread) can never end a
     * session started afterwards, such as the Transit started by resolveConflict.
     */
    @Volatile
    private var ownedSessionStartTimestamp: Long? = null

    private var currentNapPhase: NapPhase = NapPhase.IDLE
    private var baseHeartRate: Int? = null
    private var restLatencySeconds: Int? = null
    private var elapsedSeconds: Int = 0
    private var remainingNapSeconds: Int = 0

    /**
     * Last distance to the nap destination computed by the location tracking job. Re-published on
     * every progress tick because SessionManager.updateNapProgress overwrites the distance field.
     */
    @Volatile
    private var lastDistanceMeters: Float? = null

    /**
     * Publishes the current nap progress for [phase], always carrying [lastDistanceMeters] so the
     * per-second ticks never wipe the destination distance shown on screen.
     */
    private fun publishProgress(phase: NapPhase) {
        sessionManager.updateNapProgress(
            phase,
            elapsedSeconds,
            remainingNapSeconds,
            restLatencySeconds,
            lastDistanceMeters
        )
    }

    private fun resolveAlertController(): AlertControllerContract? {
        val controller = alertController ?: AlertControllerProvider.get()
        if (controller == null) {
            Log.w(TAG, "AlertControllerContract is null (not yet registered in AlertControllerProvider)")
        }
        return controller
    }

    /**
     * Starts a nap session with an optional [destination] for geographical proximity interruption.
     */
    fun startSession(destination: GeoPoint? = null) {
        // Bug 1 & Extra Menor: Reset state hygiene for consecutive session execution
        isTerminating.set(false)
        currentNapPhase = NapPhase.CALIBRATING
        baseHeartRate = null
        restLatencySeconds = null
        elapsedSeconds = 0
        remainingNapSeconds = NAP_COUNTDOWN_SECONDS
        lastDistanceMeters = null

        sessionManager.requestStartSession(SessionType.NAP, destination)
        ownedSessionStartTimestamp = sessionManager.activeSessionStartTimestamp(SessionType.NAP)

        // Cierre 3: absoluteCapJob starts strictly in startSession() covering 40 min total from real start
        absoluteCapJob?.cancel()
        absoluteCapJob = scope.launch {
            delay(ABSOLUTE_SESSION_CAP_MS)
            handle40MinAbsoluteCap(remainingNapSeconds)
        }

        // Cierre 4: Optional proximity tracking in a segregated job to prevent self-cancellation
        if (destination != null && locationFlow != null) {
            locationTrackingJob?.cancel()
            locationTrackingJob = scope.launch {
                locationFlow.collect { location ->
                    // Cierre 5: Guard against re-entry once phase is ALERTING or session is terminating
                    if (currentNapPhase == NapPhase.ALERTING || isTerminating.get()) {
                        return@collect
                    }

                    val distance = GeofenceCalculator.haversineMeters(location, destination)
                    val isDeepRest = (currentNapPhase == NapPhase.REST_CONFIRMED)
                    val dynamicRadius = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = 0.0, isDeepRest = isDeepRest)

                    lastDistanceMeters = distance.toFloat()
                    publishProgress(currentNapPhase)

                    if (distance <= dynamicRadius) {
                        currentNapPhase = NapPhase.ALERTING
                        handleProximityInterrupt()
                    }
                }
            }
        }

        napJob?.cancel()
        napJob = scope.launch {
            runNapLifecycle()
        }

        Log.i(TAG, "Nap session started (hasDestination=${destination != null})")
    }

    private suspend fun CoroutineScope.runNapLifecycle() {
        // --- PHASE 1: Calibrating (20s) ---
        publishProgress(NapPhase.CALIBRATING)
        val calibrationSamples = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val firstSampleSecond = java.util.concurrent.atomic.AtomicInteger(-1)
        var calSeconds = 0

        val hrCollectorJob = heartRateFlow?.let { flow ->
            launch {
                flow.collect { bpm ->
                    if (bpm in HR_MIN_VALID..HR_MAX_VALID) {
                        firstSampleSecond.compareAndSet(-1, calSeconds)
                        calibrationSamples.add(bpm)
                    }
                }
            }
        }

        // Wait at least CALIBRATION_DURATION_SECONDS, and until CALIBRATION_MIN_DATA_SECONDS of real
        // data exist, but never longer than CALIBRATION_MAX_WAIT_SECONDS (slow PPG warm-up, F18).
        while (!isCalibrationComplete(calSeconds, firstSampleSecond.get()) && isActive && !isTerminating.get()) {
            delay(1000L)
            calSeconds++
            elapsedSeconds++
            publishProgress(NapPhase.CALIBRATING)
        }
        hrCollectorJob?.cancel()

        if (isTerminating.get()) return

        val calibratedHr = if (calibrationSamples.isNotEmpty()) {
            calibrationSamples.average().toInt()
        } else {
            Log.w(TAG, "Calibration produced 0 valid HR readings; applying defensive fallback: $DEFENSIVE_FALLBACK_HR_BPM BPM")
            startLateBaselineCollector()
            DEFENSIVE_FALLBACK_HR_BPM
        }
        baseHeartRate = calibratedHr
        if (calibrationSamples.isNotEmpty()) {
            onBaseHeartRateCalibrated(calibratedHr)
        } else {
            // The invented fallback must not feed the estimator: without real HR_base it reports
            // invalid data (SENSOR_UNAVAILABLE) instead of scoring against a made-up baseline
            Log.w(TAG, "HR_base not sent to the rest estimator: calibration had no valid readings")
        }

        // --- PHASE 2: Monitoring ---
        currentNapPhase = NapPhase.MONITORING
        publishProgress(NapPhase.MONITORING)

        var deepRestConfirmed = false

        val evalCollectorJob = launch {
            restEvaluationFlow.collect { eval ->
                if (currentNapPhase == NapPhase.MONITORING && eval.consecutiveDeepRestCount >= 2) {
                    deepRestConfirmed = true
                }
            }
        }

        var monitoringSeconds = 0
        while (!deepRestConfirmed && isActive && !isTerminating.get()) {
            delay(1000L)
            monitoringSeconds++
            elapsedSeconds++
            publishProgress(NapPhase.MONITORING)

            // 25-minute safety timeout check (RF-NAP-05: 25 minutes of monitoring)
            if (monitoringSeconds >= SAFETY_TIMEOUT_SECONDS) {
                evalCollectorJob.cancel()
                currentNapPhase = NapPhase.ALERTING
                publishProgress(NapPhase.ALERTING)
                handle25MinTimeout()
                return
            }
        }
        evalCollectorJob.cancel()

        if (isTerminating.get()) return

        // --- PHASE 3: Rest Confirmed (15-min countdown) ---
        currentNapPhase = NapPhase.REST_CONFIRMED
        restLatencySeconds = elapsedSeconds
        publishProgress(NapPhase.REST_CONFIRMED)
        resolveAlertController()?.triggerAlert(AlertLevel.SOFT) // Level 1 soft confirmation pre-warning

        while (remainingNapSeconds > 0 && isActive && !isTerminating.get()) {
            delay(1000L)
            elapsedSeconds++
            remainingNapSeconds--
            publishProgress(NapPhase.REST_CONFIRMED)
        }

        if (remainingNapSeconds <= 0 && !isTerminating.get()) {
            currentNapPhase = NapPhase.ALERTING
            publishProgress(NapPhase.ALERTING)
            handle15MinExpiration()
        }
    }

    /**
     * Pure completion rule for the calibration window: [calSeconds] elapsed, [firstSampleSecond] is
     * the second of the first valid reading (-1 if none yet).
     */
    internal fun isCalibrationComplete(calSeconds: Int, firstSampleSecond: Int): Boolean {
        if (calSeconds >= CALIBRATION_MAX_WAIT_SECONDS) return true
        if (calSeconds < CALIBRATION_DURATION_SECONDS || firstSampleSecond < 0) return false
        return calSeconds - firstSampleSecond >= CALIBRATION_MIN_DATA_SECONDS
    }

    /**
     * Calibration ended with no data (PPG never produced a reading): keep listening during
     * MONITORING and, once [LATE_BASELINE_SAMPLE_COUNT] valid readings arrive, deliver the late
     * HR_base so the estimator leaves SENSOR_UNAVAILABLE instead of staying blind all session.
     */
    private fun startLateBaselineCollector() {
        val flow = heartRateFlow ?: return
        lateBaselineJob?.cancel()
        lateBaselineJob = scope.launch {
            val late = mutableListOf<Int>()
            flow.collect { bpm ->
                if (bpm in HR_MIN_VALID..HR_MAX_VALID) late.add(bpm)
                if (late.size >= LATE_BASELINE_SAMPLE_COUNT) {
                    val base = late.average().toInt()
                    baseHeartRate = base
                    onBaseHeartRateCalibrated(base)
                    Log.i(TAG, "Late HR_base delivered after the calibration window")
                    cancel()
                }
            }
        }
    }

    /**
     * Dispatches Level 3 URGENT alert upon normal 15-minute nap expiration.
     */
    private suspend fun handle15MinExpiration() {
        val controller = resolveAlertController()
        if (controller == null) {
            terminateSession(SessionOutcome.COMPLETED, stopActiveAlert = false)
            return
        }

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
    }

    /**
     * Interrupted by geographic destination arrival (RF-NAP-04: absolute priority).
     */
    private suspend fun handleProximityInterrupt() {
        // Cierre 4: Cancel nap countdown from outside locationTrackingJob - NO self-cancellation
        napJob?.cancel()
        napJob = null

        val controller = resolveAlertController()
        if (controller == null) {
            terminateSession(SessionOutcome.INTERRUPTED_BY_ARRIVAL, stopActiveAlert = false)
            return
        }

        val subscribed = CompletableDeferred<Unit>()
        dismissalCollectorJob = scope.launch {
            controller.activeAlertLevel
                .onSubscription { subscribed.complete(Unit) }
                .dropWhile { it != AlertLevel.URGENT }
                .first { it == AlertLevel.NONE }
            terminateSession(SessionOutcome.INTERRUPTED_BY_ARRIVAL, stopActiveAlert = false)
        }
        subscribed.await()
        controller.triggerAlert(AlertLevel.URGENT)
    }

    /**
     * 25-minute safety timeout reached without achieving DEEP_REST.
     */
    private fun handle25MinTimeout() {
        resolveAlertController()?.triggerAlert(AlertLevel.SOFT)
        terminateSession(SessionOutcome.TIMED_OUT, stopActiveAlert = false)
    }

    /**
     * 40-minute absolute session cap reached.
     */
    private fun handle40MinAbsoluteCap(remainingSeconds: Int) {
        val outcome = if (remainingSeconds <= 0) SessionOutcome.COMPLETED else SessionOutcome.TIMED_OUT
        terminateSession(outcome, stopActiveAlert = true)
    }

    /**
     * Manually cancels the active nap session.
     *
     * The coordinator calls this whenever any session ends (including after this nap already
     * terminated on its own, or after a Transit session), so the active alert is only cancelled
     * when this manager owns a session that is still running; otherwise an alert that must stay
     * visible (e.g. an arrival alert) would be silenced.
     */
    fun stopSession() {
        val ownsRunningSession = ownedSessionStartTimestamp != null && !isTerminating.get()
        Log.i(TAG, "stopSession (ownsRunningSession=$ownsRunningSession): cancelling nap jobs")
        napJob?.cancel()
        napJob = null
        locationTrackingJob?.cancel()
        locationTrackingJob = null
        absoluteCapJob?.cancel()
        absoluteCapJob = null
        dismissalCollectorJob?.cancel()
        dismissalCollectorJob = null
        lateBaselineJob?.cancel()
        lateBaselineJob = null
        if (!ownsRunningSession) return
        resolveAlertController()?.cancelAlert()
        terminateSession(SessionOutcome.CANCELLED, stopActiveAlert = true)
    }

    private fun terminateSession(outcome: SessionOutcome, stopActiveAlert: Boolean) {
        if (isTerminating.compareAndSet(false, true)) {
            currentNapPhase = when (outcome) {
                SessionOutcome.COMPLETED -> NapPhase.COMPLETED
                SessionOutcome.TIMED_OUT -> NapPhase.TIMED_OUT
                SessionOutcome.CANCELLED -> NapPhase.CANCELLED
                SessionOutcome.INTERRUPTED_BY_ARRIVAL -> NapPhase.COMPLETED
            }
            napJob?.cancel()
            napJob = null
            locationTrackingJob?.cancel()
            locationTrackingJob = null
            absoluteCapJob?.cancel()
            absoluteCapJob = null
            dismissalCollectorJob?.cancel()
            dismissalCollectorJob = null
            lateBaselineJob?.cancel()
            lateBaselineJob = null
            val owned = ownedSessionStartTimestamp
            if (owned != null) {
                sessionManager.endSession(outcome, stopActiveAlert = stopActiveAlert, expectedStartTimestamp = owned)
            } else {
                Log.w(TAG, "terminateSession($outcome): this manager never started a session; nothing to end")
            }
            Log.i(TAG, "Nap session terminated with outcome: $outcome (stopActiveAlert=$stopActiveAlert)")
        }
    }
}
