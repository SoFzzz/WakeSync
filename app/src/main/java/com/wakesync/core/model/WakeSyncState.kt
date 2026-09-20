package com.wakesync.core.model

/**
 * Immutable system state representing the single source of truth for the entire WakeSync app.
 * Produced exclusively by com.wakesync.core (SessionManager / WakeSyncForegroundService)
 * and observed by sensor-ai-engineer, mode-flow-engineer, and wear-ui-architect.
 *
 * @property sessionType The currently active mode (NONE, NAP, or TRANSIT).
 * @property napState Detailed status when operating in Nap mode.
 * @property transitState Detailed status when operating in Transit mode.
 * @property biometricMetrics Latest physiological sensor and rest estimation data.
 * @property activeAlertLevel Currently commanded haptic alert level.
 * @property pendingConflict Holds conflict details if an overlapping session start was requested.
 * @property isSimulated True if synthetic sensor/route data is currently driving the session.
 * @property missingPermissions List of required permissions that have not yet been granted by the user.
 */
data class WakeSyncState(
    val sessionType: SessionType = SessionType.NONE,
    val napState: NapSessionState = NapSessionState(),
    val transitState: TransitSessionState = TransitSessionState(),
    val biometricMetrics: BiometricMetrics = BiometricMetrics(),
    val activeAlertLevel: AlertLevel = AlertLevel.NONE,
    val pendingConflict: SessionConflict? = null,
    val isSimulated: Boolean = false,
    val missingPermissions: List<String> = emptyList()
)
