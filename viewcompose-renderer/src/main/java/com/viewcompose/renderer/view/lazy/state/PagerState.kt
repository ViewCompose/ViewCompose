package com.viewcompose.renderer.view.lazy.state

import androidx.viewpager2.widget.ViewPager2

/**
 * Observable snapshot and imperative scroll handle for a mounted pager.
 *
 * The renderer updates this object from `ViewPager2` callbacks on the UI thread. It can be retained
 * across renders, but scrolling is a no-op while no pager is attached. It does not save or restore
 * position by itself; the owning declarative state remains the source of truth.
 */
class PagerState {
    private val listeners = linkedSetOf<(Int, Float) -> Unit>()

    /** Current zero-based page selected by the attached pager. */
    var currentPage: Int = 0
        internal set
    /** Current fractional offset reported by `ViewPager2`, usually in `[-1f, 1f]`. */
    var pageOffset: Float = 0f
        internal set
    internal var viewPager: ViewPager2? = null

    /**
     * Requests a smooth scroll to [page] on the currently attached pager.
     *
     * The request is ignored when the state is detached. `ViewPager2` owns range validation and may
     * clamp or reject indexes that are not present in its current adapter snapshot.
     *
     * @param page zero-based target page
     */
    fun scrollToPage(page: Int) {
        viewPager?.setCurrentItem(page, true)
    }

    internal fun updateFromPager(
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

    internal fun addOnPageSnapshotListener(
        listener: (Int, Float) -> Unit,
    ) {
        listeners += listener
    }

    internal fun removeOnPageSnapshotListener(
        listener: (Int, Float) -> Unit,
    ) {
        listeners -= listener
    }
}
