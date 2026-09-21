package com.wakesync.core.insights

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract interface for requesting post-session AI wellness insights from Gemini.
 * Defined in com.wakesync.core and implemented by com.wakesync.insights (Dependency Inversion).
 *
 * Covers RF-CORE-07, RF-INS-01 to RF-INS-04.
 */
interface InsightContract {
    /**
     * Observable flow of the insight generation state.
     */
    val state: StateFlow<InsightState>

    /**
     * Initiates asynchronous generation of post-session insight.
     *
     * @param sessionStartTimestamp Epoch timestamp of the target session record used as a local ID.
     */
    fun requestInsight(sessionStartTimestamp: Long)

    /**
     * Retries requesting an insight if previously [InsightState.Unavailable].
     */
    fun retry()
}
