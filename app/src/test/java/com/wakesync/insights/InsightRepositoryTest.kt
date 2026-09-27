package com.wakesync.insights

import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.network.ApiResult
import com.wakesync.network.BackendClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightRepositoryTest {

    private lateinit var fakeBackendClient: FakeBackendClient
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testTimestamp = 1726000000000L
    private val sampleRecord = SessionRecord(
        id = "test-id-1",
        sessionType = SessionType.NAP,
        startTimestamp = testTimestamp,
        durationSeconds = 1200,
        restLatencySeconds = 240,
        outcome = SessionOutcome.COMPLETED,
        insightText = null
    )

    private var currentRecords = mutableListOf<SessionRecord>()
    private var lastUpdatedInsight: Pair<Long, String>? = null

    class FakeBackendClient : BackendClient() {
        var requestedSessionType: String? = null
        var requestedDuration: Int? = null
        var requestedLatency: Int? = null
        var requestedOutcome: String? = null

        var insightResult: ApiResult<String> = ApiResult.Success("Default insight")

        override suspend fun requestInsight(
            sessionType: String,
            durationSeconds: Int,
            restLatencySeconds: Int?,
            outcome: String
        ): ApiResult<String> {
            requestedSessionType = sessionType
            requestedDuration = durationSeconds
            requestedLatency = restLatencySeconds
            requestedOutcome = outcome
            return insightResult
        }
    }

    private lateinit var repository: InsightRepository

    @Before
    fun setUp() {
        fakeBackendClient = FakeBackendClient()
        currentRecords = mutableListOf(sampleRecord)
        lastUpdatedInsight = null

        repository = InsightRepository(
            backendClient = fakeBackendClient,
            historyProvider = { currentRecords },
            insightUpdater = { ts, text -> lastUpdatedInsight = Pair(ts, text) },
            scope = testScope
        )
    }

    @Test
    fun `requestInsight extracts strictly 4 aggregated fields and emits Ready on success`() = testScope.runTest {
        fakeBackendClient.insightResult = ApiResult.Success("Buen descanso para continuar tu dia.")

        repository.requestInsight(testTimestamp)
        assertEquals(InsightState.Loading, repository.state.value)

        advanceUntilIdle()

        val state = repository.state.value
        assertTrue(state is InsightState.Ready)
        assertEquals("Buen descanso para continuar tu dia.", (state as InsightState.Ready).text)

        assertEquals("NAP", fakeBackendClient.requestedSessionType)
        assertEquals(1200, fakeBackendClient.requestedDuration)
        assertEquals(240, fakeBackendClient.requestedLatency)
        assertEquals("COMPLETED", fakeBackendClient.requestedOutcome)

        assertEquals(Pair(testTimestamp, "Buen descanso para continuar tu dia."), lastUpdatedInsight)
    }

    @Test
    fun `requestInsight reuses existing insightText without calling backend`() = testScope.runTest {
        val cachedRecord = sampleRecord.copy(insightText = "Texto ya generado previamente.")
        currentRecords = mutableListOf(cachedRecord)

        repository.requestInsight(testTimestamp)
        advanceUntilIdle()

        val state = repository.state.value
        assertTrue(state is InsightState.Ready)
        assertEquals("Texto ya generado previamente.", (state as InsightState.Ready).text)

        assertEquals(null, fakeBackendClient.requestedSessionType)
    }

    @Test
    fun `requestInsight emits Unavailable on network failure without throwing`() = testScope.runTest {
        fakeBackendClient.insightResult = ApiResult.NetworkUnavailable

        repository.requestInsight(testTimestamp)
        advanceUntilIdle()

        assertEquals(InsightState.Unavailable, repository.state.value)
    }

    @Test
    fun `retry re-executes last requested session insight`() = testScope.runTest {
        fakeBackendClient.insightResult = ApiResult.Timeout

        repository.requestInsight(testTimestamp)
        advanceUntilIdle()
        assertEquals(InsightState.Unavailable, repository.state.value)

        // Switch to success
        fakeBackendClient.insightResult = ApiResult.Success("Segundo intento exitoso.")
        repository.retry()
        advanceUntilIdle()

        val state = repository.state.value
        assertTrue(state is InsightState.Ready)
        assertEquals("Segundo intento exitoso.", (state as InsightState.Ready).text)
    }
}
