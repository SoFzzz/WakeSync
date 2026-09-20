package com.wakesync.core.model

/**
 * State representation specific to the Transit (TransitNudge) operating mode.
 *
 * @property phase Current progression phase of the transit tracking session.
 * @property destination Target geographic coordinate for arrival detection.
 * @property currentDistanceMeters Straight-line distance to destination using Haversine formula.
 * @property dynamicAlertRadiusMeters Calculated trigger radius (meters), with a minimum floor of 250m.
 * @property estimatedSpeedMps Estimated user ground velocity in meters per second.
 */
data class TransitSessionState(
    val phase: TransitPhase = TransitPhase.IDLE,
    val destination: GeoPoint? = null,
    val currentDistanceMeters: Float? = null,
    val dynamicAlertRadiusMeters: Float = 250f,
    val estimatedSpeedMps: Float? = null
)
