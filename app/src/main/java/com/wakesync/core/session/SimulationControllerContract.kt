package com.wakesync.core.session

import com.wakesync.core.model.GeoPoint

/**
 * Contract decoupling UI and SessionManager from the concrete simulation engine implementation.
 * Allows com.wakesync.ui to trigger simulation runs strictly via SessionManager without
 * violating module boundaries (wakesync-architecture).
 */
interface SimulationControllerContract {
    fun startSimulateNap()
    fun startSimulateRoute(destination: GeoPoint?)
}
