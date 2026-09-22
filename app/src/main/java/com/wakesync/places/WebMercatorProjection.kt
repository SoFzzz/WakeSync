package com.wakesync.places

import com.wakesync.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator projection utility functions for circular map display on Wear OS.
 * Strictly adheres to Appendix A.7 of WAKESYNC_MASTER_DOCUMENTATION.md.
 */
object WebMercatorProjection {

    const val TILE_SIZE = 256.0
    const val MAX_LATITUDE = 85.0511

    /**
     * Physical screen size of the reference Wear OS device (454x454 physical pixels).
     */
    const val SCREEN_SIZE_PX = 454.0

    /**
     * Map scale factor (scale=2 in Maps Static API request).
     * 1 logical pixel = 2 physical screen pixels.
     */
    const val MAP_SCALE = 2.0

    /**
     * Logical world size in pixels at zoom level [zoom].
     * W = 256 * 2^zoom
     */
    fun worldSize(zoom: Int): Double = TILE_SIZE * 2.0.pow(zoom)

    /**
     * Projects spherical coordinates [point] to world pixel coordinates at zoom level [zoom].
     */
    fun toWorldPx(point: GeoPoint, zoom: Int): Pair<Double, Double> {
        val w = worldSize(zoom)
        val x = w * (point.longitude + 180.0) / 360.0
        val clampedLat = point.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val latRad = Math.toRadians(clampedLat)
        val y = w * (0.5 - ln(tan(PI / 4.0 + latRad / 2.0)) / (2.0 * PI))
        return x to y
    }

    /**
     * Inverts world pixel coordinates ([x], [y]) back to spherical [GeoPoint] at zoom level [zoom].
     */
    fun fromWorldPx(x: Double, y: Double, zoom: Int): GeoPoint {
        val w = worldSize(zoom)
        val lon = ((360.0 * x / w - 180.0 + 540.0) % 360.0) - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / w))))
        return GeoPoint(lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE), lon)
    }

    /**
     * Computes the new map center after dragging the viewport by screen pixels ([dxScreenPx], [dyScreenPx]).
     *
     * Because 1 logical pixel = 2 physical pixels, screen delta is divided by [MAP_SCALE].
     * x' = xc - dxScreen / 2, y' = yc - dyScreen / 2
     */
    fun pan(center: GeoPoint, dxScreenPx: Float, dyScreenPx: Float, zoom: Int): GeoPoint {
        val (xc, yc) = toWorldPx(center, zoom)
        val newX = xc - (dxScreenPx / MAP_SCALE)
        val newY = yc - (dyScreenPx / MAP_SCALE)
        return fromWorldPx(newX, newY, zoom)
    }

    /**
     * Computes the new map center when tapping on screen coordinate ([xScreenPx], [yScreenPx]).
     *
     * Offset from screen center (S / 2) divided by [MAP_SCALE].
     * x' = xc + (sx - S / 2) / 2, y' = yc + (sy - S / 2) / 2
     */
    fun tap(
        center: GeoPoint,
        xScreenPx: Float,
        yScreenPx: Float,
        zoom: Int,
        screenSizePx: Double = SCREEN_SIZE_PX
    ): GeoPoint {
        val (xc, yc) = toWorldPx(center, zoom)
        val halfScreen = screenSizePx / 2.0
        val newX = xc + ((xScreenPx - halfScreen) / MAP_SCALE)
        val newY = yc + ((yScreenPx - halfScreen) / MAP_SCALE)
        return fromWorldPx(newX, newY, zoom)
    }
}
