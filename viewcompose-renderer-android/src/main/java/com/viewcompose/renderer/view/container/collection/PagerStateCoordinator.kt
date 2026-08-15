package com.viewcompose.renderer.view.container

import androidx.viewpager2.widget.ViewPager2
import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerStateSnapshot

/** Keeps ViewPager2 motion, the portable snapshot, and settled callbacks on one state machine. */
internal class PagerStateCoordinator(
    private val viewPager: ViewPager2,
    private val pageCount: () -> Int,
    private val onSettledPageChanged: () -> ((Int) -> Unit)?,
) : PagerConnector {
    private var snapshotListener: ((PagerStateSnapshot) -> Unit)? = null
    private var currentSnapshot = PagerStateSnapshot.initial()
    private var currentPage = 0
    private var settledPage = 0
    private var targetPage = 0
    private var pageOffset = 0f
    private var scrollState = ViewPager2.SCROLL_STATE_IDLE
    private var lastCallbackPage: Int? = null

    val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            targetPage = position
            if (scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                currentPage = position
                pageOffset = 0f
                settleAndPublish(dispatchCallback = true)
            } else {
                publish()
            }
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int,
        ) {
            currentPage = position
            pageOffset = positionOffset.coerceIn(0f, 1f)
            publish()
        }

        override fun onPageScrollStateChanged(state: Int) {
            scrollState = state
            if (state == ViewPager2.SCROLL_STATE_IDLE) {
                currentPage = viewPager.currentItem
                targetPage = currentPage
                pageOffset = 0f
                settleAndPublish(dispatchCallback = true)
            } else {
                publish()
            }
        }
    }

    /** Publishes a controlled declarative page without feeding it back through the callback. */
    fun applyControlledPage(page: Int) {
        val count = pageCount()
        if (count == 0) {
            resetEmpty()
            return
        }
        val resolvedPage = page.coerceIn(0, count - 1)
        lastCallbackPage = resolvedPage
        if (viewPager.currentItem != resolvedPage) {
            viewPager.setCurrentItem(resolvedPage, false)
        }
        currentPage = resolvedPage
        settledPage = resolvedPage
        targetPage = resolvedPage
        pageOffset = 0f
        scrollState = ViewPager2.SCROLL_STATE_IDLE
        publish()
    }

    /** Revalidates the snapshot after the adapter accepts a new page collection. */
    fun onPageCountChanged() {
        val count = pageCount()
        if (count == 0) {
            resetEmpty()
            return
        }
        currentPage = currentPage.coerceIn(0, count - 1)
        settledPage = settledPage.coerceIn(0, count - 1)
        targetPage = targetPage.coerceIn(0, count - 1)
        publish()
    }

    override fun scrollToPage(page: Int, animated: Boolean) {
        val count = pageCount()
        if (count == 0) return
        val resolvedPage = page.coerceIn(0, count - 1)
        targetPage = resolvedPage
        viewPager.setCurrentItem(resolvedPage, animated)
        if (!animated) {
            currentPage = viewPager.currentItem
            pageOffset = 0f
            settleAndPublish(dispatchCallback = true)
        }
    }

    override fun currentSnapshot(): PagerStateSnapshot = currentSnapshot

    override fun setOnSnapshotChangedListener(listener: ((PagerStateSnapshot) -> Unit)?) {
        snapshotListener = listener
    }

    private fun settleAndPublish(dispatchCallback: Boolean) {
        settledPage = currentPage
        targetPage = currentPage
        publish()
        if (dispatchCallback && lastCallbackPage != settledPage) {
            lastCallbackPage = settledPage
            onSettledPageChanged()?.invoke(settledPage)
        }
    }

    private fun resetEmpty() {
        currentPage = 0
        settledPage = 0
        targetPage = 0
        pageOffset = 0f
        scrollState = ViewPager2.SCROLL_STATE_IDLE
        lastCallbackPage = null
        publish()
    }

    private fun publish() {
        val count = pageCount()
        val next = if (count == 0) {
            PagerStateSnapshot.initial()
        } else {
            val lastIndex = count - 1
            val resolvedCurrentPage = currentPage.coerceIn(0, lastIndex)
            PagerStateSnapshot(
                currentPage = resolvedCurrentPage,
                settledPage = settledPage.coerceIn(0, lastIndex),
                targetPage = targetPage.coerceIn(0, lastIndex),
                pageOffset = pageOffset.coerceIn(0f, 1f),
                pageCount = count,
                isScrollInProgress = scrollState != ViewPager2.SCROLL_STATE_IDLE,
                canScrollBackward = resolvedCurrentPage > 0 || pageOffset > 0f,
                canScrollForward = resolvedCurrentPage < lastIndex,
            )
        }
        if (currentSnapshot == next) return
        currentSnapshot = next
        snapshotListener?.invoke(next)
    }
}
