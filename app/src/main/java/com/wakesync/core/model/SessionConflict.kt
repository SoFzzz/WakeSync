package com.wakesync.core.model

/**
 * Encapsulates a mutual exclusion conflict when a user requests starting a new mode
 * while another mode is already active.
 *
 * @property runningSession The currently active session type.
 * @property requestedSession The session type the user attempted to launch.
 * @property requestedDestination Destination confirmed for the requested session, carried over so
 *                                it is not lost if the user accepts cancelling the running one.
 */
data class SessionConflict(
    val runningSession: SessionType,
    val requestedSession: SessionType,
    val requestedDestination: GeoPoint? = null
)
