package com.wakesync.core.geo

import com.wakesync.core.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared mathematical utility for geofencing, distance calculation, and dynamic alert radius.
 * Located in com.wakesync.core.geo so both com.wakesync.transit and com.wakesync.sleep can
 * utilize it without violating module boundary rules (transit-geofence-spec).
 */
object GeofenceCalculator {

    // Mathematical constants from transit-geofence-spec & AGENTS.md
    const val EARTH_RADIUS_METERS: Double = 6_371_000.0
    const val MIN_ALERT_RADIUS_METERS: Double = 250.0
    const val T_REACTION_NORMAL_SECONDS: Double = 60.0
    const val T_REACTION_DEEP_REST_SECONDS: Double = 90.0
    const val BRAKING_DECELERATION_MPS2: Double = 1.1

    /**
     * Calculates straight-line spherical geodesic distance between two coordinates using Haversine.
     * Note: This is straight-line distance, not street/road distance.
     *
     * @return Distance in meters.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Convenience overload for [GeoPoint] domain models.
     */
    fun haversineMeters(p1: GeoPoint, p2: GeoPoint): Double =
        haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

    /**
     * Calculates dynamic alert radius R_alert based on vehicle speed and rest state:
     *
     *   R_alert = max(250, v * T_reaccion + v² / (2 * |a_frenado|))
     *
     * @param speedMps Estimated vehicle velocity in meters per second.
     * @param isDeepRest True if RestEstimatorEngine reports DEEP_REST (increases reaction time to 90s).
     * @return Dynamic alert radius in meters, guaranteed >= 250.0m.
     */
    fun calculateDynamicAlertRadius(speedMps: Double, isDeepRest: Boolean): Double {
        val reactionTime = if (isDeepRest) T_REACTION_DEEP_REST_SECONDS else T_REACTION_NORMAL_SECONDS
        val v = max(0.0, speedMps) // Protect against negative corrupted sensor values
        val dynamicRadius = (v * reactionTime) + ((v * v) / (2.0 * BRAKING_DECELERATION_MPS2))
        return max(MIN_ALERT_RADIUS_METERS, dynamicRadius)
    }

    /**
     * Estimates linear speed in m/s between two timestamped geographic positions.
     *
     * @return Estimated speed in m/s (non-negative). Returns 0.0 if time delta <= 0.
     */
    fun estimateSpeedMps(
        prevPoint: GeoPoint,
        prevTimestampMs: Long,
        currentPoint: GeoPoint,
        currentTimestampMs: Long
    ): Double {
        val deltaSeconds = (currentTimestampMs - prevTimestampMs) / 1000.0
        if (deltaSeconds <= 0.0) return 0.0
        val distanceMeters = haversineMeters(prevPoint, currentPoint)
        return max(0.0, distanceMeters / deltaSeconds)
    }
}
