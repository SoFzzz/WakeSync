package com.wakesync

import android.app.Application
import android.util.Log
import com.wakesync.alerts.HapticVibrationController
import com.wakesync.core.alerts.AlertControllerProvider
import com.wakesync.core.session.SessionManager

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
    }
}
