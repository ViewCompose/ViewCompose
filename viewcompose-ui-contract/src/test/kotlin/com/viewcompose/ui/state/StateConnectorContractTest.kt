package com.viewcompose.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateConnectorContractTest {
    @Test
    fun `lazy list state routes scroll commands and stop to attached connector`() {
        val state = LazyListState(
            initialFirstVisibleItemIndex = 2,
            initialFirstVisibleItemScrollOffset = 8,
        )
        val calls = mutableListOf<Triple<Int, Int, Boolean>>()
        var stopCalls = 0
        val connector = object : LazyListConnector {
            override fun scrollToItem(
                index: Int,
                scrollOffset: Int,
                animated: Boolean,
            ) {
                calls += Triple(index, scrollOffset, animated)
            }

            override fun stopScroll() {
                stopCalls += 1
            }
        }

        state.attach(connector)
        state.scrollToItem(index = 3, scrollOffset = 12)
        state.animateScrollToItem(5)
        state.stopScroll()
        state.attach(null)
        state.scrollToItem(9)

        assertEquals(
            listOf(
                Triple(2, 8, false),
                Triple(3, 12, false),
                Triple(5, 0, true),
            ),
            calls,
        )
        assertEquals(1, stopCalls)
    }

    @Test
    fun `lazy list state publishes complete layout snapshot and captures it on detach`() {
        val state = LazyListState()
        var platformListener: ((LazyListStateSnapshot) -> Unit)? = null
        var currentPlatformSnapshot = snapshot(index = 7, offset = 24)
        val connector = object : LazyListConnector {
            override fun scrollToItem(
                index: Int,
                scrollOffset: Int,
                animated: Boolean,
            ) = Unit

            override fun currentSnapshot(): LazyListStateSnapshot {
                return currentPlatformSnapshot
            }

            override fun setOnSnapshotChangedListener(
                listener: ((LazyListStateSnapshot) -> Unit)?,
            ) {
                platformListener = listener
            }
        }
        val observed = mutableListOf<LazyListStateSnapshot>()
        state.addOnSnapshotChangedListener { snapshot -> observed += snapshot }

        state.attach(connector)
        currentPlatformSnapshot = snapshot(
            index = 8,
            offset = 4,
            scrolling = true,
        )
        platformListener?.invoke(currentPlatformSnapshot)
        state.attach(null)

        assertEquals(8, state.firstVisibleItemIndex)
        assertEquals(4, state.firstVisibleItemScrollOffset)
        assertEquals(30, state.layoutInfo.totalItemsCount)
        assertEquals(listOf(8, 9), state.layoutInfo.visibleItemsInfo.map { it.index })
        assertEquals(LazyListOrientation.Vertical, state.layoutInfo.orientation)
        assertTrue(state.isScrollInProgress)
        assertTrue(state.canScrollBackward)
        assertTrue(state.canScrollForward)
        assertTrue(state.lastScrolledForward)
        assertFalse(state.lastScrolledBackward)
        assertEquals(2, observed.size)
        assertEquals(null, platformListener)
    }

    @Test
    fun `reattaching same platform identity does not reset scroll anchor`() {
        val state = LazyListState(initialFirstVisibleItemIndex = 4)
        val identity = Any()
        val calls = mutableListOf<Int>()

        fun connector() = object : LazyListConnector {
            override val identity: Any = identity

            override fun scrollToItem(
                index: Int,
                scrollOffset: Int,
                animated: Boolean,
            ) {
                calls += index
            }
        }

        state.attach(connector())
        state.attach(connector())

        assertEquals(listOf(4), calls)
    }

    @Test
    fun `pager state notifies listeners and delegates scroll command`() {
        val state = PagerState()
        val pageSnapshots = mutableListOf<Pair<Int, Float>>()
        var scrollTarget = -1
        val connector = object : PagerConnector {
            override fun scrollToPage(page: Int) {
                scrollTarget = page
            }
        }

        state.addOnPageSnapshotListener { page, offset ->
            pageSnapshots += page to offset
        }
        state.attach(connector)
        state.scrollToPage(7)
        state.updateFromPager(currentPage = 2, pageOffset = 0.25f)
        state.updateFromPager(currentPage = 2, pageOffset = 0.25f)

        assertEquals(7, scrollTarget)
        assertEquals(1, pageSnapshots.size)
        assertEquals(2, pageSnapshots.single().first)
        assertTrue(pageSnapshots.single().second == 0.25f)
    }

    private fun snapshot(
        index: Int,
        offset: Int,
        scrolling: Boolean = false,
    ): LazyListStateSnapshot {
        return LazyListStateSnapshot(
            firstVisibleItemIndex = index,
            firstVisibleItemScrollOffset = offset,
            layoutInfo = LazyListLayoutInfo(
                visibleItemsInfo = listOf(
                    LazyListItemInfo(
                        index = index,
                        key = "item-$index",
                        contentType = "row",
                        offset = -offset,
                        size = 40,
                    ),
                    LazyListItemInfo(
                        index = index + 1,
                        key = "item-${index + 1}",
                        contentType = "row",
                        offset = 20,
                        size = 40,
                    ),
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 100,
                totalItemsCount = 30,
                beforeContentPadding = 8,
                afterContentPadding = 12,
                mainAxisItemSpacing = 4,
                orientation = LazyListOrientation.Vertical,
                reverseLayout = false,
            ),
            isScrollInProgress = scrolling,
            canScrollBackward = index > 0,
            canScrollForward = true,
            lastScrolledBackward = false,
            lastScrolledForward = true,
        )
    }
}
