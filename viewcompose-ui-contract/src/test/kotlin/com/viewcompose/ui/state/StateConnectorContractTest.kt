package com.viewcompose.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateConnectorContractTest {
    @Test
    fun `scroll state retains immediate commands and ignores detached animation`() {
        val state = ScrollState(initialValue = 12)
        val commands = mutableListOf<Pair<Int, Boolean>>()
        var listener: ((ScrollStateSnapshot) -> Unit)? = null
        val recordingConnector = object : ScrollConnector {
            override fun scrollTo(value: Int, animated: Boolean) {
                commands += value to animated
            }

            override fun setOnSnapshotChangedListener(next: ((ScrollStateSnapshot) -> Unit)?) {
                listener = next
            }
        }

        state.scrollTo(24)
        state.animateScrollTo(30)
        state.attach(recordingConnector)
        state.animateScrollTo(40)
        listener?.invoke(scrollSnapshot(value = 40, maximum = 100, scrolling = true))
        state.attach(null)
        state.scrollTo(60)

        assertEquals(listOf(24 to false, 40 to true), commands)
        assertEquals(60, state.value)
        assertEquals(null, listener)
    }

    @Test
    fun `scroll state replacement with same native identity does not reset offset`() {
        val identity = Any()
        val state = ScrollState(initialValue = 18)
        val commands = mutableListOf<Int>()

        fun connector() = object : ScrollConnector {
            override val identity: Any = identity

            override fun scrollTo(value: Int, animated: Boolean) {
                commands += value
            }
        }

        state.attach(connector())
        state.attach(connector())

        assertEquals(listOf(18), commands)
    }

    @Test
    fun `scroll state publishes distinct complete snapshots`() {
        val state = ScrollState()
        var listener: ((ScrollStateSnapshot) -> Unit)? = null
        val connector = object : ScrollConnector {
            override fun scrollTo(value: Int, animated: Boolean) = Unit

            override fun setOnSnapshotChangedListener(next: ((ScrollStateSnapshot) -> Unit)?) {
                listener = next
            }
        }
        val observed = mutableListOf<ScrollStateSnapshot>()
        state.addOnSnapshotChangedListener(observed::add)
        state.attach(connector)
        val snapshot = scrollSnapshot(value = 20, maximum = 80, scrolling = true)

        listener?.invoke(snapshot)
        listener?.invoke(snapshot)

        assertEquals(listOf(snapshot), observed)
        assertEquals(80, state.maxValue)
        assertEquals(40, state.viewportSize)
        assertTrue(state.lastScrolledForward)
    }

    @Test
    fun `scrolling to the current value preserves direction and does not notify again`() {
        val state = ScrollState()
        var listener: ((ScrollStateSnapshot) -> Unit)? = null
        val connector = object : ScrollConnector {
            override fun scrollTo(value: Int, animated: Boolean) = Unit

            override fun setOnSnapshotChangedListener(next: ((ScrollStateSnapshot) -> Unit)?) {
                listener = next
            }
        }
        val observed = mutableListOf<ScrollStateSnapshot>()
        state.addOnSnapshotChangedListener(observed::add)
        state.attach(connector)
        listener?.invoke(scrollSnapshot(value = 20, maximum = 80, scrolling = false))

        state.scrollTo(20)

        assertEquals(1, observed.size)
        assertTrue(state.lastScrolledForward)
        assertFalse(state.lastScrolledBackward)
    }

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
        assertEquals("item-8", state.firstVisibleItemKey)
        assertEquals(9, state.lastVisibleItemIndex)
        assertFalse(state.isAtStart)
        assertFalse(state.isAtEnd)
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
    fun `pager state publishes snapshots and distinguishes immediate and animated commands`() {
        val state = PagerState()
        val pageSnapshots = mutableListOf<PagerStateSnapshot>()
        val scrollTargets = mutableListOf<Pair<Int, Boolean>>()
        var platformListener: ((PagerStateSnapshot) -> Unit)? = null
        val connector = object : PagerConnector {
            override fun scrollToPage(page: Int, animated: Boolean) {
                scrollTargets += page to animated
            }

            override fun setOnSnapshotChangedListener(listener: ((PagerStateSnapshot) -> Unit)?) {
                platformListener = listener
            }
        }

        state.addOnSnapshotChangedListener { snapshot -> pageSnapshots += snapshot }
        state.attach(connector)
        state.scrollToPage(7)
        state.animateScrollToPage(8)
        val snapshot = PagerStateSnapshot(
            currentPage = 2,
            settledPage = 1,
            targetPage = 3,
            pageOffset = 0.25f,
            pageCount = 10,
            isScrollInProgress = true,
            canScrollBackward = true,
            canScrollForward = true,
        )
        platformListener?.invoke(snapshot)
        platformListener?.invoke(snapshot)
        state.attach(null)

        assertEquals(listOf(7 to false, 8 to true), scrollTargets)
        assertEquals(listOf(snapshot), pageSnapshots)
        assertEquals(2, state.currentPage)
        assertEquals(1, state.settledPage)
        assertEquals(3, state.targetPage)
        assertTrue(state.isScrollInProgress)
        assertEquals(null, platformListener)
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

    private fun scrollSnapshot(
        value: Int,
        maximum: Int,
        scrolling: Boolean,
    ): ScrollStateSnapshot = ScrollStateSnapshot(
        value = value,
        maxValue = maximum,
        viewportSize = 40,
        isScrollInProgress = scrolling,
        canScrollBackward = value > 0,
        canScrollForward = value < maximum,
        lastScrolledBackward = false,
        lastScrolledForward = true,
    )
}
