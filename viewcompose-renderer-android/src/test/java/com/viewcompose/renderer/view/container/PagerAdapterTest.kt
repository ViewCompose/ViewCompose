package com.viewcompose.renderer.view.container

import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.lazyListItemSessionStrategy
import com.viewcompose.ui.state.PagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PagerAdapterTest {
    @Test
    fun `horizontal pager applies selected page when page snapshot is unchanged`() {
        val view = DeclarativeHorizontalPagerLayout(RuntimeEnvironment.getApplication())
        val pages = listOf(inertPage("first"), inertPage("second"))

        bindHorizontalPager(view, pages, currentPage = 0)
        bindHorizontalPager(view, pages, currentPage = 1)

        assertEquals(1, (view.getChildAt(0) as DeclarativePagerRecyclerView).currentPage)
    }

    @Test
    fun `vertical pager applies selected page when page snapshot is unchanged`() {
        val view = DeclarativeVerticalPagerLayout(RuntimeEnvironment.getApplication())
        val pages = listOf(inertPage("first"), inertPage("second"))

        bindVerticalPager(view, pages, currentPage = 0)
        bindVerticalPager(view, pages, currentPage = 1)

        assertEquals(1, (view.getChildAt(0) as DeclarativePagerRecyclerView).currentPage)
    }

    @Test
    fun `horizontal pager state commands publish settled page once`() {
        val view = DeclarativeHorizontalPagerLayout(RuntimeEnvironment.getApplication())
        val pages = listOf(inertPage("first"), inertPage("second"), inertPage("third"))
        val state = PagerState()
        val settled = mutableListOf<Int>()

        bindHorizontalPager(
            view = view,
            pages = pages,
            currentPage = 1,
            pagerState = state,
            onPageChanged = settled::add,
            userScrollEnabled = false,
        )
        state.scrollToPage(2)

        val nativePager = view.getChildAt(0) as DeclarativePagerRecyclerView
        assertEquals(LinearLayoutManager.HORIZONTAL, nativePager.pagerOrientation)
        assertEquals(false, nativePager.isUserScrollEnabled)
        assertEquals(2, nativePager.currentPage)
        assertEquals(2, state.currentPage)
        assertEquals(2, state.settledPage)
        assertEquals(3, state.pageCount)
        assertEquals(listOf(2), settled)
    }

    @Test
    fun `vertical pager state commands publish settled page once`() {
        val view = DeclarativeVerticalPagerLayout(RuntimeEnvironment.getApplication())
        val pages = listOf(inertPage("first"), inertPage("second"), inertPage("third"))
        val state = PagerState()
        val settled = mutableListOf<Int>()

        bindVerticalPager(
            view = view,
            pages = pages,
            currentPage = 1,
            pagerState = state,
            onPageChanged = settled::add,
            userScrollEnabled = false,
        )
        state.scrollToPage(2)

        val nativePager = view.getChildAt(0) as DeclarativePagerRecyclerView
        assertEquals(LinearLayoutManager.VERTICAL, nativePager.pagerOrientation)
        assertEquals(false, nativePager.isUserScrollEnabled)
        assertEquals(2, nativePager.currentPage)
        assertEquals(2, state.currentPage)
        assertEquals(2, state.settledPage)
        assertEquals(3, state.pageCount)
        assertEquals(listOf(2), settled)
    }

    @Test
    fun `pager disposal detaches portable state commands`() {
        val view = DeclarativeHorizontalPagerLayout(RuntimeEnvironment.getApplication())
        val state = PagerState()

        bindHorizontalPager(
            view = view,
            pages = listOf(inertPage("first"), inertPage("second")),
            currentPage = 0,
            pagerState = state,
        )
        view.dispose()
        state.scrollToPage(1)

        assertEquals(0, (view.getChildAt(0) as DeclarativePagerRecyclerView).currentPage)
    }

    @Test
    fun `horizontal pager move with stable revisions skips page render`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val label = arrayOf("first")
        val updater: (LazyListItemSession) -> Unit = { session ->
            (session as RecordingSession).label = label.single()
            events += "update:${label.single()}"
        }
        val adapter = HorizontalPagerAdapter()
        adapter.submitPages(
            listOf(
                recordingPage(key = "page", events = events, sessionUpdater = updater),
                inertPage(key = "other"),
            ),
        )
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        label[0] = "second"
        adapter.submitPages(
            listOf(
                inertPage(key = "other"),
                recordingPage(key = "page", events = events, sessionUpdater = updater),
            ),
        )
        adapter.onBindViewHolder(holder, 1)

        assertEquals(
            listOf("update:first", "render:first"),
            events,
        )
    }

    @Test
    fun `horizontal pager retains collision-free ids across moves`() {
        val first = CollidingKey("first")
        val second = CollidingKey("second")
        val adapter = HorizontalPagerAdapter()
        adapter.submitPages(listOf(inertPage(first), inertPage(second)))
        val firstId = adapter.getItemId(0)
        val secondId = adapter.getItemId(1)

        adapter.submitPages(listOf(inertPage(second), inertPage(first)))

        assertNotEquals(firstId, secondId)
        assertEquals(secondId, adapter.getItemId(0))
        assertEquals(firstId, adapter.getItemId(1))
    }

    @Test
    fun `horizontal pager refreshes a detached keyed page on attach`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = HorizontalPagerAdapter()
        adapter.submitPages(listOf(recordingPage(label = "first", key = "page", events = events)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        adapter.onViewDetachedFromWindow(holder)

        adapter.submitPages(
            listOf(recordingPage(label = "second", key = "page", events = events, contentRevision = "second")),
        )

        assertEquals(listOf("update:first", "render:first"), events)
        adapter.onViewAttachedToWindow(holder)
        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
    }

    @Test
    fun `vertical pager move with stable revisions skips page render`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val label = arrayOf("first")
        val updater: (LazyListItemSession) -> Unit = { session ->
            (session as RecordingSession).label = label.single()
            events += "update:${label.single()}"
        }
        val adapter = VerticalPagerAdapter()
        adapter.submitPages(
            listOf(
                recordingPage(key = "page", events = events, sessionUpdater = updater),
                inertPage(key = "other"),
            ),
        )
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        label[0] = "second"
        adapter.submitPages(
            listOf(
                inertPage(key = "other"),
                recordingPage(key = "page", events = events, sessionUpdater = updater),
            ),
        )
        adapter.onBindViewHolder(holder, 1)

        assertEquals(
            listOf("update:first", "render:first"),
            events,
        )
    }

    @Test
    fun `vertical pager refreshes a detached keyed page on attach`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = VerticalPagerAdapter()
        adapter.submitPages(listOf(recordingPage(label = "first", key = "page", events = events)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        adapter.onViewDetachedFromWindow(holder)

        adapter.submitPages(
            listOf(recordingPage(label = "second", key = "page", events = events, contentRevision = "second")),
        )

        assertEquals(listOf("update:first", "render:first"), events)
        adapter.onViewAttachedToWindow(holder)
        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
    }

    @Test
    fun `pager view types partition incompatible page content`() {
        val horizontal = HorizontalPagerAdapter()
        val vertical = VerticalPagerAdapter()
        val pages = listOf(
            inertPage(key = "row", contentType = "row"),
            inertPage(key = "card", contentType = "card"),
        )
        horizontal.submitPages(pages)
        vertical.submitPages(pages)

        assertNotEquals(horizontal.getItemViewType(0), horizontal.getItemViewType(1))
        assertNotEquals(vertical.getItemViewType(0), vertical.getItemViewType(1))
    }

    private fun recordingPage(
        label: String = "",
        key: Any,
        events: MutableList<String>,
        sessionUpdater: ((LazyListItemSession) -> Unit)? = null,
        contentRevision: Any? = "stable",
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            sessionStrategy = lazyListItemSessionStrategy(
                create = { RecordingSession(events) },
                update = sessionUpdater ?: { session ->
                    (session as RecordingSession).label = label
                    events += "update:$label"
                },
            ),
        )
    }

    private fun inertPage(
        key: Any,
        contentType: Any? = null,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = "stable",
            contentType = contentType,
            sessionStrategy = lazyListItemSessionStrategy(
                create = {
                    object : LazyListItemSession {
                        override fun render() = true

                        override fun dispose() = Unit
                    }
                },
                update = {},
            ),
        )
    }

    private fun bindHorizontalPager(
        view: DeclarativeHorizontalPagerLayout,
        pages: List<LazyListItem>,
        currentPage: Int,
        pagerState: PagerState? = null,
        onPageChanged: ((Int) -> Unit)? = null,
        userScrollEnabled: Boolean = true,
    ) {
        view.bind(
            pages = pages,
            currentPage = currentPage,
            onPageChanged = onPageChanged,
            offscreenPageLimit = DeclarativePagerRecyclerView.DEFAULT_OFFSCREEN_PAGE_LIMIT,
            pagerState = pagerState,
            userScrollEnabled = userScrollEnabled,
            mountedTreeCacheSize = 2,
        )
    }

    private fun bindVerticalPager(
        view: DeclarativeVerticalPagerLayout,
        pages: List<LazyListItem>,
        currentPage: Int,
        pagerState: PagerState? = null,
        onPageChanged: ((Int) -> Unit)? = null,
        userScrollEnabled: Boolean = true,
    ) {
        view.bind(
            pages = pages,
            currentPage = currentPage,
            onPageChanged = onPageChanged,
            offscreenPageLimit = DeclarativePagerRecyclerView.DEFAULT_OFFSCREEN_PAGE_LIMIT,
            pagerState = pagerState,
            userScrollEnabled = userScrollEnabled,
            mountedTreeCacheSize = 2,
        )
    }

    private class RecordingSession(
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        var label: String = ""

        override fun render(): Boolean {
            events += "render:$label"
            return true
        }

        override fun dispose() = Unit
    }

    private data class CollidingKey(
        val value: String,
    ) {
        override fun hashCode(): Int = 1
    }
}
