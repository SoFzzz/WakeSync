package com.wakesync

import android.app.Application
import android.util.Log
import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.insights.InsightProvider
import com.wakesync.core.places.DestinationSearchProvider
import com.wakesync.insights.InsightRepository
import com.wakesync.network.BackendClient
import com.wakesync.places.DestinationSearchRepository

/**
 * Main Application class serving as the Composition Root for WakeSync.
 *
 * Located in root package com.wakesync to coordinate module assembly without violating
 * internal module boundaries defined in wakesync-architecture.
 */
class WakeSyncApplication : Application() {

    companion object {
        private const val TAG = "WakeSyncApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WakeSyncApplication initialized")

        val coordinator = AppSessionCoordinator.getInstance(this)
        coordinator.start()

        // Phase 4b Connectivity Registrations (CR-01, RF-CORE-07, RF-NET-01, RF-PLC-01, RF-INS-01)
        val backendClient = BackendClient(this)
        val destinationRepository = DestinationSearchRepository(
            backendClient = backendClient,
            currentLocationProvider = { coordinator.sessionManager.state.value.transitState.destination }
        )
        val sessionHistoryRepository = SessionHistoryRepository(this)
        val insightRepository = InsightRepository(
            backendClient = backendClient,
            sessionHistoryRepository = sessionHistoryRepository
        )

        DestinationSearchProvider.register(destinationRepository)
        InsightProvider.register(insightRepository)
        Log.i(TAG, "Registered DestinationSearchProvider and InsightProvider contracts")
    }
}
