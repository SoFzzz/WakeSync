package com.wakesync.transit

import com.wakesync.core.model.GeoPoint

/**
 * Predefined destination coordinates for TransitNudge operating mode (RF-TRAN-01).
 * Provides the 3 catalogued urban locations: Campus UCC, Estación Metro, and Casa.
 */
object TransitDestinations {
    val CAMPUS_UCC = GeoPoint(6.2518, -75.5684, "Campus UCC")
    val ESTACION_METRO = GeoPoint(6.2550, -75.5700, "Estación Metro")
    val CASA = GeoPoint(6.2400, -75.5800, "Casa")

    val PREDEFINED_DESTINATIONS: List<GeoPoint> = listOf(
        CAMPUS_UCC,
        ESTACION_METRO,
        CASA
    )
}
