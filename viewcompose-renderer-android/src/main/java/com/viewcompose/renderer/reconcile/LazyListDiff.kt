package com.viewcompose.renderer.reconcile

import androidx.annotation.DoNotInline
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.viewcompose.ui.node.LazyListItem

/** Incremental update consumed in order by a lazy-list adapter. */
sealed interface LazyListUpdate {
    /**
     * Inserts one item at [index].
     *
     * @property index zero-based insertion index in the list state at this point in the sequence
     */
    data class Insert(
        val index: Int,
    ) : LazyListUpdate

    /**
     * Removes one item at [index].
     *
     * @property index zero-based removal index in the list state at this point in the sequence
     */
    data class Remove(
        val index: Int,
    ) : LazyListUpdate

    /**
     * Moves one existing item without recreating its holder.
     *
     * @property fromIndex zero-based source index before this operation
     * @property toIndex zero-based destination index after this operation
     */
    data class Move(
        val fromIndex: Int,
        val toIndex: Int,
    ) : LazyListUpdate

    /**
     * Rebinds one existing item in place.
     *
     * @property index zero-based item index at this point in the sequence
     * @property payload optional targeted-change description; `null` requests a full item bind
     */
    data class Change(
        val index: Int,
        val payload: Any? = null,
    ) : LazyListUpdate

    /** Replaces all adapter items because stable keyed diffing is not possible. */
    data object ReloadAll : LazyListUpdate
}

/** Targeted payload delivered when lazy-item identity is stable but one semantic revision changes. */
sealed interface LazyListChangePayload {
    /**
     * Carries old and new content and environment revisions for an item with unchanged identity.
     *
     * @property previousContent caller-owned revision from the previous item snapshot
     * @property nextContent caller-owned revision from the next item snapshot
     * @property previousEnvironment framework-owned revision from the previous environment
     * @property nextEnvironment framework-owned revision from the next environment
     */
    data class RevisionChanged(
        /** Caller-owned content revision in the preceding snapshot. */
        val previousContent: Any?,
        /** Caller-owned content revision in the next snapshot. */
        val nextContent: Any?,
        /** Framework-owned environment revision in the preceding snapshot. */
        val previousEnvironment: Any?,
        /** Framework-owned environment revision in the next snapshot. */
        val nextEnvironment: Any?,
    ) : LazyListChangePayload

}

/** Requests physical layout or compatibility work without invalidating logical item content. */
internal data object LazyListPresentationChangedPayload

/**
 * Compact non-empty payload used only by the RecyclerView adapter.
 *
 * The logical item session reads the authoritative next [LazyListItem], so carrying per-item old
 * and new revisions through RecyclerView would allocate one payload per row and prevent AndroidX
 * from batching adjacent change notifications.
 */
internal data object LazyListAdapterChangedPayload

/** RecyclerView notification plan for one committed lazy-list snapshot. */
internal sealed interface LazyListAdapterUpdatePlan {
    /** The next snapshot is semantically equal and must retain the installed item instances. */
    data object NoChange : LazyListAdapterUpdatePlan

    /** Stable keys are ambiguous, so RecyclerView must invalidate the complete data set. */
    data object ReloadAll : LazyListAdapterUpdatePlan

    /**
     * Key order is unchanged; only these adjacent native ranges need rebinding.
     *
     * [ranges] may be empty when logical sessions must consume a semantic revision but native
     * change animation is disabled.
     */
    data class SameKeyOrderChanges(
        val ranges: List<LazyListChangedRange>,
    ) : LazyListAdapterUpdatePlan

    /**
     * A cyclic permutation represented by the smaller set of single-item native moves.
     * [changedRanges] uses positions after all moves and may be empty.
     */
    data class CyclicRotation(
        val direction: LazyListRotationDirection,
        val moveCount: Int,
        val changedRanges: List<LazyListChangedRange>,
    ) : LazyListAdapterUpdatePlan

