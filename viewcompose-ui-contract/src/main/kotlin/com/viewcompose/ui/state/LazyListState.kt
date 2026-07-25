package com.viewcompose.ui.state

import com.viewcompose.runtime.mutableStateOf

/**
 * Observable state for lazy lists and grids.
 *
 * The renderer owns platform scrolling while this class owns the durable anchor and the latest
 * platform-independent layout snapshot. Reading any public snapshot-backed property during
 * composition registers a normal snapshot-state dependency.
 */
class LazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
) {
    private var connector: LazyListConnector? = null
    private var connectorIdentity: Any? = null
    private val listeners = linkedSetOf<(LazyListStateSnapshot) -> Unit>()
    private val snapshotState = mutableStateOf(
        LazyListStateSnapshot.initial(
            firstVisibleItemIndex = initialFirstVisibleItemIndex.requireValidIndex(),
            firstVisibleItemScrollOffset =
                initialFirstVisibleItemScrollOffset.requireValidScrollOffset(),
        ),
    )

    val snapshot: LazyListStateSnapshot
        get() = snapshotState.value

    val firstVisibleItemIndex: Int
        get() = snapshot.firstVisibleItemIndex

    val firstVisibleItemScrollOffset: Int
        get() = snapshot.firstVisibleItemScrollOffset

    val layoutInfo: LazyListLayoutInfo
        get() = snapshot.layoutInfo

    val isScrollInProgress: Boolean
        get() = snapshot.isScrollInProgress

    val canScrollBackward: Boolean
        get() = snapshot.canScrollBackward

    val canScrollForward: Boolean
        get() = snapshot.canScrollForward

    val lastScrolledBackward: Boolean
        get() = snapshot.lastScrolledBackward

    val lastScrolledForward: Boolean
        get() = snapshot.lastScrolledForward

    /**
     * Immediately places [index] at the start edge, shifted by [scrollOffset] pixels.
     */
    fun scrollToItem(
        index: Int,
        scrollOffset: Int = 0,
    ) {
        val targetIndex = index.requireValidIndex()
        val targetOffset = scrollOffset.requireValidScrollOffset()
        updateSnapshot(
            snapshot.copy(
                firstVisibleItemIndex = targetIndex,
                firstVisibleItemScrollOffset = targetOffset,
                isScrollInProgress = false,
            ),
        )
        connector?.scrollToItem(
            index = targetIndex,
            scrollOffset = targetOffset,
            animated = false,
        )
    }

    /**
     * Smoothly scrolls [index] into view. The final platform anchor is reported through [snapshot].
     */
    fun animateScrollToItem(index: Int) {
        val targetIndex = index.requireValidIndex()
        connector?.scrollToItem(
            index = targetIndex,
            scrollOffset = 0,
            animated = true,
        )
    }

    fun stopScroll() {
        connector?.stopScroll()
    }

    fun addOnSnapshotChangedListener(listener: (LazyListStateSnapshot) -> Unit) {
        listeners += listener
    }

    fun removeOnSnapshotChangedListener(listener: (LazyListStateSnapshot) -> Unit) {
        listeners -= listener
    }

    /**
     * Renderer attachment boundary. Public so a platform renderer can implement the connector
     * without making the UI contract depend on Android.
     */
    fun attach(nextConnector: LazyListConnector?) {
        if (connector === nextConnector) {
            return
        }

        val previousConnector = connector
        val samePlatformIdentity =
            previousConnector != null &&
                nextConnector != null &&
                connectorIdentity === nextConnector.identity

        previousConnector?.currentSnapshot()?.let(::updateSnapshot)
        previousConnector?.setOnSnapshotChangedListener(null)

        connector = nextConnector
        connectorIdentity = nextConnector?.identity
        if (nextConnector == null) {
            return
        }

        nextConnector.setOnSnapshotChangedListener(::updateSnapshot)
        if (!samePlatformIdentity) {
            nextConnector.scrollToItem(
                index = snapshot.firstVisibleItemIndex,
                scrollOffset = snapshot.firstVisibleItemScrollOffset,
                animated = false,
            )
        }
        nextConnector.currentSnapshot()?.let(::updateSnapshot)
    }

    private fun updateSnapshot(next: LazyListStateSnapshot) {
        if (snapshotState.value == next) {
            return
        }
        snapshotState.value = next
        listeners.toList().forEach { listener -> listener(next) }
    }
}

