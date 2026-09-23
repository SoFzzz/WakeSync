package com.wakesync.places

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.wakesync.core.geo.GeofenceCalculator
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.places.DestinationPickerState
import com.wakesync.core.places.DestinationSearchContract
import com.wakesync.network.ApiResult
import com.wakesync.network.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.round

/**
 * Concrete implementation of [DestinationSearchContract] in com.wakesync.places.
 * Coordinates autocomplete, place details, Web Mercator static maps, and reverse geocoding
 * through [BackendClient] (RF-PLC-01 to RF-PLC-06).
 */
class DestinationSearchRepository(
    private val backendClient: BackendClient,
    private val currentLocationProvider: () -> GeoPoint? = { null },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : DestinationSearchContract {

    companion object {
        const val PLACES_MIN_QUERY_CHARS = 3
        const val MAP_DEFAULT_ZOOM = 16
        const val MAP_MIN_ZOOM = 10
        const val MAP_MAX_ZOOM = 19
        const val MAP_PAN_DEBOUNCE_MS = 400L
        const val MAP_REQUEST_SIZE_PX = 227 // 454 / 2 = 227 logical px (scale=2)
        val DEFAULT_FALLBACK_CENTER = GeoPoint(6.2518, -75.5684, "Campus UCC")
    }

    private val _state = MutableStateFlow<DestinationPickerState>(DestinationPickerState.Idle)
    override val state: StateFlow<DestinationPickerState> = _state.asStateFlow()

    private var sessionToken: String = UUID.randomUUID().toString()
    private var mapDebounceJob: Job? = null
    private var lastAction: (() -> Unit)? = null

    override fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < PLACES_MIN_QUERY_CHARS) {
            return
        }

        // New session token per search flow (RF-PLC-02)
        sessionToken = UUID.randomUUID().toString()
        lastAction = { search(query) }

        _state.value = DestinationPickerState.Loading

        scope.launch {
            // Apply location bias rounded to 2 decimals for privacy (RNF-PLC-02)
            val currentLoc = currentLocationProvider()
            val biasLat = currentLoc?.let { round(it.latitude * 100.0) / 100.0 }
            val biasLng = currentLoc?.let { round(it.longitude * 100.0) / 100.0 }

            when (val result = backendClient.autocomplete(trimmed, sessionToken, biasLat, biasLng)) {
                is ApiResult.Success -> {
                    _state.value = DestinationPickerState.Results(result.data)
                }
                is ApiResult.NetworkUnavailable, is ApiResult.Timeout -> {
                    _state.value = DestinationPickerState.Offline
                }
                is ApiResult.HttpError -> {
                    _state.value = DestinationPickerState.Error("Error HTTP ${result.code}")
                }
                is ApiResult.ParseError -> {
                    _state.value = DestinationPickerState.Error("Error al procesar resultados")
                }
            }
        }
    }

    override fun selectPrediction(placeId: String) {
        lastAction = { selectPrediction(placeId) }
        _state.value = DestinationPickerState.Loading

        scope.launch {
            when (val result = backendClient.getPlaceDetails(placeId, sessionToken)) {
                is ApiResult.Success -> {
                    val dest = GeoPoint(
                        latitude = result.data.lat,
                        longitude = result.data.lng,
                        name = result.data.name.ifBlank { "Destino seleccionado" }
                    )
                    val straightLine = currentLocationProvider()?.let { current ->
                        GeofenceCalculator.haversineMeters(current, dest).toFloat()
                    }
                    _state.value = DestinationPickerState.Confirm(
                        destination = dest,
                        address = result.data.address,
                        straightLineMeters = straightLine
                    )
                }
                is ApiResult.NetworkUnavailable, is ApiResult.Timeout -> {
                    _state.value = DestinationPickerState.Offline
                }
                is ApiResult.HttpError -> {
                    _state.value = DestinationPickerState.Error("Error HTTP ${result.code}")
                }
                is ApiResult.ParseError -> {
                    _state.value = DestinationPickerState.Error("Error al resolver lugar")
                }
            }
        }
    }

    override fun openMap(center: GeoPoint?) {
        val initialCenter = center ?: currentLocationProvider() ?: DEFAULT_FALLBACK_CENTER
        _state.value = DestinationPickerState.Map(
            center = initialCenter,
            zoom = MAP_DEFAULT_ZOOM,
            image = null
        )
        fetchMapImage(initialCenter, MAP_DEFAULT_ZOOM, immediate = true)
    }

    override fun panMap(dxScreenPx: Float, dyScreenPx: Float) {
        val current = _state.value as? DestinationPickerState.Map ?: return
        val newCenter = WebMercatorProjection.pan(current.center, dxScreenPx, dyScreenPx, current.zoom)

        // Keep existing bitmap while moving viewport for visual continuity
        _state.value = current.copy(center = newCenter)
        fetchMapImage(newCenter, current.zoom, immediate = false)
    }

    override fun tapMap(xScreenPx: Float, yScreenPx: Float) {
        val current = _state.value as? DestinationPickerState.Map ?: return
        val newCenter = WebMercatorProjection.tap(current.center, xScreenPx, yScreenPx, current.zoom)

        _state.value = current.copy(center = newCenter)
        fetchMapImage(newCenter, current.zoom, immediate = false)
    }

    override fun zoomMap(delta: Int) {
        val current = _state.value as? DestinationPickerState.Map ?: return
        val newZoom = (current.zoom + delta).coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
        if (newZoom == current.zoom) return

        _state.value = current.copy(zoom = newZoom)
        fetchMapImage(current.center, newZoom, immediate = false)
    }

    override fun pinMapCenter() {
        val current = _state.value as? DestinationPickerState.Map ?: return
        val center = current.center

        lastAction = { pinMapCenter() }
        _state.value = DestinationPickerState.Loading

        scope.launch {
            val fallbackName = String.format(
                Locale.US,
                "Punto en el mapa (%.4f, %.4f)",
                center.latitude,
                center.longitude
            )

            var destName = fallbackName
            var destAddress: String? = null

            when (val result = backendClient.reverseGeocode(center.latitude, center.longitude)) {
                is ApiResult.Success -> {
                    destName = result.data.name.ifBlank { fallbackName }
                    destAddress = result.data.address
                }
                is ApiResult.NetworkUnavailable, is ApiResult.Timeout -> {
                    // Safe degradation: allows proceeding even if geocode fails
                    destName = fallbackName
                }
                else -> {
                    destName = fallbackName
                }
            }

            val destination = GeoPoint(center.latitude, center.longitude, destName)
            val distance = currentLocationProvider()?.let { currentLoc ->
                GeofenceCalculator.haversineMeters(currentLoc, destination).toFloat()
            }

            _state.value = DestinationPickerState.Confirm(
                destination = destination,
                address = destAddress,
                straightLineMeters = distance
            )
        }
    }

    override fun retry() {
        lastAction?.invoke()
    }

    override fun reset() {
        mapDebounceJob?.cancel()
        lastAction = null
        _state.value = DestinationPickerState.Idle
    }

    private fun fetchMapImage(center: GeoPoint, zoom: Int, immediate: Boolean) {
        mapDebounceJob?.cancel()
        // A network failure replaces Map with Offline, so retry must restore the viewport first
        lastAction = {
            _state.value = DestinationPickerState.Map(center = center, zoom = zoom, image = null)
            fetchMapImage(center, zoom, immediate = true)
        }

        mapDebounceJob = scope.launch {
            if (!immediate) {
                delay(MAP_PAN_DEBOUNCE_MS)
            }

            when (val result = backendClient.getStaticMap(center.latitude, center.longitude, zoom, MAP_REQUEST_SIZE_PX)) {
                is ApiResult.Success -> {
                    val bitmap = BitmapFactory.decodeByteArray(result.data, 0, result.data.size)
                    val imageBitmap = bitmap?.asImageBitmap()

                    val current = _state.value
                    if (current is DestinationPickerState.Map && current.center == center && current.zoom == zoom) {
                        _state.value = current.copy(image = imageBitmap)
                    }
                }
                is ApiResult.NetworkUnavailable, is ApiResult.Timeout -> {
                    _state.value = DestinationPickerState.Offline
                }
                else -> {
                    // Maintain current display without crashing
                }
            }
        }
    }
}
