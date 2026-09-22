package com.wakesync.ai

import com.wakesync.core.model.RestState

/**
 * Output contract emitted after each rest estimation inference cycle.
 *
 * @property score Calculated rest index in range [0.0, 1.0].
 * @property state Categorized physiological state: AWAKE, LIGHT_REST, DEEP_REST.
 * @property consecutiveDeepRestCount Number of consecutive cycles with score >= 0.60.
 * @property isDataValid False if biomedical inputs are missing, stale (>15s), or physiologically invalid.
 * @property deltaHrRelative Relative heart rate reduction component in range [0.0, 1.0].
 * @property quietude Motion quietude component in range [0.0, 1.0].
 * @property timestamp System timestamp when the evaluation was computed.
 */
data class RestEvaluationResult(
    val score: Float,
    val state: RestState,
    val consecutiveDeepRestCount: Int,
    val isDataValid: Boolean,
    val deltaHrRelative: Float = 0.0f,
    val quietude: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)
