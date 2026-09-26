package com.wakesync.core.insights

/**
 * Observable state representation for the AI wellness insight generation flow (RF-INS-01 to RF-INS-04).
 */
sealed class InsightState {
    object Idle : InsightState()
    object Loading : InsightState()
    data class Ready(val text: String) : InsightState()

    /**
     * No insight could be obtained; the UI offers a manual retry.
     *
     * @property reason Why the request failed, so the UI never claims "offline" for a request that
     * reached the backend but failed or timed out.
     */
    data class Unavailable(val reason: InsightFailureReason) : InsightState()
}

/**
 * Cause of an [InsightState.Unavailable] state, exposed to the UI without leaking network types.
 */
enum class InsightFailureReason {
    /** The watch had no validated network, so no request was sent. */
    OFFLINE,

    /** A request was attempted but timed out, was rejected, or could not be processed. */
    FAILED
}
