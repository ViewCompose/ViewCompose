package com.viewcompose.ui.state

import com.viewcompose.runtime.mutableStateOf

/**
 * Owns the observable anchor and layout snapshot for a lazy list or grid.
 *
 * The renderer owns native scrolling and attaches through [LazyListConnector]. Reading [snapshot]
 * or any derived property during composition records a normal snapshot-state dependency. Commands
 * and listener registration are thread-confined to the owning renderer thread; Android callers use
 * the main thread. Snapshot listeners run synchronously after a distinct snapshot is installed.
 *
 * @sample com.viewcompose.ui.samples.lazyListStateSample
 * @param initialFirstVisibleItemIndex non-negative initial anchor index used before attachment
 * @param initialFirstVisibleItemScrollOffset non-negative initial offset from the start edge in
 * renderer units, normally physical pixels on Android
 * @throws IllegalArgumentException if either initial value is negative
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

    /** Latest immutable renderer snapshot; reads participate in snapshot observation. */
    val snapshot: LazyListStateSnapshot
        get() = snapshotState.value

    /** Current non-negative first visible item index derived from [snapshot]. */
    val firstVisibleItemIndex: Int
        get() = snapshot.firstVisibleItemIndex

    /** Current non-negative start-edge offset of the first visible item. */
    val firstVisibleItemScrollOffset: Int
        get() = snapshot.firstVisibleItemScrollOffset

    /** Latest immutable visible-item and viewport information. */
    val layoutInfo: LazyListLayoutInfo
        get() = snapshot.layoutInfo

    /** Whether the attached platform container reports an active scroll operation. */
    val isScrollInProgress: Boolean
        get() = snapshot.isScrollInProgress

    /** Whether the platform container can scroll toward items before the current viewport. */
    val canScrollBackward: Boolean
        get() = snapshot.canScrollBackward

    /** Whether the platform container can scroll toward items after the current viewport. */
    val canScrollForward: Boolean
        get() = snapshot.canScrollForward

    /** Whether the latest reported scroll direction was backward. */
    val lastScrolledBackward: Boolean
        get() = snapshot.lastScrolledBackward

    /** Whether the latest reported scroll direction was forward. */
    val lastScrolledForward: Boolean
        get() = snapshot.lastScrolledForward

    /**
     * Semantic key of the first visible item, or `null` when layout information has no matching item.
     */
    val firstVisibleItemKey: Any?
        get() = layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == firstVisibleItemIndex }
            ?.key

    /** Highest visible item index, or `0` when no visible item information is available. */
    val lastVisibleItemIndex: Int
        get() = layoutInfo.visibleItemsInfo.maxOfOrNull { item -> item.index } ?: 0

    /** Whether backward scrolling is currently unavailable. */
    val isAtStart: Boolean
        get() = !canScrollBackward

    /**
     * Whether a non-empty data set currently reports no forward scrolling.
     *
     * Empty or not-yet-laid-out content returns `false` because an end boundary is not established.
     */
    val isAtEnd: Boolean
        get() = layoutInfo.totalItemsCount > 0 && !canScrollForward

    /**
     * Immediately places [index] at the start edge with [scrollOffset].
     *
     * The local snapshot anchor updates before the renderer command, including while detached.
     * An attached connector receives the command synchronously with animation disabled.
     *
     * @param index non-negative target item index
     * @param scrollOffset non-negative offset from the start edge in renderer units
     * @throws IllegalArgumentException if [index] or [scrollOffset] is negative
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
     * Requests an animated scroll that places [index] at the start edge.
     *
     * This is a no-op while detached and does not predictively update [snapshot]. The connector
     * reports intermediate and final state through its snapshot listener.
     *
     * @param index non-negative target item index
     * @throws IllegalArgumentException if [index] is negative
     */
    fun animateScrollToItem(index: Int) {
        val targetIndex = index.requireValidIndex()
        connector?.scrollToItem(
            index = targetIndex,
            scrollOffset = 0,
            animated = true,
        )
    }

    /** Requests cancellation of platform scrolling, or does nothing while detached. */
    fun stopScroll() {
        connector?.stopScroll()
    }

    /**
     * Registers [listener] for distinct future snapshots.
     *
     * Registration does not replay the current value. Re-adding an equal listener is idempotent.
     *
     * @param listener callback invoked synchronously after a snapshot update
     */
    fun addOnSnapshotChangedListener(listener: (LazyListStateSnapshot) -> Unit) {
        listeners += listener
    }

    /**
     * Removes [listener] from future snapshot callbacks.
     *
     * @param listener previously registered callback; unknown callbacks are ignored
     */
    fun removeOnSnapshotChangedListener(listener: (LazyListStateSnapshot) -> Unit) {
        listeners -= listener
    }

    /**
     * Rebinds this state to [nextConnector] at the platform renderer boundary.
     *
     * The previous connector's latest snapshot is captured before its listener is cleared. A new
     * platform identity receives the retained anchor immediately; a replacement wrapper with the
     * same [LazyListConnector.identity] does not issue a redundant scroll. Passing `null` detaches
     * while retaining the latest snapshot. Reattaching the same connector instance is a no-op.
     *
     * @param nextConnector new renderer connector, or `null` to detach
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
 * Bridges [LazyListState] commands and snapshots to one platform lazy container.
 *
 * This is a renderer implementation boundary rather than an application extension point. Methods
 * are called synchronously on the state owner's renderer thread. Default optional operations make
 * a minimal connector command-only and snapshot-silent.
 */
