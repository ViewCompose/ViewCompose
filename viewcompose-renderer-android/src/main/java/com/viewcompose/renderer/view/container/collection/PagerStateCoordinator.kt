package com.viewcompose.renderer.view.container

import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerStateSnapshot

/** Keeps pager motion, the portable snapshot, and settled callbacks on one state machine. */
internal class PagerStateCoordinator(
    private val currentViewportPage: () -> Int,
    private val moveViewportToPage: (page: Int, animated: Boolean) -> Unit,
    private val pageCount: () -> Int,
    private val onSettledPageChanged: () -> ((Int) -> Unit)?,
) : PagerConnector, PagerViewportListener {
    private var snapshotListener: ((PagerStateSnapshot) -> Unit)? = null
    private var currentSnapshot = PagerStateSnapshot.initial()
    private var currentPage = 0
    private var settledPage = 0
    private var targetPage = 0
    private var pageOffset = 0f
    private var scrollState = PagerScrollState.Idle
    private var lastCallbackPage: Int? = null
    private var lastLogicalPosition: Float? = null
    private var commandedTargetPage: Int? = null

    override fun onPageSelected(position: Int) {
        targetPage = position
        if (scrollState == PagerScrollState.Idle) {
            currentPage = position
            pageOffset = 0f
            settleAndPublish(dispatchCallback = true)
        } else {
            publish()
        }
    }

    override fun onPageScrolled(position: Int, offset: Float) {
        currentPage = position
        pageOffset = offset.coerceIn(0f, 1f)
        val logicalPosition = position + pageOffset
        val previousLogicalPosition = lastLogicalPosition ?: settledPage.toFloat()
        targetPage = commandedTargetPage ?: when {
            logicalPosition > previousLogicalPosition -> position + 1
            logicalPosition < previousLogicalPosition -> position
            else -> targetPage
        }
        targetPage = targetPage.coerceIn(0, (pageCount() - 1).coerceAtLeast(0))
        lastLogicalPosition = logicalPosition
        publish()
    }

    override fun onScrollStateChanged(state: PagerScrollState) {
        scrollState = state
        if (state == PagerScrollState.Idle) {
            currentPage = currentViewportPage()
            targetPage = currentPage
            pageOffset = 0f
            lastLogicalPosition = currentPage.toFloat()
            commandedTargetPage = null
            settleAndPublish(dispatchCallback = true)
        } else {
            if (state == PagerScrollState.Dragging) {
                commandedTargetPage = null
                lastLogicalPosition = settledPage.toFloat()
            }
            publish()
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
        if (currentViewportPage() != resolvedPage) {
            moveViewportToPage(resolvedPage, false)
        }
        currentPage = resolvedPage
        settledPage = resolvedPage
        targetPage = resolvedPage
        pageOffset = 0f
        scrollState = PagerScrollState.Idle
        lastLogicalPosition = resolvedPage.toFloat()
        commandedTargetPage = null
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
        commandedTargetPage = resolvedPage.takeIf { animated }
        moveViewportToPage(resolvedPage, animated)
        if (!animated) {
            currentPage = currentViewportPage()
            pageOffset = 0f
            lastLogicalPosition = currentPage.toFloat()
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
        scrollState = PagerScrollState.Idle
        lastCallbackPage = null
        lastLogicalPosition = null
        commandedTargetPage = null
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
                isScrollInProgress = scrollState != PagerScrollState.Idle,
                canScrollBackward = resolvedCurrentPage > 0 || pageOffset > 0f,
                canScrollForward = resolvedCurrentPage < lastIndex,
            )
        }
        if (currentSnapshot == next) return
        currentSnapshot = next
        snapshotListener?.invoke(next)
    }
}
