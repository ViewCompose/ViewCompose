package com.viewcompose.renderer.view.container

import com.viewcompose.ui.state.PagerStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerStateCoordinatorTest {
    @Test
    fun `drag publishes motion and callback only after settled idle`() {
        var pageCount = 4
        var viewportPage = 0
        val settledCallbacks = mutableListOf<Int>()
        val snapshots = mutableListOf<PagerStateSnapshot>()
        val coordinator = coordinator(
            pageCount = { pageCount },
            currentViewportPage = { viewportPage },
            moveViewportToPage = { page, _ -> viewportPage = page },
            settledCallbacks = settledCallbacks,
        )
        coordinator.setOnSnapshotChangedListener(snapshots::add)
        coordinator.applyControlledPage(1)

        coordinator.onScrollStateChanged(PagerScrollState.Dragging)
        coordinator.onPageScrolled(1, 0.5f)
        coordinator.onPageSelected(2)

        assertTrue(coordinator.currentSnapshot().isScrollInProgress)
        assertEquals(1, coordinator.currentSnapshot().currentPage)
        assertEquals(2, coordinator.currentSnapshot().targetPage)
        assertEquals(emptyList<Int>(), settledCallbacks)

        viewportPage = 2
        coordinator.onScrollStateChanged(PagerScrollState.Idle)
        coordinator.onScrollStateChanged(PagerScrollState.Idle)

        assertEquals(listOf(2), settledCallbacks)
        assertEquals(2, coordinator.currentSnapshot().settledPage)
        assertFalse(coordinator.currentSnapshot().isScrollInProgress)
        assertTrue(snapshots.isNotEmpty())
    }

    @Test
    fun `controlled rebinding never feeds page callback back to caller`() {
        var viewportPage = 0
        val settledCallbacks = mutableListOf<Int>()
        val coordinator = coordinator(
            pageCount = { 3 },
            currentViewportPage = { viewportPage },
            moveViewportToPage = { page, _ -> viewportPage = page },
            settledCallbacks = settledCallbacks,
        )

        coordinator.applyControlledPage(2)
        coordinator.onPageSelected(2)
        coordinator.onScrollStateChanged(PagerScrollState.Idle)

        assertEquals(emptyList<Int>(), settledCallbacks)
        assertEquals(2, coordinator.currentSnapshot().settledPage)
    }

    @Test
    fun `backward drag reports the lower logical page as its target`() {
        var viewportPage = 2
        val coordinator = coordinator(
            pageCount = { 4 },
            currentViewportPage = { viewportPage },
            moveViewportToPage = { page, _ -> viewportPage = page },
        )
        coordinator.applyControlledPage(2)

        coordinator.onScrollStateChanged(PagerScrollState.Dragging)
        coordinator.onPageScrolled(position = 1, offset = 0.8f)

        assertEquals(1, coordinator.currentSnapshot().currentPage)
        assertEquals(1, coordinator.currentSnapshot().targetPage)
        assertEquals(0.8f, coordinator.currentSnapshot().pageOffset)
    }

    @Test
    fun `animated command retains its final target across intermediate pages`() {
        var viewportPage = 0
        val coordinator = coordinator(
            pageCount = { 5 },
            currentViewportPage = { viewportPage },
            moveViewportToPage = { _, _ -> Unit },
        )
        coordinator.applyControlledPage(0)

        coordinator.scrollToPage(page = 4, animated = true)
        coordinator.onScrollStateChanged(PagerScrollState.Settling)
        coordinator.onPageScrolled(position = 1, offset = 0.25f)

        assertEquals(4, coordinator.currentSnapshot().targetPage)
    }

    @Test
    fun `page count changes clamp every published index`() {
        var pageCount = 4
        var viewportPage = 0
        val coordinator = coordinator(
            pageCount = { pageCount },
            currentViewportPage = { viewportPage },
            moveViewportToPage = { page, _ -> viewportPage = page },
        )
        coordinator.applyControlledPage(3)

        pageCount = 2
        coordinator.onPageCountChanged()

        assertEquals(1, coordinator.currentSnapshot().currentPage)
        assertEquals(1, coordinator.currentSnapshot().settledPage)
        assertEquals(2, coordinator.currentSnapshot().pageCount)
    }

    private fun coordinator(
        pageCount: () -> Int,
        currentViewportPage: () -> Int,
        moveViewportToPage: (Int, Boolean) -> Unit,
        settledCallbacks: MutableList<Int> = mutableListOf(),
    ): PagerStateCoordinator = PagerStateCoordinator(
        currentViewportPage = currentViewportPage,
        moveViewportToPage = moveViewportToPage,
        pageCount = pageCount,
        onSettledPageChanged = { settledCallbacks::add },
    )
}
