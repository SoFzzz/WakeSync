package com.wakesync.core.model

/**
 * Represents the active operating mode in WakeSync.
 * Mutual exclusion guarantees at most one mode is active at any time.
 */
enum class SessionType {
    NONE,
    NAP,
    TRANSIT
}

/**
 * Represents the physiological rest state estimated by the heuristic AI.
 * Follows the mandatory migration: AWAKE, LIGHT_REST, DEEP_REST.
 */
enum class RestState {
    AWAKE,
    LIGHT_REST,
    DEEP_REST,
    CALIBRATING,
    SENSOR_UNAVAILABLE,
    UNKNOWN
}


/**
 * Possible termination outcomes for a session.
 */
enum class SessionOutcome {
    COMPLETED,
    INTERRUPTED_BY_ARRIVAL,
    TIMED_OUT,
    CANCELLED
}

/**
 * Lifecycle phases for a Nap (MicroNap) session.
 */
enum class NapPhase {
    IDLE,
    CALIBRATING,
    MONITORING,
    REST_CONFIRMED,
    ALERTING,
    COMPLETED,
    TIMED_OUT,
    CANCELLED
}

/**
 * Lifecycle phases for a Transit (TransitNudge) session.
 */
enum class TransitPhase {
    IDLE,
    TRACKING,
    APPROACHING,
    ALERTING,
    COMPLETED,
    CANCELLED
}
