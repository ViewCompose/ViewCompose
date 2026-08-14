package com.viewcompose.renderer.reconcile

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
        val diffResult = DiffUtil.calculateDiff(
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
                    return previous[oldItemPosition] == next[newItemPosition]
                }

                override fun getChangePayload(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Any {
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