    /** Structural keyed changes require AndroidX insertion, removal, and move reconciliation. */
    data class StructuralDiff(
        val result: DiffUtil.DiffResult,
    ) : LazyListAdapterUpdatePlan
}

/** Direction used to express a cyclic key permutation with the fewest RecyclerView moves. */
internal enum class LazyListRotationDirection {
    Left,
    Right,
}

/** One contiguous changed range in an otherwise identity-stable list. */
internal data class LazyListChangedRange(
    val positionStart: Int,
    val itemCount: Int,
)

/**
 * Complete result of reconciling two lazy-item snapshots.
 *
 * @property updates ordered adapter operations, or a single [LazyListUpdate.ReloadAll]
 * @property items exact next item instances that the adapter must retain
 * @property diffResult AndroidX result when keyed diffing succeeded, otherwise `null`
 */
data class LazyListDiffResult(
    val updates: List<LazyListUpdate>,
    val items: List<LazyListItem>,
    val diffResult: DiffUtil.DiffResult? = null,
)

/**
 * Calculates lazy-list adapter updates from stable item keys.
 *
 * A duplicate key falls back to [LazyListUpdate.ReloadAll], preventing `DiffUtil` from
 * assigning platform holder state to an ambiguous identity. Content equality includes the complete
 * [LazyListItem]; revision changes receive [LazyListChangePayload.RevisionChanged], while layout
 * metadata changes remain physical-only payloads.
 */
object LazyListDiff {
    /**
     * Calculates the lazy item update sequence from previous to next.
     *
     * This method performs synchronous CPU work and must not be called concurrently with mutation
     * of either list. Returned [LazyListDiffResult.items] always uses the exact [next] instances so
     * refreshed closures and session updaters are not lost.
     *
     * @sample com.viewcompose.renderer.samples.lazyListDiffSample
     * @param previous last adapter item snapshot in committed order
     * @param next next immutable item snapshot in target order
     * @return ordered update plan plus the exact next snapshot
     */
    fun calculate(
        previous: List<LazyListItem>,
        next: List<LazyListItem>,
    ): LazyListDiffResult {
        if (!canDiffByKey(previous, next)) {
            return LazyListDiffResult(
                updates = listOf(LazyListUpdate.ReloadAll),
                items = next,
                diffResult = null,
            )
        }

        val updates = mutableListOf<LazyListUpdate>()
        val diffResult = calculateDiff(previous, next)
        diffResult.dispatchUpdatesTo(
            RecordingLazyListUpdateCallback(
                updates = updates,
            ),
        )

        return LazyListDiffResult(
            updates = updates,
            // Always use latest item instances to preserve refreshed closures/session updaters.
            items = next,
            diffResult = diffResult,
        )
    }

