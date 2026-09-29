package com.wakesync.ui.format

import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiFormattersTest {

    @Test
    fun `formatStraightLineDistance returns null when meters is null`() {
        assertNull(UiFormatters.formatStraightLineDistance(null))
    }

    @Test
    fun `formatStraightLineDistance formats meters below 1km as integer meters`() {
        assertEquals("850 m", UiFormatters.formatStraightLineDistance(850f))
    }

    @Test
    fun `formatStraightLineDistance formats 1000m and above as km`() {
        assertEquals("1.2 km", UiFormatters.formatStraightLineDistance(1234f))
        assertEquals("1.0 km", UiFormatters.formatStraightLineDistance(1000f))
    }

    @Test
    fun `formatSessionDuration formats minutes and seconds with zero padding`() {
        assertEquals("07:05", UiFormatters.formatSessionDuration(425))
        assertEquals("00:09", UiFormatters.formatSessionDuration(9))
    }

    @Test
    fun `formatSessionDuration clamps negative durations to zero`() {
        assertEquals("00:00", UiFormatters.formatSessionDuration(-5))
    }

    @Test
    fun `mostRecentSessionRecord picks highest startTimestamp regardless of list order`() {
        val older = SessionRecord(
            sessionType = SessionType.NAP,
            startTimestamp = 1000L,
            durationSeconds = 60,
            restLatencySeconds = null,
            outcome = SessionOutcome.COMPLETED
        )
        val newer = SessionRecord(
            sessionType = SessionType.TRANSIT,
            startTimestamp = 5000L,
            durationSeconds = 30,
            restLatencySeconds = null,
            outcome = SessionOutcome.COMPLETED
        )

        // Deliberately oldest-first order to prove the selector does not depend on list order.
        assertEquals(newer.id, UiFormatters.mostRecentSessionRecord(listOf(older, newer))?.id)
        // And newest-first order (the repository's real prepend order) must still work.
        assertEquals(newer.id, UiFormatters.mostRecentSessionRecord(listOf(newer, older))?.id)
    }

    @Test
    fun `mostRecentSessionRecord returns null for empty history`() {
        assertNull(UiFormatters.mostRecentSessionRecord(emptyList()))
    }

    @Test
    fun `resolveJustEndedSessionRecord finds the new record when baseline predates it`() {
        val old = SessionRecord(
            sessionType = SessionType.NAP,
            startTimestamp = 1000L,
            durationSeconds = 60,
            restLatencySeconds = null,
            outcome = SessionOutcome.COMPLETED
        )
        val new = SessionRecord(
            sessionType = SessionType.TRANSIT,
            startTimestamp = 2000L,
            durationSeconds = 30,
            restLatencySeconds = null,
            outcome = SessionOutcome.COMPLETED
        )
        // Newest-first, matching SessionHistoryRepository's real prepend order.
        val history = listOf(new, old)

        val result = UiFormatters.resolveJustEndedSessionRecord(history, preEndTopRecordId = old.id)

        assertEquals(new.id, result?.id)
    }

    @Test
    fun `resolveJustEndedSessionRecord returns null when the new record was already the baseline`() {
        // Regression test for the original bug: if the "pre-end" baseline is captured AFTER
        // the new record has already landed in history (e.g. captured inside
        // LaunchedEffect(justEndedSession) instead of continuously while the session was still
        // active), preEndTopRecordId ends up equal to the new record's own id and the summary
        // would never appear — this proves that exact failure mode in isolation.
        val newRecord = SessionRecord(
            sessionType = SessionType.NAP,
            startTimestamp = 3000L,
            durationSeconds = 45,
            restLatencySeconds = null,
            outcome = SessionOutcome.COMPLETED
        )
        val history = listOf(newRecord)

        val result = UiFormatters.resolveJustEndedSessionRecord(history, preEndTopRecordId = newRecord.id)

        assertNull(result)
    }

    @Test
    fun `resolveJustEndedSessionRecord returns null while history is still empty`() {
        assertNull(UiFormatters.resolveJustEndedSessionRecord(emptyList(), preEndTopRecordId = UiFormatters.NO_PRIOR_RECORD_ID))
    }

    @Test
    fun `resolveJustEndedSessionRecord finds the very first session ever recorded`() {
        val first = SessionRecord(
            sessionType = SessionType.NAP,
            startTimestamp = 500L,
            durationSeconds = 20,
            restLatencySeconds = null,
            outcome = SessionOutcome.COMPLETED
        )

        val result = UiFormatters.resolveJustEndedSessionRecord(listOf(first), preEndTopRecordId = UiFormatters.NO_PRIOR_RECORD_ID)

        assertEquals(first.id, result?.id)
    }
}
