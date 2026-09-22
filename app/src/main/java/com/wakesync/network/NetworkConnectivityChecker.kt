package com.wakesync.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Interface to check for active validated internet connectivity (RF-NET-02).
 */
interface NetworkConnectivityChecker {
    /**
     * Returns true if there is an active network with [NetworkCapabilities.NET_CAPABILITY_VALIDATED].
     */
    fun isNetworkValidated(): Boolean
}

/**
 * Real implementation querying system [ConnectivityManager].
 */
class DefaultNetworkConnectivityChecker(private val context: Context) : NetworkConnectivityChecker {

    override fun isNetworkValidated(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
