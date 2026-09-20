package com.wakesync.core.model

/**
 * Haptic alert levels defining progressive vibration patterns according to haptic-waveform-spec.
 *
 * Trigger mapping:
 * - [NONE]: Idle state of the StateFlow, no vibration active.
 * - [SOFT]: Level 1 — Rest confirmation (DEEP_REST) or pre-warning (heartbeat pattern).
 * - [MODERATE]: Level 2 — Arrival at TransitNudge dynamic geofence (ascending rhythmic pattern).
 * - [URGENT]: Level 3 — End of nap (15 min) or critical alert requiring active user dismissal (repeating double pulse).
 */
enum class AlertLevel {
    NONE,
    SOFT,
    MODERATE,
    URGENT
}
