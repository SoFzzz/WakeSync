package com.wakesync.core.insights

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [InsightProvider] verifying singleton registration, isolation, and retrieval.
 */
class InsightProviderTest {

    private class MockInsightContract : InsightContract {
        override val state: StateFlow<InsightState> = MutableStateFlow(InsightState.Idle)
        var requestedTimestamp: Long? = null
        var retryCalled = false

        override fun requestInsight(sessionStartTimestamp: Long) {
            requestedTimestamp = sessionStartTimestamp
        }

        override fun retry() {
            retryCalled = true
        }
    }

    @Before
    fun setUp() {
        InsightProvider.resetForTesting()
    }

    @After
    fun tearDown() {
        InsightProvider.resetForTesting()
    }

    @Test
    fun get_returnsNullBeforeAnyRegistration() {
        assertNull("get() must return null before registration", InsightProvider.get())
    }

    @Test
    fun register_followedByGet_returnsSameInstance() {
        val mock = MockInsightContract()
        InsightProvider.register(mock)

        val retrieved = InsightProvider.get()
        assertSame("Retrieved instance must be identical to registered instance", mock, retrieved)
    }

    @Test
    fun register_overwritesPreviousInstance() {
        val firstMock = MockInsightContract()
        val secondMock = MockInsightContract()

        InsightProvider.register(firstMock)
        assertSame(firstMock, InsightProvider.get())

        InsightProvider.register(secondMock)
        assertSame(secondMock, InsightProvider.get())
    }

    @Test
    fun resetForTesting_clearsRegisteredInstance() {
        val mock = MockInsightContract()
        InsightProvider.register(mock)
        InsightProvider.resetForTesting()

        assertNull(InsightProvider.get())
    }
}
