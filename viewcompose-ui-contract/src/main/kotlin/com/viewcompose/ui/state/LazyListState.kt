package com.viewcompose.ui.state

import com.viewcompose.runtime.mutableStateOf

/**
 * lazy list/grid 的可观察状态。
 * Observable state for lazy lists and grids.
 *
 * renderer 负责平台滚动动作，本类负责持久锚点和最新的平台无关布局快照。
 * composition 中读取任何由 snapshot 支撑的公开属性，都会登记普通的 snapshot-state 依赖。
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

    val firstVisibleItemKey: Any?
        get() = layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == firstVisibleItemIndex }
            ?.key

    val lastVisibleItemIndex: Int
        get() = layoutInfo.visibleItemsInfo.maxOfOrNull { item -> item.index } ?: 0

    val isAtStart: Boolean
        get() = !canScrollBackward

    val isAtEnd: Boolean
        get() = layoutInfo.totalItemsCount > 0 && !canScrollForward

    /**
     * 立即将 [index] 放到起始边缘，并按 [scrollOffset] 像素偏移。
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
     * 平滑滚动到 [index]；最终平台锚点会通过 [snapshot] 回传。
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
     * renderer 连接边界。保持 public 是为了让平台 renderer 实现连接器，同时避免 UI contract 依赖 Android。
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

/**
 * lazy list 状态与平台 renderer 之间的命令/快照桥接。
 * Command and snapshot bridge between LazyListState and the platform renderer.
 */
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

/**
 * lazy list/grid 某一时刻的平台无关滚动与布局快照。
 * Platform-neutral scroll and layout snapshot for a lazy list/grid at one moment.
 */
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

/**
 * lazy list/grid 当前可见布局信息。
 * Current visible layout information for a lazy list/grid.
 */
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

/**
 * 单个可见 lazy item 的平台无关布局信息。
 * Platform-neutral layout information for one visible lazy item.
 */
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

/**
 * lazy list 主轴方向。
 * Main-axis orientation for a lazy list.
 */
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
