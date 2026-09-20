package com.wakesync.core.geo

import com.wakesync.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceCalculatorTest {

    @Test
    fun `haversine returns zero for identical coordinates`() {
        val p1 = GeoPoint(6.2518, -75.5684, "Campus UCC")
        val distance = GeofenceCalculator.haversineMeters(p1, p1)
        assertEquals(0.0, distance, 0.0001)
    }

    @Test
    fun `haversine computes exact distance for one degree of latitude`() {
        // 1 degree of latitude = 2 * PI * R / 360 = 2 * PI * 6,371,000 / 360 ≈ 111,194.9266 m
        val lat1 = 0.0
        val lat2 = 1.0
        val lon = 0.0

        val distance = GeofenceCalculator.haversineMeters(lat1, lon, lat2, lon)
        assertEquals(111_194.9266, distance, 0.5)
    }

    @Test
    fun `calculateDynamicAlertRadius returns 250m floor when velocity is zero`() {
        val radiusNormal = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = 0.0, isDeepRest = false)
        val radiusDeep = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = 0.0, isDeepRest = true)

        assertEquals(250.0, radiusNormal, 0.0001)
        assertEquals(250.0, radiusDeep, 0.0001)
    }

    @Test
    fun `calculateDynamicAlertRadius protects against negative velocity and enforces 250m floor`() {
        val radiusNegative = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = -10.0, isDeepRest = false)
        assertEquals(250.0, radiusNegative, 0.0001)
    }

    @Test
    fun `calculateDynamicAlertRadius enforces 250m floor when calculated value is below 250m`() {
        // At v = 2.0 m/s: 2 * 60 + 4 / 2.2 = 120 + 1.82 = 121.82 m < 250.0 m -> must be 250.0 m
        val radius = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = 2.0, isDeepRest = false)
        assertEquals(250.0, radius, 0.0001)
    }

    @Test
    fun `calculateDynamicAlertRadius computes exact radius for 15 m per s in normal and deep rest`() {
        // v = 15.0 m/s (~54 km/h), a = 1.1 m/s²
        // Braking distance component: 15² / (2 * 1.1) = 225 / 2.2 ≈ 102.2727 m
        // Normal (T = 60s): 15 * 60 + 102.2727 = 900 + 102.2727 = 1002.2727 m
        val radiusNormal = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = 15.0, isDeepRest = false)
        assertEquals(1002.2727, radiusNormal, 0.01)

        // DEEP_REST (T = 90s): 15 * 90 + 102.2727 = 1350 + 102.2727 = 1452.2727 m
        val radiusDeep = GeofenceCalculator.calculateDynamicAlertRadius(speedMps = 15.0, isDeepRest = true)
        assertEquals(1452.2727, radiusDeep, 0.01)

        // Verify deep rest adds exactly (90 - 60) * 15 = 450 meters
        assertEquals(450.0, radiusDeep - radiusNormal, 0.01)
    }

    @Test
    fun `estimateSpeedMps calculates speed correctly and handles edge cases`() {
        val p1 = GeoPoint(0.0, 0.0)
        // Point approximately 300m North
        val latOffset = 300.0 / 111_194.9266
        val p2 = GeoPoint(latOffset, 0.0)

        // 300m in 20s = 15 m/s
        val speed = GeofenceCalculator.estimateSpeedMps(
            prevPoint = p1,
            prevTimestampMs = 10_000L,
            currentPoint = p2,
            currentTimestampMs = 30_000L // delta = 20s
        )
        assertEquals(15.0, speed, 0.05)

        // Edge case: Delta time <= 0
        val zeroTimeSpeed = GeofenceCalculator.estimateSpeedMps(p1, 10_000L, p2, 10_000L)
        assertEquals(0.0, zeroTimeSpeed, 0.0001)

        // Edge case: Identical points
        val stationarySpeed = GeofenceCalculator.estimateSpeedMps(p1, 10_000L, p1, 20_000L)
        assertEquals(0.0, stationarySpeed, 0.0001)
    }
}
