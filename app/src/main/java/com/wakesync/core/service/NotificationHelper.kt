package com.wakesync.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.model.WakeSyncState

/**
 * Manages notification channel registration and creates ongoing notifications
 * for WakeSyncForegroundService.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    companion object {
        const val CHANNEL_ID = "wakesync_foreground_channel"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_NAME = "WakeSync Active Session"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the system notification channel required on Android 8.0+ (API 26+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active Nap or Transit tracking session status"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Builds an ongoing, non-dismissible notification reflecting the current system state.
     */
    fun buildNotification(state: WakeSyncState): Notification {
        val title = when (state.sessionType) {
            SessionType.NAP -> "WakeSync: MicroNap"
            SessionType.TRANSIT -> "WakeSync: TransitNudge"
            SessionType.NONE -> "WakeSync"
        }

        val contentText = when (state.sessionType) {
            SessionType.NAP -> formatNapStatus(state)
            SessionType.TRANSIT -> formatTransitStatus(state)
            SessionType.NONE -> "Session standby"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun formatNapStatus(state: WakeSyncState): String {
        return when (state.napState.phase) {
            NapPhase.CALIBRATING -> "Calibrating basal heart rate..."
            NapPhase.MONITORING -> "Monitoring rest (Score: ${formatScore(state.biometricMetrics.restScore)})"
            NapPhase.REST_CONFIRMED -> "Deep rest confirmed: ${formatTime(state.napState.remainingNapSeconds)} left"
            NapPhase.ALERTING -> "Wake up alert active!"
            NapPhase.COMPLETED -> "Nap completed"
            NapPhase.TIMED_OUT -> "Nap timed out without deep rest"
            NapPhase.CANCELLED -> "Nap cancelled"
            NapPhase.IDLE -> "Standby"
        }
    }

    private fun formatTransitStatus(state: WakeSyncState): String {
        val distance = state.transitState.currentDistanceMeters
        val destinationName = state.confirmedDestination?.name ?: "destination"
        return when (state.transitState.phase) {
            TransitPhase.TRACKING -> {
                if (distance != null) "Approaching $destinationName: ${distance.toInt()}m"
                else "Tracking location..."
            }
            TransitPhase.APPROACHING -> "Near $destinationName: ${distance?.toInt() ?: 0}m"
            TransitPhase.ALERTING -> "$destinationName reached! Wake up alert active"
            TransitPhase.COMPLETED -> "Arrived at $destinationName"
            TransitPhase.CANCELLED -> "Transit tracking cancelled"
            TransitPhase.IDLE -> "Standby"
        }
    }

    private fun formatScore(score: Float?): String =
        if (score != null) String.format("%.2f", score) else "--"

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
