package com.wakesync.core.places

/**
 * Thread-safe registry and provider for [DestinationSearchContract].
 *
 * Initialized by the Application composition root and consumed by com.wakesync.ui
 * without concrete module coupling to com.wakesync.places.
 */
object DestinationSearchProvider {

    @Volatile
    private var instance: DestinationSearchContract? = null

    /**
     * Registers the singleton [DestinationSearchContract] instance.
     */
    fun register(contract: DestinationSearchContract) {
        instance = contract
    }

    /**
     * Retrieves the registered [DestinationSearchContract] instance, or null if not registered.
     */
    fun get(): DestinationSearchContract? = instance

    /**
     * Resets the singleton instance for isolated test execution.
     */
    internal fun resetForTesting() {
        instance = null
    }
}
