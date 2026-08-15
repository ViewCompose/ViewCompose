package com.viewcompose.ui.state

import com.viewcompose.runtime.mutableStateOf

/**
 * Owns the observable position snapshot and commands for one attached pager.
 *
 * Reading [snapshot] or a derived property during composition records a normal snapshot-state
 * dependency. The renderer reports page motion through [PagerConnector]; listeners run
 * synchronously after a distinct immutable snapshot is installed. Commands and attachment are
 * confined to the owning renderer thread, which is Android's main thread for the first-party
 * renderer.
 *
 * Both page commands are no-ops while detached and rely on renderer snapshots for intermediate
 * and final state. The pager DSL's controlled `currentPage` remains the source of truth across
 * detach and recreation; this object observes and commands the currently mounted presentation.
 *
 * @sample com.viewcompose.ui.samples.pagerStateSample
 */
class PagerState {
    private var connector: PagerConnector? = null
    private val listeners = linkedSetOf<(PagerStateSnapshot) -> Unit>()
    private val snapshotState = mutableStateOf(PagerStateSnapshot.initial())

    /** Latest immutable renderer snapshot; reads participate in snapshot observation. */
    val snapshot: PagerStateSnapshot
        get() = snapshotState.value

    /** Page at the leading edge of the current scroll position. */
    val currentPage: Int
        get() = snapshot.currentPage

    /** Last page confirmed after scrolling reached an idle settled state. */
    val settledPage: Int
        get() = snapshot.settledPage

    /** Page toward which the attached pager is currently moving. */
    val targetPage: Int
        get() = snapshot.targetPage

    /** Fractional progress from [currentPage] toward the next logical page. */
    val pageOffset: Float
        get() = snapshot.pageOffset

    /** Number of pages in the latest attached renderer snapshot. */
    val pageCount: Int
        get() = snapshot.pageCount

    /** Whether the attached pager reports active dragging or settling. */
    val isScrollInProgress: Boolean
        get() = snapshot.isScrollInProgress

    /** Whether a logical page exists before the current position. */
    val canScrollBackward: Boolean
        get() = snapshot.canScrollBackward

    /** Whether a logical page exists after the current position. */
    val canScrollForward: Boolean
        get() = snapshot.canScrollForward

    /**
     * Immediately selects [page] on the attached pager.
     *
     * The request is a no-op while detached and does not predictively change [snapshot]. The
     * mounted renderer resolves the upper bound and reports the resulting snapshot.
     *
     * @param page non-negative logical target page
     * @throws IllegalArgumentException if [page] is negative
     */
    fun scrollToPage(page: Int) {
        connector?.scrollToPage(page.requireValidPage(), animated = false)
    }

    /**
     * Requests an animated transition to [page] on the attached pager.
     *
     * The request is a no-op while detached and does not predictively change [snapshot]. The
     * connector reports motion and settled state through its snapshot listener.
     *
     * @param page non-negative logical target page
     * @throws IllegalArgumentException if [page] is negative
     */
    fun animateScrollToPage(page: Int) {
        connector?.scrollToPage(page.requireValidPage(), animated = true)
    }

    /**
     * Registers [listener] for distinct future snapshots.
     *
     * Registration does not replay the current value. Re-adding an equal listener is idempotent.
     *
     * @param listener callback invoked synchronously after a snapshot update
     */
    fun addOnSnapshotChangedListener(listener: (PagerStateSnapshot) -> Unit) {
        listeners += listener
    }

    /**
     * Removes [listener] from future snapshot callbacks.
     *
     * @param listener previously registered callback; unknown callbacks are ignored
     */
    fun removeOnSnapshotChangedListener(listener: (PagerStateSnapshot) -> Unit) {
        listeners -= listener
    }

    /**
     * Rebinds this state to [nextConnector] at the renderer boundary.
     *
     * The previous connector's latest snapshot is captured before its listener is cleared. Passing
     * `null` detaches while retaining the latest snapshot. A new connector publishes its current
     * snapshot without receiving an implicit page command, so controlled pager input stays
     * authoritative.
     *
     * @param nextConnector renderer bridge, or `null` to detach
     */
    fun attach(nextConnector: PagerConnector?) {
        if (connector === nextConnector) return

        val previousConnector = connector

        previousConnector?.currentSnapshot()?.let(::updateSnapshot)
        previousConnector?.setOnSnapshotChangedListener(null)

        connector = nextConnector
        if (nextConnector == null) return

        nextConnector.setOnSnapshotChangedListener(::updateSnapshot)
        nextConnector.currentSnapshot()?.let(::updateSnapshot)
    }

