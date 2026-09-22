package com.wakesync.core.places

import com.wakesync.core.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DestinationSearchProvider] verifying singleton registration, isolation, and retrieval.
 */
class DestinationSearchProviderTest {

    private class MockDestinationSearchContract : DestinationSearchContract {
        override val state: StateFlow<DestinationPickerState> =
            MutableStateFlow(DestinationPickerState.Idle)

        var lastSearchQuery: String? = null
        var selectedPlaceId: String? = null

        override fun search(query: String) {
            lastSearchQuery = query
        }

        override fun selectPrediction(placeId: String) {
            selectedPlaceId = placeId
        }

        override fun openMap(center: GeoPoint?) {}
        override fun panMap(dxScreenPx: Float, dyScreenPx: Float) {}
        override fun tapMap(xScreenPx: Float, yScreenPx: Float) {}
        override fun zoomMap(delta: Int) {}
        override fun pinMapCenter() {}
        override fun retry() {}
        override fun reset() {}
    }

    @Before
    fun setUp() {
        DestinationSearchProvider.resetForTesting()
    }

    @After
    fun tearDown() {
        DestinationSearchProvider.resetForTesting()
    }

    @Test
    fun get_returnsNullBeforeAnyRegistration() {
        assertNull("get() must return null before registration", DestinationSearchProvider.get())
    }

    @Test
    fun register_followedByGet_returnsSameInstance() {
        val mock = MockDestinationSearchContract()
        DestinationSearchProvider.register(mock)

        val retrieved = DestinationSearchProvider.get()
        assertSame("Retrieved instance must be identical to registered instance", mock, retrieved)
    }

    @Test
    fun register_overwritesPreviousInstance() {
        val firstMock = MockDestinationSearchContract()
        val secondMock = MockDestinationSearchContract()

        DestinationSearchProvider.register(firstMock)
        assertSame(firstMock, DestinationSearchProvider.get())

        DestinationSearchProvider.register(secondMock)
        assertSame(secondMock, DestinationSearchProvider.get())
    }

    @Test
    fun resetForTesting_clearsRegisteredInstance() {
        val mock = MockDestinationSearchContract()
        DestinationSearchProvider.register(mock)
        DestinationSearchProvider.resetForTesting()

        assertNull(DestinationSearchProvider.get())
    }
}