interface LazyListConnector {
    val identity: Any
        get() = this

    fun scrollToItem(
        index: Int,
        scrollOffset: Int,
        animated: Boolean,
    )

    fun stopScroll() = Unit

    fun currentSnapshot(): LazyListStateSnapshot? = null

    fun setOnSnapshotChangedListener(listener: ((LazyListStateSnapshot) -> Unit)?) = Unit
}

data class LazyListStateSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val layoutInfo: LazyListLayoutInfo,
    val isScrollInProgress: Boolean,
    val canScrollBackward: Boolean,
    val canScrollForward: Boolean,
    val lastScrolledBackward: Boolean,
    val lastScrolledForward: Boolean,
) {
    init {
        firstVisibleItemIndex.requireValidIndex()
        firstVisibleItemScrollOffset.requireValidScrollOffset()
        require(!(lastScrolledBackward && lastScrolledForward)) {
            "A lazy list snapshot cannot report both scroll directions."
        }
    }

    companion object {
        fun initial(
            firstVisibleItemIndex: Int = 0,
            firstVisibleItemScrollOffset: Int = 0,
        ): LazyListStateSnapshot {
            return LazyListStateSnapshot(
                firstVisibleItemIndex = firstVisibleItemIndex.requireValidIndex(),
                firstVisibleItemScrollOffset =
                    firstVisibleItemScrollOffset.requireValidScrollOffset(),
                layoutInfo = LazyListLayoutInfo.Empty,
                isScrollInProgress = false,
                canScrollBackward = false,
                canScrollForward = false,
                lastScrolledBackward = false,
                lastScrolledForward = false,
            )
        }
    }
}

data class LazyListLayoutInfo(
    val visibleItemsInfo: List<LazyListItemInfo>,
    val viewportStartOffset: Int,
    val viewportEndOffset: Int,
    val totalItemsCount: Int,
    val beforeContentPadding: Int,
    val afterContentPadding: Int,
    val mainAxisItemSpacing: Int,
    val orientation: LazyListOrientation,
    val reverseLayout: Boolean,
) {
    init {
        require(viewportEndOffset >= viewportStartOffset) {
            "viewportEndOffset must be greater than or equal to viewportStartOffset."
        }
        require(totalItemsCount >= 0) { "totalItemsCount must be non-negative." }
        require(beforeContentPadding >= 0) { "beforeContentPadding must be non-negative." }
        require(afterContentPadding >= 0) { "afterContentPadding must be non-negative." }
        require(mainAxisItemSpacing >= 0) { "mainAxisItemSpacing must be non-negative." }
        visibleItemsInfo.forEach { item ->
            require(item.index < totalItemsCount) {
                "Visible item index ${item.index} is outside totalItemsCount=$totalItemsCount."
            }
        }
    }

    val viewportSize: Int
        get() = viewportEndOffset - viewportStartOffset

    companion object {
        val Empty = LazyListLayoutInfo(
            visibleItemsInfo = emptyList(),
            viewportStartOffset = 0,
            viewportEndOffset = 0,
            totalItemsCount = 0,
            beforeContentPadding = 0,
            afterContentPadding = 0,
            mainAxisItemSpacing = 0,
            orientation = LazyListOrientation.Vertical,
            reverseLayout = false,
        )
    }
}

data class LazyListItemInfo(
    val index: Int,
    val key: Any,
    val contentType: Any?,
    val offset: Int,
    val size: Int,
    val spanIndex: Int = 0,
    val spanSize: Int = 1,
) {
    init {
        index.requireValidIndex()
        require(size >= 0) { "Lazy list item size must be non-negative." }
        require(spanIndex >= 0) { "Lazy list item spanIndex must be non-negative." }
        require(spanSize > 0) { "Lazy list item spanSize must be greater than zero." }
    }
}

enum class LazyListOrientation {
    Vertical,
    Horizontal,
}

private fun Int.requireValidIndex(): Int {
    require(this >= 0) {
        "Lazy list item index must be non-negative, but was $this."
    }
    return this
}

private fun Int.requireValidScrollOffset(): Int {
    require(this >= 0) {
        "Lazy list scroll offset must be non-negative, but was $this."
    }
    return this
}
