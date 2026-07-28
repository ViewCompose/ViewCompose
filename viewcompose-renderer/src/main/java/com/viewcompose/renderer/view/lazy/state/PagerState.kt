package com.viewcompose.renderer.view.lazy.state

import androidx.viewpager2.widget.ViewPager2

/**
 * 暴露 pager 当前页与偏移量的轻量状态对象。
 * Lightweight state object exposing pager current page and offset.
 */
class PagerState {
    private val listeners = linkedSetOf<(Int, Float) -> Unit>()

    var currentPage: Int = 0
        internal set
    var pageOffset: Float = 0f
        internal set
    internal var viewPager: ViewPager2? = null

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
