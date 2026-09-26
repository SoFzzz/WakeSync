package com.wakesync.places

import com.wakesync.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorProjectionTest {

    @Test
    fun `toWorldPx and fromWorldPx roundtrip has less than 1e-6 degrees error`() {
        val testPoints = listOf(
            GeoPoint(6.2518, -75.5684, "Medellin"),
            GeoPoint(0.0, 0.0, "Equator/PrimeMeridian"),
            GeoPoint(40.7128, -74.0060, "New York"),
            GeoPoint(-33.8688, 151.2093, "Sydney"),
            GeoPoint(51.5074, -0.1278, "London"),
            GeoPoint(-34.6037, -58.3816, "Buenos Aires")
        )

        for (zoom in 10..19) {
            for (point in testPoints) {
                val (x, y) = WebMercatorProjection.toWorldPx(point, zoom)
                val inverted = WebMercatorProjection.fromWorldPx(x, y, zoom)

                assertEquals(
                    "Latitude mismatch at zoom $zoom for ${point.name}",
                    point.latitude,
                    inverted.latitude,
                    1e-6
                )
                assertEquals(
                    "Longitude mismatch at zoom $zoom for ${point.name}",
                    point.longitude,
                    inverted.longitude,
                    1e-6
                )
            }
        }
    }

    @Test
    fun `pan moves map center correctly`() {
        val center = GeoPoint(6.2518, -75.5684)
        val zoom = 16

        // Pan right on screen: drag dx = 100px. Viewport moves right, center shifts west
        val panned = WebMercatorProjection.pan(center, dxScreenPx = 100f, dyScreenPx = 0f, zoom = zoom)

        assertTrue(panned.longitude < center.longitude)
        assertEquals(center.latitude, panned.latitude, 1e-5)
    }

    @Test
    fun `tap on center screen preserves center coordinates`() {
        val center = GeoPoint(6.2518, -75.5684)
        val zoom = 16

        // Tap on exact screen center: (454 / 2, 454 / 2) = (227, 227)
        val tapped = WebMercatorProjection.tap(center, xScreenPx = 227f, yScreenPx = 227f, zoom = zoom)

        assertEquals(center.latitude, tapped.latitude, 1e-6)
        assertEquals(center.longitude, tapped.longitude, 1e-6)
    }

    @Test
    fun `latitude is clamped to Web Mercator limits`() {
        val extremePoint = GeoPoint(89.0, 10.0)
        val (x, y) = WebMercatorProjection.toWorldPx(extremePoint, 16)
        val inverted = WebMercatorProjection.fromWorldPx(x, y, 16)

        assertTrue(inverted.latitude <= WebMercatorProjection.MAX_LATITUDE)
        assertTrue(inverted.latitude >= -WebMercatorProjection.MAX_LATITUDE)
    }
}
