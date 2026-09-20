package com.wakesync.core.places

import androidx.compose.ui.graphics.ImageBitmap
import com.wakesync.core.model.GeoPoint

/**
 * Data representation of a single Google Places autocomplete suggestion (RF-PLC-01, Appendix F.3).
 *
 * @property placeId Unique identifier assigned by Google Places.
 * @property primaryText Main descriptive title of the place (e.g., "Universidad Cooperativa de Colombia").
 * @property secondaryText Optional secondary subtitle (e.g., "Envigado, Antioquia").
 */
data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String? = null
)

/**
 * Observable state representation for destination selection with Google Maps (CR-01, RF-PLC-01 to RF-PLC-06).
 */
sealed class DestinationPickerState {
    object Idle : DestinationPickerState()
    object Loading : DestinationPickerState()
    data class Results(val predictions: List<PlacePrediction>) : DestinationPickerState()
    data class Map(val center: GeoPoint, val zoom: Int, val image: ImageBitmap?) : DestinationPickerState()
    data class Confirm(
        val destination: GeoPoint,
        val address: String?,
        val straightLineMeters: Float?
    ) : DestinationPickerState()
    object Offline : DestinationPickerState() // RF-PLC-05, orange accent
    data class Error(val message: String) : DestinationPickerState()
}
