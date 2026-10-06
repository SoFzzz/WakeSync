package com.wakesync.core.model

import java.util.UUID

/**
 * Historical record of a completed or terminated session.
 * Adheres to local-storage-spec with the intentional 'id' extension for Compose key stability.
 *
 * @property id Unique identifier for UI list anchoring and individual record operations.
 * @property sessionType The operating mode executed (NAP or TRANSIT).
 * @property startTimestamp Epoch timestamp in milliseconds when the session was started.
 * @property durationSeconds Total elapsed duration of the session in seconds.
 * @property restLatencySeconds Seconds taken to achieve DEEP_REST (Nap mode only; null otherwise).
 * @property outcome Final termination outcome of the session.
 * @property insightText Gemini AI generated wellness insight (<=140 chars); null if not yet generated (CR-01, RF-INS-03).
 */
data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val sessionType: SessionType,
    val startTimestamp: Long,
    val durationSeconds: Int,
    val restLatencySeconds: Int?,
    val outcome: SessionOutcome,
    val insightText: String? = null
)