    /**
     * Calculates the notification plan consumed directly by a RecyclerView adapter.
     *
     * Equal key order and same-size cyclic permutations do not need Myers diff or move detection.
     * They are classified in linear time and adjacent native changes are batched. Other structural
     * changes retain AndroidX DiffUtil semantics, while duplicate keys retain full-invalidation
     * fallback behavior. [includeSemanticChanges] preserves RecyclerView change notifications for
     * animation; disabling it still publishes the snapshot for synchronous item-session refresh.
     */
    @DoNotInline
    internal fun calculateAdapterUpdatePlan(
        previous: List<LazyListItem>,
        next: List<LazyListItem>,
        supportsKeyedDiff: Boolean,
        includeSemanticChanges: Boolean = true,
    ): LazyListAdapterUpdatePlan {
        if (previous === next) return LazyListAdapterUpdatePlan.NoChange

        var sameKeyOrder = previous.size == next.size
        var hasItemChanges = false
        var changedRangeStart = -1
        var changedRangeCount = 0
        var changedRanges: ArrayList<LazyListChangedRange>? = null

        if (sameKeyOrder) {
            var index = 0
            while (index < previous.size) {
                val oldItem = previous[index]
                val newItem = next[index]
                val sameItem = oldItem == newItem
                if (!sameItem && oldItem.key != newItem.key) {
                    sameKeyOrder = false
                    break
                }
                if (!sameItem) hasItemChanges = true
                val notifyNativeChange = !sameItem && requiresNativeChange(
                    oldItem = oldItem,
                    newItem = newItem,
                    includeSemanticChanges = includeSemanticChanges,
                )
                if (!notifyNativeChange) {
                    if (changedRangeStart >= 0) {
                        val ranges = changedRanges ?: ArrayList<LazyListChangedRange>().also {
                            changedRanges = it
                        }
                        ranges.add(LazyListChangedRange(changedRangeStart, changedRangeCount))
                        changedRangeStart = -1
                        changedRangeCount = 0
                    }
                } else if (changedRangeStart < 0) {
                    changedRangeStart = index
                    changedRangeCount = 1
                } else {
                    changedRangeCount += 1
                }
                index += 1
            }
            if (sameKeyOrder) {
                if (changedRangeStart >= 0) {
                    val ranges = changedRanges ?: ArrayList<LazyListChangedRange>().also {
                        changedRanges = it
                    }
                    ranges.add(LazyListChangedRange(changedRangeStart, changedRangeCount))
                }
                if (!hasItemChanges) {
                    return LazyListAdapterUpdatePlan.NoChange
                }
            }
        }

        if (!supportsKeyedDiff) return LazyListAdapterUpdatePlan.ReloadAll
        if (sameKeyOrder) {
            return LazyListAdapterUpdatePlan.SameKeyOrderChanges(
                ranges = changedRanges ?: emptyList(),
            )
        }
        calculateCyclicRotation(
            previous = previous,
            next = next,
            includeSemanticChanges = includeSemanticChanges,
        )?.let { rotation ->
            return rotation
        }
        return LazyListAdapterUpdatePlan.StructuralDiff(
            result = calculateDiff(
                previous = previous,
                next = next,
                compactAdapterPayload = true,
                includeSemanticChanges = includeSemanticChanges,
            ),
        )
    }

    private fun calculateCyclicRotation(
        previous: List<LazyListItem>,
        next: List<LazyListItem>,
        includeSemanticChanges: Boolean,
    ): LazyListAdapterUpdatePlan.CyclicRotation? {
        val size = previous.size
        if (size <= 1 || size != next.size) return null

        val nextFirstKey = next[0].key
        var leftRotation = 0
        while (leftRotation < size && previous[leftRotation].key != nextFirstKey) {
            leftRotation += 1
        }
        if (leftRotation == size || leftRotation == 0) return null

        var changedRangeStart = -1
        var changedRangeCount = 0
        var changedRanges: ArrayList<LazyListChangedRange>? = null
        var nextPosition = 0
        while (nextPosition < size) {
            var previousPosition = nextPosition + leftRotation
            if (previousPosition >= size) previousPosition -= size
            val oldItem = previous[previousPosition]
            val newItem = next[nextPosition]
            val sameItem = oldItem == newItem
            if (!sameItem && oldItem.key != newItem.key) return null

            val notifyNativeChange = !sameItem && requiresNativeChange(
                oldItem = oldItem,
                newItem = newItem,
                includeSemanticChanges = includeSemanticChanges,
            )
            if (!notifyNativeChange) {
                if (changedRangeStart >= 0) {
                    val ranges = changedRanges ?: ArrayList<LazyListChangedRange>().also {
                        changedRanges = it
                    }
                    ranges.add(LazyListChangedRange(changedRangeStart, changedRangeCount))
                    changedRangeStart = -1
                    changedRangeCount = 0
                }
            } else if (changedRangeStart < 0) {
                changedRangeStart = nextPosition
                changedRangeCount = 1
            } else {
                changedRangeCount += 1
            }
            nextPosition += 1
        }
        if (changedRangeStart >= 0) {
            val ranges = changedRanges ?: ArrayList<LazyListChangedRange>().also {
                changedRanges = it
            }
            ranges.add(LazyListChangedRange(changedRangeStart, changedRangeCount))
        }

        val rightRotation = size - leftRotation
        val direction: LazyListRotationDirection
        val moveCount: Int
        if (leftRotation <= rightRotation) {
            direction = LazyListRotationDirection.Left
            moveCount = leftRotation
        } else {
            direction = LazyListRotationDirection.Right
            moveCount = rightRotation
        }
        return LazyListAdapterUpdatePlan.CyclicRotation(
            direction = direction,
            moveCount = moveCount,
            changedRanges = changedRanges ?: emptyList(),
        )
    }