interface LazyListConnector {
    /**
     * Stable identity of the native lazy container across connector-wrapper replacement.
     *
     * The default is connector object identity.
     */
    val identity: Any
        get() = this

    /**
     * Places [index] at the start edge with [scrollOffset].
     *
     * @param index validated non-negative target index
     * @param scrollOffset validated non-negative start-edge offset in renderer units
     * @param animated whether the platform should animate toward the target
     */
    fun scrollToItem(
        index: Int,
        scrollOffset: Int,
        animated: Boolean,
    )

    /** Stops an active platform scroll when supported. */
    fun stopScroll() = Unit

    /**
     * Returns the latest platform snapshot, or `null` when synchronous capture is unavailable.
     *
     * @return immutable current snapshot or `null`
     */
    fun currentSnapshot(): LazyListStateSnapshot? = null

    /**
     * Replaces the callback used for future platform snapshot changes.
     *
     * @param listener callback for distinct or platform-defined updates, or `null` to detach it
     */
    fun setOnSnapshotChangedListener(listener: ((LazyListStateSnapshot) -> Unit)?) = Unit
}

/**
 * Captures platform-neutral scroll and layout state for a lazy list or grid at one instant.
 *
 * @property firstVisibleItemIndex non-negative anchor index
 * @property firstVisibleItemScrollOffset non-negative offset from the start edge in renderer units
 * @property layoutInfo immutable visible-item and viewport information
 * @property isScrollInProgress whether the platform reports an active scroll
 * @property canScrollBackward whether content exists before the current viewport
 * @property canScrollForward whether content exists after the current viewport
 * @property lastScrolledBackward whether the latest reported direction was backward
 * @property lastScrolledForward whether the latest reported direction was forward
 * @throws IllegalArgumentException if an anchor value is negative or both last-direction flags are true
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

    /** Creates initial snapshots before a renderer supplies layout information. */
    companion object {
        /**
         * Creates a non-scrolling snapshot with empty layout information.
         *
         * @param firstVisibleItemIndex non-negative initial anchor index
         * @param firstVisibleItemScrollOffset non-negative start-edge offset in renderer units
         * @return an initial immutable snapshot
         * @throws IllegalArgumentException if either anchor value is negative
         */
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
 * Describes the visible viewport of a lazy list or grid.
 *
 * Offsets and sizes use renderer units, normally physical pixels on Android. The visible item list
 * is an immutable list reference by convention; callers and connectors must not mutate a supplied
 * mutable implementation after construction.
 *
 * @property visibleItemsInfo visible items in renderer-defined placement order
 * @property viewportStartOffset inclusive main-axis viewport start
 * @property viewportEndOffset exclusive main-axis viewport end
 * @property totalItemsCount non-negative total data-set size
 * @property beforeContentPadding non-negative padding before content on the main axis
 * @property afterContentPadding non-negative padding after content on the main axis
 * @property mainAxisItemSpacing non-negative spacing between adjacent items
 * @property orientation main axis used for offsets and sizes
 * @property reverseLayout whether logical item order is placed from the opposite edge
 * @throws IllegalArgumentException if viewport bounds, counts, padding, spacing, or visible indices
 * violate the layout invariants
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

    /** Non-negative main-axis viewport size derived from end minus start. */
    val viewportSize: Int
        get() = viewportEndOffset - viewportStartOffset

    /** Provides common lazy-layout snapshots. */
    companion object {
        /** Empty vertical viewport used before the first renderer layout. */
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
 * Describes one visible lazy item in platform-neutral renderer units.
 *
 * @property index non-negative data-set index
 * @property key semantic item identity retained across moves
 * @property contentType optional reuse classification supplied by the DSL
 * @property offset item start relative to the viewport start; may be negative when partially clipped
 * @property size non-negative main-axis size
 * @property spanIndex non-negative starting span for grid layouts
 * @property spanSize positive number of spans occupied by the item
 * @throws IllegalArgumentException if index, size, or span values violate their ranges
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

/** Selects a vertical or horizontal main axis for lazy-list layout information. */
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
