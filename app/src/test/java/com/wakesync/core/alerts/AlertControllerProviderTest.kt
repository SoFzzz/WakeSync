package com.wakesync.core.alerts

import com.wakesync.core.model.AlertLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AlertControllerProvider] verifying singleton registration, isolation, and retrieval.
 */
class AlertControllerProviderTest {

    private class MockAlertController : AlertControllerContract {
        override val activeAlertLevel: StateFlow<AlertLevel> = MutableStateFlow(AlertLevel.NONE)
        var triggerCalledWith: AlertLevel? = null
        var cancelCalled = false

        override fun triggerAlert(level: AlertLevel) {
            triggerCalledWith = level
        }

        override fun cancelAlert() {
            cancelCalled = true
        }
    }

    @Before
    fun setUp() {
        AlertControllerProvider.resetForTesting()
    }

    @After
    fun tearDown() {
        AlertControllerProvider.resetForTesting()
    }

    @Test
    fun get_returnsNullBeforeAnyRegistration() {
        assertNull("get() must return null before any registration", AlertControllerProvider.get())
    }

    @Test
    fun register_followedByGet_returnsSameInstance() {
        val mock = MockAlertController()
        AlertControllerProvider.register(mock)

        val retrieved = AlertControllerProvider.get()
        assertSame("Retrieved instance must be identical to registered instance", mock, retrieved)
    }

    @Test
    fun register_overwritesPreviousInstance() {
        val firstMock = MockAlertController()
        val secondMock = MockAlertController()

        AlertControllerProvider.register(firstMock)
        assertSame(firstMock, AlertControllerProvider.get())

        AlertControllerProvider.register(secondMock)
        assertSame(secondMock, AlertControllerProvider.get())
    }
}
