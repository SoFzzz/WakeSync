package com.wakesync.core.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.session.SessionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service hosting the core background execution of WakeSync.
 * Configured with health and location foreground service types, and START_STICKY resilience.
 */
class WakeSyncForegroundService : LifecycleService() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var sessionManager: SessionManager
    private var notificationManager: NotificationManager? = null

    companion object {
        private const val TAG = "WakeSyncService"

        const val ACTION_START_NAP = "com.wakesync.core.action.START_NAP"
        const val ACTION_START_TRANSIT = "com.wakesync.core.action.START_TRANSIT"
        const val ACTION_STOP_SESSION = "com.wakesync.core.action.STOP_SESSION"

        fun startService(context: Context, action: String) {
            val intent = Intent(context, WakeSyncForegroundService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        sessionManager = SessionManager.getInstance(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        startInForeground()
        observeStateForNotifications()
        Log.i(TAG, "WakeSyncForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_NAP -> sessionManager.requestStartSession(SessionType.NAP)
            ACTION_START_TRANSIT -> sessionManager.requestStartSession(SessionType.TRANSIT)
            ACTION_STOP_SESSION -> sessionManager.endSession(SessionOutcome.CANCELLED)
        }

        // RNF-CORE-02: Ensure automatic restart if killed by OS under memory pressure
        return START_STICKY
    }

    private fun startInForeground() {
        val initialNotification = notificationHelper.buildNotification(sessionManager.state.value)

        val foregroundTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            initialNotification,
            foregroundTypes
        )
    }

    private fun observeStateForNotifications() {
        lifecycleScope.launch {
            sessionManager.state.collectLatest { state ->
                val updatedNotification = notificationHelper.buildNotification(state)
                notificationManager?.notify(NotificationHelper.NOTIFICATION_ID, updatedNotification)
            }
        }
    }

    override fun onDestroy() {
        sessionManager.cancelAlert()
        super.onDestroy()
        Log.i(TAG, "WakeSyncForegroundService destroyed")
    }
}
