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
 */
data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val sessionType: SessionType,
    val startTimestamp: Long,
    val durationSeconds: Int,
    val restLatencySeconds: Int?,
    val outcome: SessionOutcome
)
