package com.wakesync.core.alerts

import com.wakesync.core.model.AlertLevel
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract interface for haptic alert controllers.
 * Enables com.wakesync.alerts to bind with core without core importing alerts (Dependency Inversion).
 *
 * Adheres to wakesync-architecture boundary: core imports from —.
 */
interface AlertControllerContract {
    /**
     * Observable flow of the active vibration level dispatched by the controller.
     */
    val activeAlertLevel: StateFlow<AlertLevel>

    /**
     * Commands an alert level vibration.
     *
     * @param level Target alert level (SOFT, MODERATE, URGENT).
     */
    fun triggerAlert(level: AlertLevel)

    /**
     * Immediately cancels active vibration waveforms.
     */
    fun cancelAlert()
}
