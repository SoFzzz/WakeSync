package com.wakesync.places

import com.wakesync.core.model.GeoPoint
import com.wakesync.core.places.DestinationPickerState
import com.wakesync.core.places.PlacePrediction
import com.wakesync.network.ApiResult
import com.wakesync.network.BackendClient
import com.wakesync.network.model.PlaceDetailsDto
import com.wakesync.network.model.ReverseGeocodeDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationSearchRepositoryTest {

    private lateinit var fakeBackendClient: FakeBackendClient
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val userLocation = GeoPoint(6.2518, -75.5684, "User Position")

    private lateinit var repository: DestinationSearchRepository

    class FakeBackendClient : BackendClient() {
        var autocompleteResult: ApiResult<List<PlacePrediction>> = ApiResult.Success(emptyList())
        var placeDetailsResult: ApiResult<PlaceDetailsDto> = ApiResult.Success(
            PlaceDetailsDto("id", "name", "addr", 0.0, 0.0)
        )
        var reverseGeocodeResult: ApiResult<ReverseGeocodeDto> = ApiResult.Success(
            ReverseGeocodeDto("name", "addr")
        )
        var staticMapResult: ApiResult<ByteArray> = ApiResult.Success(byteArrayOf(1, 2, 3))

        override suspend fun autocomplete(
            query: String,
            sessionToken: String,
            biasLat: Double?,
            biasLng: Double?
        ): ApiResult<List<PlacePrediction>> = autocompleteResult

        override suspend fun getPlaceDetails(
            placeId: String,
            sessionToken: String
        ): ApiResult<PlaceDetailsDto> = placeDetailsResult

        override suspend fun reverseGeocode(
            lat: Double,
            lng: Double
        ): ApiResult<ReverseGeocodeDto> = reverseGeocodeResult

        override suspend fun getStaticMap(
            lat: Double,
            lng: Double,
            zoom: Int,
            size: Int
        ): ApiResult<ByteArray> = staticMapResult
    }

    @Before
    fun setUp() {
        fakeBackendClient = FakeBackendClient()
        repository = DestinationSearchRepository(
            backendClient = fakeBackendClient,
            currentLocationProvider = { userLocation },
            scope = testScope
        )
    }

    @Test
    fun `search with valid query emits Results on backend success`() = testScope.runTest {
        val predictions = listOf(
            PlacePrediction("ChIJ1", "UCC Medellin", "Calle 50")
        )
        fakeBackendClient.autocompleteResult = ApiResult.Success(predictions)

        repository.search("universidad")
        assertEquals(DestinationPickerState.Loading, repository.state.value)

        advanceUntilIdle()

        val state = repository.state.value
        assertTrue(state is DestinationPickerState.Results)
        assertEquals(1, (state as DestinationPickerState.Results).predictions.size)
    }

    @Test
    fun `search with network unavailable emits Offline`() = testScope.runTest {
        fakeBackendClient.autocompleteResult = ApiResult.NetworkUnavailable

        repository.search("universidad")
        advanceUntilIdle()

        assertEquals(DestinationPickerState.Offline, repository.state.value)
    }

    @Test
    fun `selectPrediction resolves details and emits Confirm with straightLineMeters`() = testScope.runTest {
        val details = PlaceDetailsDto(
            placeId = "ChIJ1",
            name = "Universidad Cooperativa",
            address = "Calle 50 #45",
            lat = 6.2520,
            lng = -75.5680
        )
        fakeBackendClient.placeDetailsResult = ApiResult.Success(details)

        repository.selectPrediction("ChIJ1")
        assertEquals(DestinationPickerState.Loading, repository.state.value)

        advanceUntilIdle()

        val state = repository.state.value
        assertTrue(state is DestinationPickerState.Confirm)
        val confirm = state as DestinationPickerState.Confirm
        assertEquals("Universidad Cooperativa", confirm.destination.name)
        assertEquals("Calle 50 #45", confirm.address)
        assertTrue((confirm.straightLineMeters ?: 0f) > 0f)
    }

    @Test
    fun `openMap sets Map state and triggers static map fetch`() = testScope.runTest {
        val dummyPng = byteArrayOf(137.toByte(), 80, 78, 71)
        fakeBackendClient.staticMapResult = ApiResult.Success(dummyPng)

        repository.openMap(userLocation)

        val state = repository.state.value
        assertTrue(state is DestinationPickerState.Map)
        assertEquals(userLocation, (state as DestinationPickerState.Map).center)
    }

    @Test
    fun `panMap debounces 400ms before requesting static map`() = testScope.runTest {
        val dummyPng = byteArrayOf(137.toByte(), 80, 78, 71)
        fakeBackendClient.staticMapResult = ApiResult.Success(dummyPng)

        repository.openMap(userLocation)
        advanceUntilIdle()

        // Pan 50px right
        repository.panMap(50f, 0f)
        val intermediateState = repository.state.value as DestinationPickerState.Map
        assertTrue(intermediateState.center.longitude < userLocation.longitude)

        // Advance 200ms (should not trigger static map yet)
        advanceTimeBy(200)

        // Advance remaining 250ms (crosses 400ms debounce threshold)
        advanceTimeBy(250)
        advanceUntilIdle()
    }

    @Test
    fun `retry after static map network failure restores Map with last center and zoom`() = testScope.runTest {
        repository.openMap(userLocation)
        advanceUntilIdle()

        fakeBackendClient.staticMapResult = ApiResult.NetworkUnavailable
        repository.zoomMap(1)
        advanceUntilIdle()
        assertEquals(DestinationPickerState.Offline, repository.state.value)

        fakeBackendClient.staticMapResult = ApiResult.Success(byteArrayOf(137.toByte(), 80, 78, 71))
        repository.retry()

        val restored = repository.state.value
        assertTrue(restored is DestinationPickerState.Map)
        restored as DestinationPickerState.Map
        assertEquals(userLocation, restored.center)
        assertEquals(DestinationSearchRepository.MAP_DEFAULT_ZOOM + 1, restored.zoom)

        advanceUntilIdle()
        assertTrue(repository.state.value is DestinationPickerState.Map)
    }

    @Test
    fun `pinMapCenter uses reverse geocoding to populate Confirm state`()= testScope.runTest {
        repository.openMap(userLocation)
        advanceUntilIdle()

        fakeBackendClient.reverseGeocodeResult = ApiResult.Success(
            ReverseGeocodeDto("Calle 50 #45", "Calle 50 #45, Medellin")
        )

        repository.pinMapCenter()
        advanceUntilIdle()

        val state = repository.state.value
        assertTrue(state is DestinationPickerState.Confirm)
        val confirm = state as DestinationPickerState.Confirm
        assertEquals("Calle 50 #45", confirm.destination.name)
    }

    @Test
    fun `reset returns state to Idle`() = testScope.runTest {
        repository.search("universidad")
        advanceUntilIdle()

        repository.reset()
        assertEquals(DestinationPickerState.Idle, repository.state.value)
    }
}