    private fun updateSnapshot(next: PagerStateSnapshot) {
        if (snapshotState.value == next) return
        snapshotState.value = next
        listeners.toList().forEach { listener -> listener(next) }
    }
}

/**
 * Bridges [PagerState] commands and immutable snapshots to one platform pager.
 *
 * This is a renderer implementation boundary. Methods run synchronously on the state owner's
 * renderer thread. Optional snapshot methods allow a command-only custom renderer.
 */
interface PagerConnector {
    /**
     * Selects [page] using the requested motion policy.
     *
     * @param page validated non-negative target whose upper bound is resolved by the renderer
     * @param animated whether the platform should animate toward the target
     */
    fun scrollToPage(
        page: Int,
        animated: Boolean,
    )

    /**
     * Returns the latest platform snapshot, or `null` when synchronous capture is unavailable.
     *
     * @return immutable current snapshot or `null`
     */
    fun currentSnapshot(): PagerStateSnapshot? = null

    /**
     * Replaces the callback used for future platform snapshot changes.
     *
     * @param listener callback for platform updates, or `null` to detach it
     */
    fun setOnSnapshotChangedListener(listener: ((PagerStateSnapshot) -> Unit)?) = Unit
}

/**
 * Captures one platform-neutral pager position and motion state.
 *
 * Page indexes use logical order and do not reverse in RTL. [pageOffset] is in `[0, 1]` and moves
 * from [currentPage] toward the next logical page. A zero [pageCount] is also used by a detached
 * state, which may retain a non-negative requested page but reports no scrolling capability.
 *
 * @property currentPage page at the leading edge of the current position
 * @property settledPage last page confirmed while idle
 * @property targetPage page selected as the current motion target
 * @property pageOffset fraction from [currentPage] toward the next logical page
 * @property pageCount non-negative number of pages in the renderer snapshot
 * @property isScrollInProgress whether dragging or settling is active
 * @property canScrollBackward whether a logical page exists before the current position
 * @property canScrollForward whether a logical page exists after the current position
 * @throws IllegalArgumentException for negative indexes/counts, indexes outside a non-empty page
 * count, offsets outside `[0, 1]`, or impossible directional flags
 */
data class PagerStateSnapshot(
    val currentPage: Int,
    val settledPage: Int,
    val targetPage: Int,
    val pageOffset: Float,
    val pageCount: Int,
    val isScrollInProgress: Boolean,
    val canScrollBackward: Boolean,
    val canScrollForward: Boolean,
) {
    init {
        currentPage.requireValidPage()
        settledPage.requireValidPage()
        targetPage.requireValidPage()
        require(pageOffset in 0f..1f) { "Pager pageOffset must be inside [0, 1]." }
        require(pageCount >= 0) { "Pager pageCount must be non-negative." }
        if (pageCount == 0) {
            require(!canScrollBackward && !canScrollForward) {
                "A detached or empty pager cannot report scroll capability."
            }
        } else {
            require(currentPage < pageCount && settledPage < pageCount && targetPage < pageCount) {
                "Pager indexes must be smaller than pageCount."
            }
            require(currentPage < pageCount - 1 || pageOffset == 0f) {
                "The last pager page cannot report a forward page offset."
            }
            require(canScrollBackward == (currentPage > 0 || pageOffset > 0f)) {
                "Pager backward capability must match its logical position."
            }
            require(canScrollForward == (currentPage < pageCount - 1)) {
                "Pager forward capability must match its logical position."
            }
        }
    }

    /** Creates initial detached snapshots before a renderer supplies page-count information. */
    companion object {
        /**
         * Creates a non-scrolling detached snapshot retaining [page].
         *
         * A detached state has no known page count, so page capability remains unavailable even
         * when the retained target is non-zero.
         *
         * @param page non-negative retained page
         * @return immutable detached snapshot
         * @throws IllegalArgumentException if [page] is negative
         */
        fun initial(page: Int = 0): PagerStateSnapshot {
            val validPage = page.requireValidPage()
            return PagerStateSnapshot(
                currentPage = validPage,
                settledPage = validPage,
                targetPage = validPage,
                pageOffset = 0f,
                pageCount = 0,
                isScrollInProgress = false,
                canScrollBackward = false,
                canScrollForward = false,
            )
        }
    }
}

private fun Int.requireValidPage(): Int {
    require(this >= 0) { "Pager page must be non-negative." }
    return this
}
