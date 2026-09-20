package com.wakesync.core.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Manages the acquisition and safe release of Android's PARTIAL_WAKE_LOCK.
 *
 * Responsibilities are strictly limited to CPU power management.
 * Business logic (such as session timeouts or state transitions) is completely decoupled
 * and managed by SessionManager and mode-flow-engineer.
 */
class WakeLockManager(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val lock = Any()

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "WakeLockManager"
        private const val WAKE_LOCK_TAG = "WakeSync::SessionWakeLock"

        /**
         * Domain-anchored hardware safety timeout:
         * Worst-case Nap duration = 25 min (max pre-rest monitoring) + 15 min (nap countdown)
         * + 5 min (safety buffer for progressive alert escalation and user dismissal) = 45 min.
         *
         * Prevents indefinite battery drain if the process dies unexpectedly without normal cleanup.
         */
        const val MAX_HARDWARE_WAKE_LOCK_TIMEOUT_MS: Long = 45 * 60 * 1000L
    }

    /**
     * Acquires the PARTIAL_WAKE_LOCK with the domain-anchored safety timeout.
     * Safe to call multiple times; redundant calls when already held are ignored.
     */
    fun acquireWakeLock() {
        synchronized(lock) {
            try {
                if (wakeLock == null) {
                    wakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        WAKE_LOCK_TAG
                    )?.apply {
                        setReferenceCounted(false)
                    }
                }

                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(MAX_HARDWARE_WAKE_LOCK_TIMEOUT_MS)
                    Log.d(TAG, "PARTIAL_WAKE_LOCK acquired with safety timeout: ${MAX_HARDWARE_WAKE_LOCK_TIMEOUT_MS}ms")
                } else {
                    Log.d(TAG, "PARTIAL_WAKE_LOCK already held; skipping acquire")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while acquiring wake lock: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error acquiring wake lock: ${e.message}", e)
            }
        }
    }

    /**
     * Safely releases the PARTIAL_WAKE_LOCK if currently held.
     */
    fun releaseWakeLock() {
        synchronized(lock) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    Log.d(TAG, "PARTIAL_WAKE_LOCK successfully released")
                } else {
                    Log.d(TAG, "PARTIAL_WAKE_LOCK was not held; no action taken")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if the wake lock is currently active and held.
     */
    val isHeld: Boolean
        get() = synchronized(lock) {
            wakeLock?.isHeld == true
        }
}
