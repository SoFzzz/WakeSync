package com.wakesync.core.model

/**
 * Geographic coordinate representation for Transit and optional Nap destinations.
 *
 * @property latitude Geographic latitude in decimal degrees.
 * @property longitude Geographic longitude in decimal degrees.
 * @property name Optional human-readable label (e.g., "Campus UCC").
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null
)
