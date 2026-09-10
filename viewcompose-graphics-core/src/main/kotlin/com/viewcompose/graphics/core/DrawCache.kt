package com.viewcompose.graphics.core

/**
 * Retains one build result, including null, under one equality-based key.
 *
 * A new key replaces the previous entry. `null` is a valid key and a valid cached result. The cache is
 * not synchronized and should remain confined to one render thread or externally synchronized
 * build flow. It does not observe state or invalidate itself when captured values change.
 *
 * @sample com.viewcompose.graphics.core.samples.drawCacheSample
 */
class DrawCache<T> {
    private var cachedKey: Any? = null
    private var cachedValue: T? = null
    private var initialized = false

    /** Removes the current key and value so the next [getOrBuild] call invokes its builder. */
    fun clear() {
        cachedKey = null
        cachedValue = null
        initialized = false
    }

    /**
     * Returns the cached value for an equal [key], or builds and replaces the single entry.
     *
     * [builder] executes synchronously on the caller's thread. If it throws, the previous cache entry
     * remains unchanged and the exception propagates. A successful null result is reused until the
     * key changes or [clear] is called.
     *
     * @param key equality-based semantic input for the build result
     * @param builder value producer invoked on a miss
     * @return cached or newly built value
     */
    fun getOrBuild(
        key: Any?,
        builder: () -> T,
    ): T {
        val cached = cachedValue
        if (initialized && cachedKey == key) {
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
        val rebuilt = builder()
        cachedKey = key
        cachedValue = rebuilt
        initialized = true
        return rebuilt
    }
}
