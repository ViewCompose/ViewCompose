package com.viewcompose.ui.state

/**
 * Stores the latest pager position and bridges page commands to a renderer.
 *
 * [currentPage] and [pageOffset] are plain snapshot values updated through [updateFromPager]; they
 * do not independently register composition observation. Use [addOnPageSnapshotListener] or the
 * higher-level widget state integration when a render must react to changes. All operations are
 * thread-confined to the owning renderer thread, normally Android's main thread.
 *
 * @sample com.viewcompose.ui.samples.pagerStateSample
 */
class PagerState {
    private val listeners = linkedSetOf<(Int, Float) -> Unit>()
    private var connector: PagerConnector? = null

    /** Latest page index reported by the renderer. */
    var currentPage: Int = 0
        private set

    /** Latest renderer-defined fractional offset from [currentPage]. */
    var pageOffset: Float = 0f
        private set

    /**
     * Requests the attached renderer to scroll to [page].
     *
     * The request is a no-op while detached and does not predictively change [currentPage]. This
     * contract does not validate page bounds; the mounted pager owns range handling.
     *
     * @param page renderer-resolved target page index
     */
    fun scrollToPage(page: Int) {
        connector?.scrollToPage(page)
    }

    /**
     * Installs a renderer-reported position and notifies listeners when it changes.
     *
     * Equal page and offset values are ignored. Listeners run synchronously in registration order;
     * mutating listener registration from inside a callback is unsupported.
     *
     * @param currentPage page index reported by the renderer
     * @param pageOffset renderer-defined fractional offset from that page
     */
    fun updateFromPager(
        currentPage: Int,
        pageOffset: Float,
    ) {
        if (this.currentPage == currentPage && this.pageOffset == pageOffset) {
            return
        }
        this.currentPage = currentPage
        this.pageOffset = pageOffset
        listeners.forEach { listener ->
            listener(currentPage, pageOffset)
        }
    }

    /**
     * Registers [listener] for distinct future pager positions.
     *
     * Registration does not replay the current position. Re-adding an equal listener is idempotent;
     * callers must remove listeners they no longer own.
     *
     * @param listener callback receiving page and offset in that order
     */
    fun addOnPageSnapshotListener(
        listener: (Int, Float) -> Unit,
    ) {
        listeners += listener
    }

    /**
     * Removes [listener] from future position callbacks.
     *
     * @param listener previously registered callback; unknown callbacks are ignored
     */
    fun removeOnPageSnapshotListener(
        listener: (Int, Float) -> Unit,
    ) {
        listeners -= listener
    }

    /**
     * Replaces the renderer connector used by future page commands.
     *
     * Passing `null` detaches without changing the retained position.
     *
     * @param connector renderer bridge, or `null` to detach
     */
    fun attach(connector: PagerConnector?) {
        this.connector = connector
    }
}

/**
 * Bridges [PagerState] page commands to a platform pager.
 *
 * This is a renderer implementation boundary. Calls are synchronous on the state's owning thread.
 */
interface PagerConnector {
    /**
     * Requests platform scrolling to [page].
     *
     * @param page target index whose range is resolved by the mounted pager
     */
    fun scrollToPage(page: Int)
}
