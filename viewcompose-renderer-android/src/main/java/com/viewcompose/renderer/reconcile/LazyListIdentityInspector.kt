package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.LazyListItem

/**
 * Static identity analysis for one immutable lazy-item snapshot.
 *
 * @property duplicateKeys distinct keys that occur more than once, in encounter order
 */
data class LazyListIdentityAnalysis(
    val duplicateKeys: List<Any>,
) {
    /** Returns whether every item key is unique within the snapshot. */
    val supportsKeyedDiff: Boolean
        get() = duplicateKeys.isEmpty()

    /**
     * Returns a diagnostic summary of missing and duplicate identities.
     *
     * @param listName human-readable collection name included in the message
     * @return `null` when keyed diff is supported, otherwise a stable English warning
     */
    fun warningMessage(listName: String): String? {
        if (supportsKeyedDiff) {
            return null
        }
        return "LazyColumn $listName cannot use keyed diff: duplicate keys $duplicateKeys"
    }
}

/** Checks lazy items for the stable, unique identities required by keyed diffing. */
object LazyListIdentityInspector {
    /**
     * Collects duplicate keys.
     *
     * @sample com.viewcompose.renderer.samples.lazyListIdentitySample
     * @param items immutable item snapshot to inspect without invoking item content
     * @return identity analysis preserving source index and key encounter order
     */
    fun analyze(items: List<LazyListItem>): LazyListIdentityAnalysis {
        val duplicateKeys = items
            .map(LazyListItem::key)
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .toList()
        return LazyListIdentityAnalysis(
            duplicateKeys = duplicateKeys,
        )
    }
}
