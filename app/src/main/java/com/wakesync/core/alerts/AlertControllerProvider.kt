package com.wakesync.core.alerts

/**
 * Thread-safe registry and provider for [AlertControllerContract].
 *
 * Initialized by the Application composition root and consumed by core, sleep, and transit
 * to obtain the haptic alert controller without concrete module coupling.
 */
object AlertControllerProvider {

    @Volatile
    private var instance: AlertControllerContract? = null

    /**
     * Registers the singleton [AlertControllerContract] instance.
     */
    fun register(controller: AlertControllerContract) {
        instance = controller
    }

    /**
     * Retrieves the registered [AlertControllerContract] instance, or null if not yet registered.
     */
    fun get(): AlertControllerContract? = instance

    /**
     * Resets the singleton instance for isolated test execution.
     */
    internal fun resetForTesting() {
        instance = null
    }
}
