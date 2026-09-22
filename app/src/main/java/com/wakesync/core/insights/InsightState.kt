package com.wakesync.core.insights

/**
 * Observable state representation for the AI wellness insight generation flow (RF-INS-01 to RF-INS-04).
 */
sealed class InsightState {
    object Idle : InsightState()
    object Loading : InsightState()
    data class Ready(val text: String) : InsightState()
    object Unavailable : InsightState() // Offline or failed; allows retry in UI
}
