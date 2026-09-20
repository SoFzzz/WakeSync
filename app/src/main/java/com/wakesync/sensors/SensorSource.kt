package com.wakesync.sensors

import com.wakesync.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow

/**
 * Unified sensor abstraction defining reactive data streams for physiological and location data.
 * Implemented by both real hardware providers and simulation engines.
 */
interface SensorSource {
    /**
     * Emits heart rate readings in beats per minute (BPM).
     * Sampling frequency: 0.5 - 1.0 Hz.
     */
    fun getHeartRate(): Flow<Int>

    /**
     * Emits Signal Vector Magnitude (SVM) in m/s² derived from triaxial accelerometer.
     * Sampling frequency: 20 Hz.
     */
    fun getMotionSvm(): Flow<Float>

    /**
     * Emits geographic location updates as [GeoPoint].
     */
    fun getLocation(): Flow<GeoPoint>
}
