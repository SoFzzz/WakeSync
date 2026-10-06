package com.wakesync.ui.format

import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure formatting/selection helpers used by the destination picker and post-session summary
 * screens. Extracted from Composables so they can be unit-tested in app/src/test — this
 * project has no androidTest source set configured for Compose UI tests.
 */
object UiFormatters {

    /**
     * Formats a straight-line distance for the Confirm Destination screen.
     * Returns null when the distance is unknown so the caller omits the row entirely
     * instead of rendering a misleading "0 m" or "--" placeholder.
     */
    fun formatStraightLineDistance(meters: Float?): String? {
        if (meters == null) return null
        return if (meters >= 1000f) {
            String.format(Locale.US, "%.1f km", meters / 1000f)
        } else {
            String.format(Locale.US, "%.0f m", meters)
        }
    }

    /**
     * Formats a completed session duration as mm:ss for the Post-Session Summary card.
     */
    fun formatSessionDuration(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val seconds = safeSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Selects the most recently started session record by [SessionRecord.startTimestamp].
     *
     * Never relies on list order: [com.wakesync.core.data.SessionHistoryRepository.sessionHistory]
     * is prepended newest-first, but trusting that order here previously caused the wrong
     * (oldest) session's cached insight to be shown for a different session with no visible error.
     */
    fun mostRecentSessionRecord(history: List<SessionRecord>): SessionRecord? =
        history.maxByOrNull { it.startTimestamp }

    /**
     * Sentinel for "no record existed yet" when tracking the newest record id continuously
     * while a session is active, so the very first session ever recorded can still be detected
     * once it lands (a real null would be indistinguishable from "haven't captured a baseline").
     */
    const val NO_PRIOR_RECORD_ID: String = "\u0000no-prior-record"

    /**
     * Resolves the [SessionRecord] for a just-ended session, given [preEndTopRecordId] — the id
     * of whatever record was already the newest one *while the session was still active*.
     *
     * [preEndTopRecordId] must be captured continuously before the session ends, not at the
     * moment the end is detected: if it were captured only once termination is observed, the
     * session's own record could already be persisted by then (SessionManager.endSession()
     * writes it asynchronously), making [preEndTopRecordId] equal to the new record's id and
     * this function would incorrectly report "nothing new" — the summary would then never
     * appear. See [WakeSyncNavHost][com.wakesync.ui.navigation] for how the baseline is kept
     * up to date.
     *
     * @return The new record once it appears in [history], or null while still waiting for it
     * (or when [history] is empty).
     */
    fun resolveJustEndedSessionRecord(history: List<SessionRecord>, preEndTopRecordId: String?): SessionRecord? {
        val topNow = mostRecentSessionRecord(history) ?: return null
        return topNow.takeIf { it.id != preEndTopRecordId }
    }

    private val HISTORY_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.US)

    /**
     * Formats a session start time for a Historial row as `dd/MM · HH:mm` (numeric, so it never
     * depends on localized month abbreviations fitting the row width).
     */
    fun formatHistoryDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        HISTORY_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    private val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.US)
    private val HISTORY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    /**
     * Historial row, line 1 date part (`dd/MM`). The time moves to line 2 so line 1 fits next to
     * the "has insight" dot without truncating.
     */
    fun formatHistoryDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        HISTORY_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    /** Historial row, line 2 start time (`HH:mm`, 24h). */
    fun formatHistoryTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        HISTORY_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    /**
     * Orders Historial newest-first by [SessionRecord.startTimestamp] — same reason as
     * [mostRecentSessionRecord]: the repository's list order is not a contract.
     */
    fun historyNewestFirst(history: List<SessionRecord>): List<SessionRecord> =
        history.sortedByDescending { it.startTimestamp }

    /**
     * Resolves what the Detalle screen's insight card shows for [record] (RF-INS-03).
     *
     * - A persisted [SessionRecord.insightText] always wins: shown with no backend call.
     * - If this screen never requested an insight for [record] ([requestedForId] differs), returns
     *   null: the caller shows the `NoInsight` variant with `[Generar Insight]`.
     * - [InsightState.Ready] from the shared contract is NOT trusted here: the contract is a
     *   singleton whose state carries no record id, so a late response for a record opened earlier
     *   could land while this one is open. InsightRepository persists the text into the matching
     *   record before emitting Ready, so this record's own text arrives through [record] instead;
     *   until then the card keeps showing Loading.
     */
    fun resolveDetailInsightState(
        record: SessionRecord,
        requestedForId: String?,
        contractState: InsightState
    ): InsightState? {
        val savedText = record.insightText
        if (!savedText.isNullOrBlank()) return InsightState.Ready(savedText)
        if (requestedForId != record.id) return null
        return when (contractState) {
            InsightState.Idle, is InsightState.Ready -> InsightState.Loading
            else -> contractState
        }
    }
}
