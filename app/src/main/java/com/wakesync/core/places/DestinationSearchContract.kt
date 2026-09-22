package com.wakesync.core.places

import com.wakesync.core.model.GeoPoint
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract interface for Google Maps destination selection flow on Wear OS.
 * Defined in com.wakesync.core and implemented by com.wakesync.places (Dependency Inversion).
 *
 * Covers RF-CORE-07, RF-PLC-01 to RF-PLC-06.
 */
interface DestinationSearchContract {
    /**
     * Observable state of the destination picker.
     */
    val state: StateFlow<DestinationPickerState>

    /**
     * Initiates search predictions with the given query string (RF-PLC-01).
     *
     * @param query Text input (voice or keyboard).
     */
    fun search(query: String)

    /**
     * Selects an autocomplete prediction to resolve its coordinates and details (RF-PLC-02).
     *
     * @param placeId Place ID returned from [PlacePrediction].
     */
    fun selectPrediction(placeId: String)

    /**
     * Opens map centered at the provided location, or current user location if null (RF-PLC-03).
     *
     * @param center Optional explicit center coordinate.
     */
    fun openMap(center: GeoPoint?)

    /**
     * Pans the map viewport by screen pixel delta.
     */
    fun panMap(dxScreenPx: Float, dyScreenPx: Float)

    /**
     * Centers the map viewport on a tapped screen coordinate.
     */
    fun tapMap(xScreenPx: Float, yScreenPx: Float)

    /**
     * Adjusts the map zoom level by the given delta (typically from rotary crown).
     *
     * @param delta +1 for zoom in, -1 for zoom out.
     */
    fun zoomMap(delta: Int)

    /**
     * Pins the center of the current map viewport as destination and triggers reverse geocoding.
     */
    fun pinMapCenter()

    /**
     * Retries the last failed network request (RF-PLC-05).
     */
    fun retry()

    /**
     * Resets the picker state back to [DestinationPickerState.Idle].
     */
    fun reset()
}
