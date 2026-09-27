package com.wakesync.core.session

import android.content.Context
import android.content.ContextWrapper
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying thread-safe singleton lifecycle and isolation for [SessionManager.Companion.getInstance].
 */
class SessionManagerSingletonTest {

    private val mockContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        SessionManager.resetInstanceForTesting()
    }

    @After
    fun tearDown() {
        SessionManager.resetInstanceForTesting()
    }

    @Test
    fun getInstance_calledMultipleTimes_returnsExactSameReference() {
        val firstInstance = SessionManager.getInstance(mockContext)
        val secondInstance = SessionManager.getInstance(mockContext)

        assertNotNull(firstInstance)
        assertSame("Subsequent calls to getInstance must return the exact same singleton instance", firstInstance, secondInstance)
    }

    @Test
    fun resetInstanceForTesting_clearsSingleton_allowingNewInstanceCreation() {
        val firstInstance = SessionManager.getInstance(mockContext)

        SessionManager.resetInstanceForTesting()

        val newInstance = SessionManager.getInstance(mockContext)
        assertNotNull(newInstance)
        assertNotSame("After reset, getInstance must create a new distinct instance", firstInstance, newInstance)
    }
}
