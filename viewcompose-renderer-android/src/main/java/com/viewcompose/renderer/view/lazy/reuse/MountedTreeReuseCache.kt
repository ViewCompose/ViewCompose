package com.viewcompose.renderer.view.lazy.reuse

import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.ReusableItemPresentation

/** Bounded renderer-owned cache with deterministic native-tree eviction. */
internal class MountedTreeReuseCache(
    capacity: Int = 2,
) {
    data class ReuseKey(
        val kind: LazyListItemKind,
        val contentType: Any?,
    )

    private data class Entry(
        val key: ReuseKey,
        val presentation: ReusableItemPresentation,
    )

    private val entries = ArrayDeque<Entry>()
    var capacity: Int = capacity
        set(value) {
            require(value >= 0) { "Mounted-tree cache capacity must be non-negative." }
            field = value
            trimToCapacity()
        }

    fun offer(
        key: ReuseKey,
        presentation: ReusableItemPresentation,
    ) {
        if (capacity == 0) {
            presentation.release()
            return
        }
        entries.addLast(Entry(key, presentation))
        trimToCapacity()
    }

    fun take(key: ReuseKey): ReusableItemPresentation? {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == key) {
                iterator.remove()
                return entry.presentation
            }
        }
        return null
    }

    fun clear() {
        var failure: Throwable? = null
        while (entries.isNotEmpty()) {
            try {
                entries.removeFirst().presentation.release()
            } catch (releaseError: Throwable) {
                if (failure == null) failure = releaseError else failure.addSuppressed(releaseError)
            }
        }
        failure?.let { throw it }
    }

    private fun trimToCapacity() {
        var failure: Throwable? = null
        while (entries.size > capacity) {
            try {
                entries.removeFirst().presentation.release()
            } catch (releaseError: Throwable) {
                if (failure == null) failure = releaseError else failure.addSuppressed(releaseError)
            }
        }
        failure?.let { throw it }
    }
}
