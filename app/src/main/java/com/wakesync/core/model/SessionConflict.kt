package com.wakesync.core.model

/**
 * Encapsulates a mutual exclusion conflict when a user requests starting a new mode
 * while another mode is already active.
 *
 * @property runningSession The currently active session type.
 * @property requestedSession The session type the user attempted to launch.
 */
data class SessionConflict(
    val runningSession: SessionType,
    val requestedSession: SessionType
)
