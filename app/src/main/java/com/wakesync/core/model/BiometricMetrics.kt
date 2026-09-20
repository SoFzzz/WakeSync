package com.wakesync.core.model

/**
 * Biometric indicators collected and calculated by sensor-ai-engineer.
 *
 * @property currentHeartRate Current pulse in beats per minute (BPM).
 * @property baseHeartRate Basal pulse calibrated during the initial session phase.
 * @property motionSvm Signal Vector Magnitude derived from triaxial accelerometer (m/s²).
 * @property restScore Calculated rest index in range [0.0, 1.0].
 * @property restState Current physiological rest categorization.
 */
data class BiometricMetrics(
    val currentHeartRate: Int? = null,
    val baseHeartRate: Int? = null,
    val motionSvm: Float? = null,
    val restScore: Float? = null,
    val restState: RestState = RestState.UNKNOWN
)
