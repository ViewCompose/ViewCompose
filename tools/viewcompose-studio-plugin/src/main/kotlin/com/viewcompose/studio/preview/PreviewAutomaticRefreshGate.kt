package com.viewcompose.studio.preview

/**
 * Coalesces automatic refreshes without cancelling an expensive Gradle/Layoutlib run in flight.
 * User-driven requests may still supersede work immediately; save events collapse to the latest.
 */
internal class PreviewAutomaticRefreshGate<T> {
    private var activeGeneration: Long? = null
    private var pending: T? = null

    @Synchronized
    fun deferIfActive(request: T): Boolean {
        if (activeGeneration == null) return false
        pending = request
        return true
    }

    @Synchronized
    fun markActive(generation: Long) {
        activeGeneration = generation
    }

    @Synchronized
    fun supersede(generation: Long) {
        pending = null
        activeGeneration = generation
    }

    @Synchronized
    fun complete(generation: Long): T? {
        if (activeGeneration != generation) return null
        activeGeneration = null
        return pending.also { pending = null }
    }

    @Synchronized
    fun clear() {
        activeGeneration = null
        pending = null
    }
}
