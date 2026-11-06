package com.wakesync.core.geo

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.permission.PermissionManager

/**
 * Synchronous, one-shot reader of the device's last known position (CR-01, RF-PLC-04).
 *
 * Used by the destination picker to compute the straight-line distance to a candidate
 * destination and to bias search/map centering. It never starts location updates: it only
 * reads the cached fix of the GPS, fused and network providers and returns the freshest one.
 * Returns null when the permission is missing, no provider has a cached fix, or the read fails,
 * so callers can degrade gracefully (e.g. omit the distance row).
 *
 * Coordinates are never logged (AGENTS.md privacy guardrails).
 */
class DeviceLocationProvider(context: Context) {

    companion object {
        private const val TAG = "WakeSyncService"

        /** `LocationManager.FUSED_PROVIDER` is API 31+; the literal keeps minSdk 30 compatible. */
        private const val FUSED_PROVIDER = "fused"

        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)

        /**
         * Picks the most recent fix among [fixes], or null when the list is empty.
         *
         * @param fixes Candidate positions paired with their UTC fix time in milliseconds.
         */
        internal fun selectFreshest(fixes: List<Pair<GeoPoint, Long>>): GeoPoint? =
            fixes.maxByOrNull { it.second }?.first
    }

    private val appContext = context.applicationContext

    /**
     * Returns the freshest cached device position, or null when unavailable.
     * Safe to call from the main thread: `getLastKnownLocation` does not perform I/O.
     */
    fun lastKnownLocation(): GeoPoint? {
        if (!PermissionManager.hasLocationPermission(appContext)) {
            return null
        }
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fixes = PROVIDERS.mapNotNull { provider -> readProvider(manager, provider) }
            .map { location -> GeoPoint(location.latitude, location.longitude) to location.time }
        return selectFreshest(fixes)
    }

    private fun readProvider(manager: LocationManager, provider: String): Location? =
        try {
            manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied while reading last known location: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Location provider '$provider' unavailable: ${e.message}")
            null
        }
}
