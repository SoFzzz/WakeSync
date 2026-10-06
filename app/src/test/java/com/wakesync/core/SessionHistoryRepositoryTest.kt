package com.wakesync.core

import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests validating round-trip JSON serialization and deserialization in SessionHistoryRepository.
 * Ensures field-by-field parity, including nullable restLatencySeconds and list order.
 */
class SessionHistoryRepositoryTest {

    @Test
    fun jsonRoundTrip_preservesAllFieldsIncludingNullableRestLatency() {
        val napRecordWithLatency = SessionRecord(
            id = UUID.randomUUID().toString(),
            sessionType = SessionType.NAP,
            startTimestamp = 1716120000000L,
            durationSeconds = 900,
            restLatencySeconds = 180,
            outcome = SessionOutcome.COMPLETED
        )

        val transitRecordWithoutLatency = SessionRecord(
            id = UUID.randomUUID().toString(),
            sessionType = SessionType.TRANSIT,
            startTimestamp = 1716121000000L,
            durationSeconds = 620,
            restLatencySeconds = null,
            outcome = SessionOutcome.INTERRUPTED_BY_ARRIVAL
        )

        val originalList = listOf(napRecordWithLatency, transitRecordWithoutLatency)

        // Serialize to JSON string
        val jsonString = SessionHistoryRepository.serializeRecords(originalList)

        // Deserialize back to SessionRecord objects
        val deserializedList = SessionHistoryRepository.deserializeRecords(jsonString)

        assertEquals(2, deserializedList.size)

        // Verify Record 1 (NAP with non-null restLatencySeconds)
        val resultNap = deserializedList[0]
        assertEquals(napRecordWithLatency.id, resultNap.id)
        assertEquals(napRecordWithLatency.sessionType, resultNap.sessionType)
        assertEquals(napRecordWithLatency.startTimestamp, resultNap.startTimestamp)
        assertEquals(napRecordWithLatency.durationSeconds, resultNap.durationSeconds)
        assertEquals(180, resultNap.restLatencySeconds)
        assertEquals(napRecordWithLatency.outcome, resultNap.outcome)
        assertNull(resultNap.insightText)

        // Verify Record 2 (TRANSIT with null restLatencySeconds)
        val resultTransit = deserializedList[1]
        assertEquals(transitRecordWithoutLatency.id, resultTransit.id)
        assertEquals(transitRecordWithoutLatency.sessionType, resultTransit.sessionType)
        assertEquals(transitRecordWithoutLatency.startTimestamp, resultTransit.startTimestamp)
        assertEquals(transitRecordWithoutLatency.durationSeconds, resultTransit.durationSeconds)
        assertNull(resultTransit.restLatencySeconds)
        assertEquals(transitRecordWithoutLatency.outcome, resultTransit.outcome)
        assertNull(resultTransit.insightText)
    }

    @Test
    fun jsonRoundTrip_preservesInsightText() {
        val recordWithInsight = SessionRecord(
            id = UUID.randomUUID().toString(),
            sessionType = SessionType.NAP,
            startTimestamp = 1716120000000L,
            durationSeconds = 1320,
            restLatencySeconds = 240,
            outcome = SessionOutcome.COMPLETED,
            insightText = "Tardaste 4 min en relajarte y completaste tu siesta."
        )

        val jsonString = SessionHistoryRepository.serializeRecords(listOf(recordWithInsight))
        val deserialized = SessionHistoryRepository.deserializeRecords(jsonString)

        assertEquals(1, deserialized.size)
        assertEquals("Tardaste 4 min en relajarte y completaste tu siesta.", deserialized[0].insightText)
    }

    @Test
    fun deserialize_legacyRecordsWithoutInsightText_defaultsToNull() {
        // Legacy JSON without insightText field (v1.0 schema)
        val legacyJson = """
            [
              {
                "id": "legacy-id-123",
                "sessionType": "NAP",
                "startTimestamp": 1716120000000,
                "durationSeconds": 900,
                "restLatencySeconds": 180,
                "outcome": "COMPLETED"
              }
            ]
        """.trimIndent()

        val deserialized = SessionHistoryRepository.deserializeRecords(legacyJson)

        assertEquals(1, deserialized.size)
        assertEquals("legacy-id-123", deserialized[0].id)
        assertNull("insightText must default to null for legacy records", deserialized[0].insightText)
    }

    @Test
    fun emptyListRoundTrip_returnsEmptyList() {
        val emptyList = emptyList<SessionRecord>()
        val jsonString = SessionHistoryRepository.serializeRecords(emptyList)
        val result = SessionHistoryRepository.deserializeRecords(jsonString)

        assertTrue(result.isEmpty())
    }

    @Test
    fun invalidJson_returnsEmptyListGracefully() {
        val malformedJson = "{ not a valid json array }"
        val result = SessionHistoryRepository.deserializeRecords(malformedJson)

        assertTrue(result.isEmpty())
    }
}
