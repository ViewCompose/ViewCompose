package com.viewcompose.renderer.view.lazy.reuse

/**
 * Learns whether one content type is cheap enough for synchronous speculative preparation.
 *
 * A cold activation is retained only as a conservative bootstrap upper bound because it also
 * includes commit and effect work. Once detached preparation is observed, its estimate is
 * authoritative. An expensive bootstrap deliberately disables probing rather than risking an
 * unbounded first speculative preparation during a fling.
 */
internal class LazyPreparationCostTracker(
    private val budgetNanos: Long = 4_000_000L,
) {
    private val bootstrapUpperBounds = mutableMapOf<MountedTreeReuseCache.ReuseKey, Long>()
    private val preparationCosts = mutableMapOf<MountedTreeReuseCache.ReuseKey, Long>()

    fun shouldPrepare(key: MountedTreeReuseCache.ReuseKey): Boolean {
        return estimatedCostNanos(key)?.let { it <= budgetNanos } == true
    }

    fun recordBootstrapUpperBound(
        key: MountedTreeReuseCache.ReuseKey,
        elapsedNanos: Long,
    ) {
        if (preparationCosts.containsKey(key)) return
        updateTailEstimate(bootstrapUpperBounds, key, elapsedNanos)
    }

    fun recordPreparation(
        key: MountedTreeReuseCache.ReuseKey,
        elapsedNanos: Long,
    ) {
        bootstrapUpperBounds.remove(key)
        updateTailEstimate(preparationCosts, key, elapsedNanos)
    }

    internal fun estimatedCostNanos(key: MountedTreeReuseCache.ReuseKey): Long? =
        preparationCosts[key] ?: bootstrapUpperBounds[key]

    internal fun hasBootstrapUpperBound(key: MountedTreeReuseCache.ReuseKey): Boolean =
        bootstrapUpperBounds.containsKey(key)

    fun clear() {
        bootstrapUpperBounds.clear()
        preparationCosts.clear()
    }

    private fun updateTailEstimate(
        target: MutableMap<MountedTreeReuseCache.ReuseKey, Long>,
        key: MountedTreeReuseCache.ReuseKey,
        elapsedNanos: Long,
    ) {
        val previous = target[key]
        target[key] = if (previous == null) {
            elapsedNanos
        } else if (elapsedNanos > previous) {
            // Hold the peak instead of averaging one tail-producing preparation away. Cheaper
            // samples decay an estimate only while it remains eligible; crossing the budget
            // deliberately fuses speculative preparation off until this adapter is disposed.
            elapsedNanos
        } else {
            (previous * 3L + elapsedNanos) / 4L
        }
    }
}
