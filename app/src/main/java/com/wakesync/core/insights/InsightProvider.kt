package com.wakesync.core.insights

/**
 * Thread-safe registry and provider for [InsightContract].
 *
 * Initialized by the Application composition root and consumed by com.wakesync.ui
 * without concrete module coupling to com.wakesync.insights.
 */
object InsightProvider {

    @Volatile
    private var instance: InsightContract? = null

    /**
     * Registers the singleton [InsightContract] instance.
     */
    fun register(contract: InsightContract) {
        instance = contract
    }

    /**
     * Retrieves the registered [InsightContract] instance, or null if not registered.
     */
    fun get(): InsightContract? = instance

    /**
     * Resets the singleton instance for isolated test execution.
     */
    internal fun resetForTesting() {
        instance = null
    }
}