    private fun requiresNativeChange(
        oldItem: LazyListItem,
        newItem: LazyListItem,
        includeSemanticChanges: Boolean,
    ): Boolean {
        return includeSemanticChanges ||
            oldItem.contentType != newItem.contentType ||
            oldItem.kind != newItem.kind ||
            oldItem.span != newItem.span
    }

    private fun calculateDiff(
        previous: List<LazyListItem>,
        next: List<LazyListItem>,
        compactAdapterPayload: Boolean = false,
        includeSemanticChanges: Boolean = true,
    ): DiffUtil.DiffResult {
        return DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previous.size

                override fun getNewListSize(): Int = next.size

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean {
                    return previous[oldItemPosition].key == next[newItemPosition].key
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean {
                    val oldItem = previous[oldItemPosition]
                    val newItem = next[newItemPosition]
                    if (oldItem == newItem) return true
                    return compactAdapterPayload &&
                        !requiresNativeChange(
                            oldItem = oldItem,
                            newItem = newItem,
                            includeSemanticChanges = includeSemanticChanges,
                        )
                }

                override fun getChangePayload(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Any {
                    if (compactAdapterPayload) return LazyListAdapterChangedPayload
                    val oldItem = previous[oldItemPosition]
                    val newItem = next[newItemPosition]
                    return if (
                        oldItem.contentRevision != newItem.contentRevision ||
                        oldItem.environmentRevision != newItem.environmentRevision
                    ) {
                        LazyListChangePayload.RevisionChanged(
                            previousContent = oldItem.contentRevision,
                            nextContent = newItem.contentRevision,
                            previousEnvironment = oldItem.environmentRevision,
                            nextEnvironment = newItem.environmentRevision,
                        )
                    } else {
                        LazyListPresentationChangedPayload
                    }
                }
            },
        )
    }

    private fun canDiffByKey(
        previous: List<LazyListItem>,
        next: List<LazyListItem>,
    ): Boolean {
        return LazyListIdentityInspector.analyze(previous).supportsKeyedDiff &&
            LazyListIdentityInspector.analyze(next).supportsKeyedDiff
    }
}

private class RecordingLazyListUpdateCallback(
    private val updates: MutableList<LazyListUpdate>,
) : ListUpdateCallback {
    override fun onInserted(
        position: Int,
        count: Int,
    ) {
        repeat(count) { offset ->
            val index = position + offset
            updates += LazyListUpdate.Insert(index)
        }
    }

    override fun onRemoved(
        position: Int,
        count: Int,
    ) {
        repeat(count) {
            updates += LazyListUpdate.Remove(position)
        }
    }

    override fun onMoved(
        fromPosition: Int,
        toPosition: Int,
    ) {
        updates += LazyListUpdate.Move(fromPosition, toPosition)
    }

    override fun onChanged(
        position: Int,
        count: Int,
        payload: Any?,
    ) {
        repeat(count) { offset ->
            val index = position + offset
            updates += LazyListUpdate.Change(
                index = index,
                payload = payload,
            )
        }
    }
}
