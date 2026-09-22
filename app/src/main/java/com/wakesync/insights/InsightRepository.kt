package com.wakesync.insights

import android.util.Log
import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.insights.InsightContract
import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.SessionRecord
import com.wakesync.network.ApiResult
import com.wakesync.network.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Concrete implementation of [InsightContract] in com.wakesync.insights (RF-INS-01 to RF-INS-04).
 * Coordinates requesting post-session AI wellness insights from Google Gemini via [BackendClient],
 * and persists the resulting text in local history via [SessionHistoryRepository].
 */
class InsightRepository(
    private val backendClient: BackendClient,
    private val sessionHistoryRepository: SessionHistoryRepository? = null,
    private val historyProvider: suspend () -> List<SessionRecord> = {
        sessionHistoryRepository?.sessionHistory?.firstOrNull() ?: emptyList()
    },
    private val insightUpdater: suspend (Long, String) -> Unit = { ts, text ->
        sessionHistoryRepository?.updateInsight(ts, text)
    },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : InsightContract {

    /**
     * Primary production constructor injecting [SessionHistoryRepository].
     */
    constructor(
        backendClient: BackendClient,
        sessionHistoryRepository: SessionHistoryRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
    ) : this(
        backendClient = backendClient,
        sessionHistoryRepository = sessionHistoryRepository,
        historyProvider = { sessionHistoryRepository.sessionHistory.firstOrNull() ?: emptyList() },
        insightUpdater = { ts, text -> sessionHistoryRepository.updateInsight(ts, text) },
        scope = scope
    )

    companion object {
        private const val TAG = "InsightRepository"
        const val INSIGHTS_MAX_CHARS = 140
    }

    private val _state = MutableStateFlow<InsightState>(InsightState.Idle)
    override val state: StateFlow<InsightState> = _state.asStateFlow()

    private var lastRequestedTimestamp: Long? = null

    override fun requestInsight(sessionStartTimestamp: Long) {
        lastRequestedTimestamp = sessionStartTimestamp
        _state.value = InsightState.Loading

        scope.launch {
            try {
                // Find matching record in local storage
                val history = historyProvider()
                val record = history.find { it.startTimestamp == sessionStartTimestamp }

                if (record == null) {
                    Log.w(TAG, "No session record found matching timestamp $sessionStartTimestamp")
                    _state.value = InsightState.Unavailable
                    return@launch
                }

                // If insight was already generated, reuse cached text without calling the backend (RF-INS-03)
                if (!record.insightText.isNullOrBlank()) {
                    _state.value = InsightState.Ready(record.insightText)
                    return@launch
                }

                // Strictly transmit the 4 aggregated parameters (RNF-INS-01)
                val result = backendClient.requestInsight(
                    sessionType = record.sessionType.name,
                    durationSeconds = record.durationSeconds,
                    restLatencySeconds = record.restLatencySeconds,
                    outcome = record.outcome.name
                )

                when (result) {
                    is ApiResult.Success -> {
                        // Re-validate and clamp text to <= 140 chars on the watch side (RF-INS-04)
                        val rawText = result.data.trim()
                        val clampedText = if (rawText.length > INSIGHTS_MAX_CHARS) {
                            rawText.take(INSIGHTS_MAX_CHARS).trim()
                        } else {
                            rawText
                        }

                        // Persist to DataStore
                        insightUpdater(sessionStartTimestamp, clampedText)
                        _state.value = InsightState.Ready(clampedText)
                    }
                    is ApiResult.NetworkUnavailable, is ApiResult.Timeout, is ApiResult.HttpError, is ApiResult.ParseError -> {
                        Log.w(TAG, "Failed to retrieve insight: $result. Degrading to Unavailable (RF-INS-02)")
                        _state.value = InsightState.Unavailable
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error requesting insight: ${e.message}", e)
                _state.value = InsightState.Unavailable
            }
        }
    }

    override fun retry() {
        val ts = lastRequestedTimestamp
        if (ts != null) {
            requestInsight(ts)
        }
    }
}
