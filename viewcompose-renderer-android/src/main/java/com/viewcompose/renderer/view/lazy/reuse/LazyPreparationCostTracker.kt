package com.viewcompose.renderer.view.lazy.reuse

/** Learns whether one content type is cheap enough for synchronous speculative preparation. */
internal class LazyPreparationCostTracker(
    private val budgetNanos: Long = 4_000_000L,
) {
    private val costs = mutableMapOf<MountedTreeReuseCache.ReuseKey, Long>()

    fun shouldPrepare(key: MountedTreeReuseCache.ReuseKey): Boolean {
        return costs[key]?.let { it <= budgetNanos } == true
    }

    fun record(
        key: MountedTreeReuseCache.ReuseKey,
        elapsedNanos: Long,
    ) {
        val previous = costs[key]
        costs[key] = if (previous == null) {
            elapsedNanos
        } else {
            (previous * 3L + elapsedNanos) / 4L
        }
    }

    fun clear() {
        costs.clear()
    }
}
