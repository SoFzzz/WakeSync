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
import com.wakesync.core.alerts.AlertControllerProvider
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.WakeSyncState
import com.wakesync.core.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service hosting the core background execution of WakeSync.
 * Configured with health and location foreground service types, and START_STICKY resilience.
 *
 * The service owns its own teardown: once no session is active and no haptic alert is still
 * playing (see [isIdle]), it removes its notification and stops itself, so it never lingers in the
 * foreground (and drains battery) between sessions. A later session start simply recreates it.
 */
class WakeSyncForegroundService : LifecycleService() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var sessionManager: SessionManager
    private var notificationManager: NotificationManager? = null

    /** Most recent start id; passed to [stopSelfResult] so a newer start request is never dropped. */
    private var lastStartId: Int = 0

    /** False until the first start command, so the initial (still idle) state never stops the service. */
    private var hasReceivedStartCommand: Boolean = false

    /** True once a stop has been accepted; suppresses further notification updates. */
    private var isStopping: Boolean = false

    companion object {
        private const val TAG = "WakeSyncService"

        const val ACTION_START_NAP = "com.wakesync.core.action.START_NAP"
        const val ACTION_START_TRANSIT = "com.wakesync.core.action.START_TRANSIT"
        const val ACTION_STOP_SESSION = "com.wakesync.core.action.STOP_SESSION"

        /**
         * True when the service has nothing left to do: no session is active and neither the
         * reactive state nor the alert controller itself reports a playing alert. The controller
         * is checked directly because it sets its level synchronously before a session ends with
         * `stopActiveAlert = false`, while [WakeSyncState.activeAlertLevel] mirrors it
         * asynchronously; relying on the state alone could stop the service (whose [onDestroy]
         * cancels the alert) in the middle of the MODERATE arrival pattern or the URGENT alarm.
         *
         * @param state Current reactive app state.
         * @param controllerLevel Level reported by the registered alert controller, or null if none.
         */
        internal fun isIdle(state: WakeSyncState, controllerLevel: AlertLevel?): Boolean =
            state.sessionType == SessionType.NONE &&
                state.activeAlertLevel == AlertLevel.NONE &&
                (controllerLevel ?: AlertLevel.NONE) == AlertLevel.NONE

        /**
         * Starts (or delivers a command to) the foreground service.
         *
         * @param context Any context; the application context is used by the system.
         * @param action One of [ACTION_START_NAP], [ACTION_START_TRANSIT] or [ACTION_STOP_SESSION].
         */
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
        observeIdleForSelfStop()
        Log.i(TAG, "WakeSyncForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lastStartId = startId
        hasReceivedStartCommand = true

        when (intent?.action) {
            ACTION_START_NAP -> sessionManager.requestStartSession(SessionType.NAP)
            ACTION_START_TRANSIT -> sessionManager.requestStartSession(SessionType.TRANSIT)
            ACTION_STOP_SESSION -> sessionManager.endSession(SessionOutcome.CANCELLED)
        }
        // A sticky restart (null intent) after process death finds no session: stop right away.
        stopIfIdle()

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
                if (isStopping) return@collectLatest
                val updatedNotification = notificationHelper.buildNotification(state)
                notificationManager?.notify(NotificationHelper.NOTIFICATION_ID, updatedNotification)
            }
        }
    }

    /**
     * Re-evaluates [isIdle] whenever the session state or the alert controller level changes, so
     * the service stops once the session has ended AND any trailing alert has finished.
     */
    private fun observeIdleForSelfStop() {
        val controllerLevel = AlertControllerProvider.get()?.activeAlertLevel ?: MutableStateFlow(AlertLevel.NONE)
        lifecycleScope.launch {
            combine(sessionManager.state, controllerLevel) { _, _ -> Unit }.collect { stopIfIdle() }
        }
    }

    /**
     * Stops the service when [isIdle] holds for the current values. Uses [stopSelfResult] with the
     * latest start id: if a new session's start request is already queued, the system refuses the
     * stop and the service keeps running in the foreground for that session.
     */
    private fun stopIfIdle() {
        if (!hasReceivedStartCommand || isStopping) return
        val controllerLevel = AlertControllerProvider.get()?.activeAlertLevel?.value
        if (!isIdle(sessionManager.state.value, controllerLevel)) return
        if (!stopSelfResult(lastStartId)) {
            Log.i(TAG, "Self-stop skipped: a newer start request is pending")
            return
        }
        isStopping = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "No active session or alert; stopping foreground service")
    }

    override fun onDestroy() {
        // Only reached once idle (alert already NONE) or when the system kills the service.
        sessionManager.cancelAlert()
        notificationManager?.cancel(NotificationHelper.NOTIFICATION_ID)
        super.onDestroy()
        Log.i(TAG, "WakeSyncForegroundService destroyed")
    }
}
