package com.viewcompose.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateConnectorContractTest {
    @Test
    fun `lazy list state routes scroll commands to attached connector`() {
        val state = LazyListState(
            initialFirstVisibleItemIndex = 2,
            initialFirstVisibleItemScrollOffset = 8,
        )
        val calls = mutableListOf<Triple<Int, Int, Boolean>>()
        val connector = object : LazyListConnector {
            override fun scrollToPosition(index: Int, smooth: Boolean) {
                calls += Triple(index, 0, smooth)
            }

            override fun scrollToPosition(index: Int, scrollOffset: Int, smooth: Boolean) {
                calls += Triple(index, scrollOffset, smooth)
            }
        }

        state.attach(connector)
        state.scrollToPosition(index = 3, scrollOffset = 12)
        state.smoothScrollToPosition(5)
        state.attach(null)
        state.scrollToPosition(9)

        assertEquals(
            listOf(
                Triple(2, 8, false),
                Triple(3, 12, false),
                Triple(5, 0, true),
            ),
            calls,
        )
    }

    @Test
    fun `lazy list state captures visible position when connector detaches`() {
        val state = LazyListState()
        val connector = object : LazyListConnector {
            override fun scrollToPosition(index: Int, smooth: Boolean) = Unit

            override fun currentPosition(): LazyListPosition {
                return LazyListPosition(
                    index = 7,
                    scrollOffset = 24,
                )
            }
        }

        state.attach(connector)
        state.attach(null)

        assertEquals(7, state.firstVisibleItemIndex)
        assertEquals(24, state.firstVisibleItemScrollOffset)
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
}
