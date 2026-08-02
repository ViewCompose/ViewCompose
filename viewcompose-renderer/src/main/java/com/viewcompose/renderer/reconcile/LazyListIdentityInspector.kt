package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.LazyListItem

/**
 * Static identity analysis for one immutable lazy-item snapshot.
 *
 * @property missingKeyIndexes zero-based item indexes whose key is `null`
 * @property duplicateKeys distinct non-null keys that occur more than once, in encounter order
 */
data class LazyListIdentityAnalysis(
    val missingKeyIndexes: List<Int>,
    val duplicateKeys: List<Any>,
) {
    /** Returns whether every item has a non-null key that is unique within the snapshot. */
    val supportsKeyedDiff: Boolean
        get() = missingKeyIndexes.isEmpty() && duplicateKeys.isEmpty()

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
        val parts = buildList {
            if (missingKeyIndexes.isNotEmpty()) {
                add("missing keys at indexes $missingKeyIndexes")
            }
            if (duplicateKeys.isNotEmpty()) {
                add("duplicate keys $duplicateKeys")
            }
        }
        return "LazyColumn $listName cannot use keyed diff: ${parts.joinToString()}"
    }
}

/** Checks lazy items for the stable, unique identities required by keyed diffing. */
object LazyListIdentityInspector {
    /**
     * Collects missing-key indexes and duplicate keys.
     *
     * @sample com.viewcompose.renderer.samples.lazyListIdentitySample
     * @param items immutable item snapshot to inspect without invoking item content
     * @return identity analysis preserving source index and key encounter order
     */
    fun analyze(items: List<LazyListItem>): LazyListIdentityAnalysis {
        val missingKeyIndexes = items.mapIndexedNotNull { index, item ->
            index.takeIf { item.key == null }
        }
        val duplicateKeys = items
            .mapNotNull { it.key }
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .toList()
        return LazyListIdentityAnalysis(
            missingKeyIndexes = missingKeyIndexes,
            duplicateKeys = duplicateKeys,
        )
    }
}
