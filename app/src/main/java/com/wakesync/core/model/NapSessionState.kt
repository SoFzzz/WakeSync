package com.wakesync.core.model

/**
 * State representation specific to the Nap (MicroNap) operating mode.
 *
 * @property phase Current progression phase of the nap session.
 * @property elapsedSeconds Total seconds elapsed since session start.
 * @property remainingNapSeconds Remaining countdown seconds once DEEP_REST is confirmed.
 * @property restLatencySeconds Seconds elapsed from start until DEEP_REST confirmation, if achieved.
 * @property destination Optional geographic target when resting on transit.
 * @property distanceToDestinationMeters Real-time distance to optional destination in meters.
 */
data class NapSessionState(
    val phase: NapPhase = NapPhase.IDLE,
    val elapsedSeconds: Int = 0,
    val remainingNapSeconds: Int = 0,
    val restLatencySeconds: Int? = null,
    val destination: GeoPoint? = null,
    val distanceToDestinationMeters: Float? = null
)
