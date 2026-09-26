package com.wakesync.ai

import android.util.Log
import com.wakesync.core.model.RestState
import com.wakesync.sensors.SensorSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic heuristic engine that calculates the rest score and classifies rest states.
 * Follows the mathematical specification in rest-estimator-spec:
 *
 *   ΔHR_relativa = max(0, (HR_base - HR_actual) / HR_base)
 *   Quietud      = 1 - min(1, SVM_media / MotionMaxRef)      // MotionMaxRef = 2.5 m/s²
 *   score_reposo = 0.5 * ΔHR_relativa + 0.5 * Quietud
 *
 * Classification:
 * - DEEP_REST: score >= 0.60 sustained for 2 consecutive evaluations.
 * - LIGHT_REST: 0.30 <= score < 0.60.
 * - AWAKE: score < 0.30.
 *
 * Runs strictly in a background coroutine dispatcher (Dispatchers.Default) in < 5 ms per cycle.
 */
class RestEstimatorEngine(
    private val sensorSource: SensorSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        private const val TAG = "RestEstimatorEngine"

        // Mathematical constants from rest-estimator-spec
        const val WEIGHT_HR: Float = 0.5f
        const val WEIGHT_MOTION: Float = 0.5f
        const val MOTION_MAX_REF: Float = 2.5f

        // Classification thresholds
        const val THRESHOLD_DEEP_REST: Float = 0.60f
        const val THRESHOLD_LIGHT_REST: Float = 0.30f
        const val REQUIRED_CONSECUTIVE_DEEP_EVALUATIONS: Int = 2

        // Physiological validity boundaries
        const val HR_MIN_VALID: Int = 35
        const val HR_MAX_VALID: Int = 220

        // Timing constants
        const val EVALUATION_INTERVAL_MS: Long = 10_000L // 10 seconds
        const val ANALYSIS_WINDOW_MS: Long = 25_000L     // 20-30 seconds window
        const val MAX_SENSOR_STALENESS_MS: Long = 15_000L // RF-SENS-04: >15s without valid readings
    }

    private val _evaluationResult = MutableStateFlow(initialResult())
    val evaluationResult: StateFlow<RestEvaluationResult> = _evaluationResult.asStateFlow()

    // Written by the sleep module's calibration coroutine, read by the evaluation loop
    @Volatile
    private var baseHeartRate: Int? = null
    private var consecutiveDeepRestCount: Int = 0
    private var lastValidState: RestState = RestState.AWAKE

    // Circular/timed sample buffers for windowed analysis
    private val hrSamples = ArrayDeque<TimedSample<Int>>()
    private val svmSamples = ArrayDeque<TimedSample<Float>>()
    private val bufferLock = Any()

    private var lastValidHrTimestamp: Long = 0L
    private var engineJob: Job? = null

    /**
     * Calibrates or sets the basal heart rate (HR_base) required for relative delta calculation.
     */
    fun setBaseHeartRate(baseBpm: Int) {
        require(baseBpm in HR_MIN_VALID..HR_MAX_VALID) {
            "Base heart rate $baseBpm BPM is outside physiological range [$HR_MIN_VALID, $HR_MAX_VALID]"
        }
        baseHeartRate = baseBpm
        Log.d(TAG, "Base heart rate calibrated to $baseBpm BPM")
    }

    /**
     * Starts continuous background evaluation cycle.
     */
    fun start(scope: CoroutineScope) {
        if (engineJob != null) return

        engineJob = scope.launch(dispatcher) {
            // Collect Heart Rate stream
            val hrJob = launch {
                sensorSource.getHeartRate().collect { bpm ->
                    val now = timeProvider()
                    if (bpm in HR_MIN_VALID..HR_MAX_VALID) {
                        synchronized(bufferLock) {
                            hrSamples.addLast(TimedSample(bpm, now))
                            lastValidHrTimestamp = now
                            trimOldSamples(hrSamples, now - ANALYSIS_WINDOW_MS)
                        }
                    }
                }
            }

            // Collect Accelerometer SVM stream
            val svmJob = launch {
                sensorSource.getMotionSvm().collect { rawSvm ->
                    val now = timeProvider()
                    val validSvm = max(0.0f, rawSvm) // Enforce non-negative motion
                    synchronized(bufferLock) {
                        svmSamples.addLast(TimedSample(validSvm, now))
                        trimOldSamples(svmSamples, now - ANALYSIS_WINDOW_MS)
                    }
                }
            }

            // Periodic 10-second evaluation loop
            try {
                while (isActive) {
                    delay(EVALUATION_INTERVAL_MS)
                    val result = evaluateCurrentCycle()
                    _evaluationResult.value = result
                }
            } finally {
                hrJob.cancel()
                svmJob.cancel()
            }
        }
    }

    /**
     * Stops background evaluation and clears per-session state (HR_base, DEEP_REST streak, last
     * result) so the next session neither reuses a stale baseline nor replays a confirmed DEEP_REST.
     */
    fun stop() {
        engineJob?.cancel()
        engineJob = null
        synchronized(bufferLock) {
            hrSamples.clear()
            svmSamples.clear()
        }
        baseHeartRate = null
        consecutiveDeepRestCount = 0
        lastValidState = RestState.AWAKE
        lastValidHrTimestamp = 0L
        _evaluationResult.value = initialResult()
    }

    private fun initialResult() = RestEvaluationResult(
        score = 0.0f,
        state = RestState.AWAKE,
        consecutiveDeepRestCount = 0,
        isDataValid = false,
        isInitial = true
    )

    /**
     * Evaluates a single inference cycle. Can be called directly for unit testing.
     * Completes in < 5 ms.
     */
    suspend fun evaluateCurrentCycle(): RestEvaluationResult = withContext(dispatcher) {
        val now = timeProvider()
        val currentBaseHr = baseHeartRate

        val (currentHr, meanSvm, isFresh) = synchronized(bufferLock) {
            trimOldSamples(hrSamples, now - ANALYSIS_WINDOW_MS)
            trimOldSamples(svmSamples, now - ANALYSIS_WINDOW_MS)

            val hr = hrSamples.lastOrNull()?.value
            val svmMean = if (svmSamples.isNotEmpty()) {
                var sum = 0.0f
                for (s in svmSamples) sum += s.value
                sum / svmSamples.size
            } else {
                0.0f
            }
            val fresh = (now - lastValidHrTimestamp) <= MAX_SENSOR_STALENESS_MS && hr != null
            Triple(hr, svmMean, fresh)
        }

        // Validate data availability and physiological limits
        if (!isFresh || currentHr == null || currentBaseHr == null) {
            // F24: fresh, in-range HR but no basal HR yet is a different situation than a
            // genuinely unavailable sensor — but this engine has no notion of session phase, so
            // it only exposes the raw signal. The caller (AppSessionCoordinator) is responsible
            // for gating this on the actual Nap CALIBRATING phase before treating it as such.
            val calibrating = isFresh && currentHr != null && currentBaseHr == null
            val invalidResult = RestEvaluationResult(
                score = _evaluationResult.value.score,
                state = lastValidState,
                consecutiveDeepRestCount = 0,
                isDataValid = false,
                isCalibrating = calibrating,
                timestamp = now
            )
            consecutiveDeepRestCount = 0
            return@withContext invalidResult
        }

        // Pure calculation
        val result = calculateScoreAndState(
            baseHr = currentBaseHr,
            currentHr = currentHr,
            meanSvm = meanSvm,
            previousDeepRestCount = consecutiveDeepRestCount,
            now = now
        )

        // Update state and log transitions
        if (result.state != lastValidState) {
            Log.i(TAG, "Rest state transition: $lastValidState -> ${result.state} at $now")
            lastValidState = result.state
        }
        consecutiveDeepRestCount = result.consecutiveDeepRestCount

        result
    }

    /**
     * Deterministic mathematical formula and classification.
     */
    fun calculateScoreAndState(
        baseHr: Int,
        currentHr: Int,
        meanSvm: Float,
        previousDeepRestCount: Int,
        now: Long = timeProvider()
    ): RestEvaluationResult {
        // Enforce physiological bounds
        if (currentHr !in HR_MIN_VALID..HR_MAX_VALID || baseHr <= 0) {
            return RestEvaluationResult(
                score = 0.0f,
                state = lastValidState,
                consecutiveDeepRestCount = 0,
                isDataValid = false,
                timestamp = now
            )
        }

        val validSvm = max(0.0f, meanSvm)

        // ΔHR_relativa = max(0, (HR_base - HR_actual) / HR_base)
        val deltaHrRelative = max(0.0f, (baseHr - currentHr).toFloat() / baseHr.toFloat())

        // Quietud = 1 - min(1, SVM_media / MotionMaxRef)
        val quietude = 1.0f - min(1.0f, validSvm / MOTION_MAX_REF)

        // score_reposo = 0.5 * ΔHR_relativa + 0.5 * Quietud
        val score = (WEIGHT_HR * deltaHrRelative) + (WEIGHT_MOTION * quietude)

        // Threshold classification
        var newDeepCount = previousDeepRestCount
        val state: RestState

        if (score >= THRESHOLD_DEEP_REST) {
            newDeepCount++
            state = if (newDeepCount >= REQUIRED_CONSECUTIVE_DEEP_EVALUATIONS) {
                RestState.DEEP_REST
            } else {
                RestState.LIGHT_REST
            }
        } else {
            // Any evaluation below 0.60 resets consecutive counter to 0
            newDeepCount = 0
            state = if (score >= THRESHOLD_LIGHT_REST) {
                RestState.LIGHT_REST
            } else {
                RestState.AWAKE
            }
        }

        return RestEvaluationResult(
            score = score,
            state = state,
            consecutiveDeepRestCount = newDeepCount,
            isDataValid = true,
            deltaHrRelative = deltaHrRelative,
            quietude = quietude,
            timestamp = now
        )
    }

    private fun <T> trimOldSamples(deque: ArrayDeque<TimedSample<T>>, cutoff: Long) {
        while (deque.isNotEmpty() && deque.first().timestamp < cutoff) {
            deque.removeFirst()
        }
    }

    private data class TimedSample<T>(val value: T, val timestamp: Long)
}
