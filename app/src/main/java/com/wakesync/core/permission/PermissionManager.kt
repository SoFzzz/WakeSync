package com.wakesync.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Internal utility for verifying runtime permissions required by WakeSync on Wear OS (RF-CORE-04).
 * Kept internal to com.wakesync.core so UI only consumes missingPermissions via WakeSyncState.
 */
internal object PermissionManager {

    const val DEFAULT_REQUEST_CODE = 1001

    val REQUIRED_PERMISSIONS: List<String> = buildList {
        add(Manifest.permission.BODY_SENSORS)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.WAKE_LOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Requests missing permissions using an Activity context.
     * Note: In Jetpack Compose (Fase 4), wear-ui-architect can also use
     * ActivityResultContracts.RequestMultiplePermissions() with REQUIRED_PERMISSIONS.
     */
    fun requestPermissions(activity: Activity, requestCode: Int = DEFAULT_REQUEST_CODE) {
        val missing = getMissingPermissions(activity)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        }
    }

    /**
     * Checks if all required permissions are currently granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }

    /**
     * Returns a list of permissions that have not yet been granted.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the BODY_SENSORS runtime permission is granted.
     */
    fun hasBodySensorsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the ACCESS_FINE_LOCATION runtime permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the POST_NOTIFICATIONS runtime permission is granted (or true if below API 33).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
