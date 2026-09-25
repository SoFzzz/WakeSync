package com.wakesync.ai

import com.wakesync.core.model.RestState

/**
 * Output contract emitted after each rest estimation inference cycle.
 *
 * @property score Calculated rest index in range [0.0, 1.0].
 * @property state Categorized physiological state: AWAKE, LIGHT_REST, DEEP_REST.
 * @property consecutiveDeepRestCount Number of consecutive cycles with score >= 0.60.
 * @property isDataValid False if biomedical inputs are missing, stale (>15s), or physiologically invalid.
 * @property isCalibrating F24: true only when [isDataValid] is false because no basal heart rate
 *   has been set yet (`setBaseHeartRate` not called), while HR readings are otherwise fresh and
 *   in range — i.e. the engine has no reason to think the sensor itself is unavailable. This is
 *   a raw signal only: it says nothing about whether a Nap session is actually calibrating (it is
 *   also true for the whole duration of a Transit session, which never calibrates, and for a Nap
 *   already in MONITORING after a zero-reading calibration). Callers that map this to
 *   `RestState.CALIBRATING` must additionally gate on the caller's own session phase — see
 *   `AppSessionCoordinator.startBiometricsRelay()`. Always false when [isDataValid] is true.
 * @property deltaHrRelative Relative heart rate reduction component in range [0.0, 1.0].
 * @property quietude Motion quietude component in range [0.0, 1.0].
 * @property timestamp System timestamp when the evaluation was computed.
 */
data class RestEvaluationResult(
    val score: Float,
    val state: RestState,
    val consecutiveDeepRestCount: Int,
    val isDataValid: Boolean,
    val isCalibrating: Boolean = false,
    val deltaHrRelative: Float = 0.0f,
    val quietude: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)
