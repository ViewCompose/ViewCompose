package com.viewcompose

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.paging.PagingLazyColumn
import com.viewcompose.paging.PagingLifecyclePolicy
import com.viewcompose.paging.ViewComposePagingItems
import com.viewcompose.paging.collectAsViewComposePagingItems
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.unit.dp
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device proof for the compact Paging table without shipping a premature Demo route. */
@RunWith(AndroidJUnit4::class)
class PagingCompactTableDeviceTest {
    @Test
    fun millionPositionPlaceholderTable_jumpsDropsAndStaysMemoryBounded() {
        val pager = Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = true,
                initialLoadSize = PAGE_SIZE,
                maxSize = MAX_LOADED_ITEMS,
                jumpThreshold = JUMP_THRESHOLD,
            ),
            pagingSourceFactory = { MillionRowPagingSource() },
        )
        val pagingItems = AtomicReference<ViewComposePagingItems<Row>>()
        val recyclerView = AtomicReference<RecyclerView>()
        val activatedIds = Collections.synchronizedSet(mutableSetOf<Int>())
        val disposedIds = Collections.synchronizedSet(mutableSetOf<Int>())
        lateinit var container: FrameLayout
        lateinit var session: RenderSession

        launchDemoActivity(P1CoreCapabilitiesTestActivity::class.java).use { scenario ->
            forceGc()
            val baselinePssKb = Debug.getPss()
            scenario.onActivity { activity ->
                container = FrameLayout(activity).also { host ->
                    activity.addContentView(
                        host,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                session = renderInto(container) {
                    val items = pager.flow.collectAsViewComposePagingItems(
                        lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    )
                    pagingItems.set(items)
                    PagingLazyColumn(
                        items = items,
                        key = Row::id,
                        contentType = { "paging-row" },
                        contentRevision = Row::id,
                        placeholderContentRevision = "placeholder-v1",
                        placeholderContent = { index ->
                            Text(
                                text = "placeholder-$index",
                                modifier = Modifier.height(ITEM_HEIGHT),
                            )
                        },
                        placeholderContentType = "paging-row",
                        modifier = Modifier.fillMaxSize(),
                    ) { row ->
                        DisposableEffect(row.id) {
                            activatedIds += row.id
                            onDispose {
                                activatedIds -= row.id
                                disposedIds += row.id
                            }
                        }
                        Text(
                            text = "row-${row.id}",
                            modifier = Modifier.height(ITEM_HEIGHT),
                        )
                    }
                }
            }

            try {
                waitUntil(scenario, "initial million-position presentation") { _ ->
                    val list = container.findRecyclerView()?.also(recyclerView::set)
                    list?.adapter?.itemCount == TOTAL_ITEMS &&
                        pagingItems.get()?.loadedItemCount?.let { it in 1..MAX_JUMP_LOADED_ITEMS } == true
                }
                val initiallyActivated = synchronized(activatedIds) { activatedIds.toSet() }
                assertTrue("At least one loaded row must own an active item Session.", initiallyActivated.isNotEmpty())
                assertEquals(TOTAL_ITEMS, recyclerView.get().adapter?.itemCount)

                forceGc()
                val initialPssDeltaKb = (Debug.getPss() - baselinePssKb).coerceAtLeast(0)
                assertTrue(
                    "A million positional placeholders grew PSS by ${initialPssDeltaKb} KiB.",
                    initialPssDeltaKb < MAX_INITIAL_PSS_DELTA_KB,
                )
                Log.i(TEST_LOG_TAG, "Million initial PSS delta: $initialPssDeltaKb KiB")

                val jumpStartedAt = SystemClock.elapsedRealtime()
                scenario.onActivity {
                    val list = recyclerView.get()
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        TOTAL_ITEMS - 1,
                        0,
                    )
                    list.requestLayout()
                }
                waitUntil(scenario, "RecyclerView layout at the final position") { _ ->
                    val list = recyclerView.get()
                    val layoutManager = list.layoutManager as LinearLayoutManager
                    if (layoutManager.findLastVisibleItemPosition() < TOTAL_ITEMS - 1) {
                        layoutManager.scrollToPositionWithOffset(TOTAL_ITEMS - 1, 0)
                        list.requestLayout()
                    }
                    layoutManager.findLastVisibleItemPosition() == TOTAL_ITEMS - 1
                }
                waitUntil(scenario, "last row after the million-position jump") { _ ->
                    pagingItems.get()?.peek(TOTAL_ITEMS - 1)?.id == TOTAL_ITEMS - 1
                }
                val jumpDurationMs = SystemClock.elapsedRealtime() - jumpStartedAt
                assertTrue(
                    "Local million-position jump took ${jumpDurationMs} ms.",
                    jumpDurationMs < MAX_JUMP_DURATION_MS,
                )
                Log.i(TEST_LOG_TAG, "Million jump duration: $jumpDurationMs ms")

                waitUntil(scenario, "disposed initial item Sessions after the jump") { _ ->
                    synchronized(disposedIds) { disposedIds.containsAll(initiallyActivated) }
                }
                scenario.onActivity {
                    val items = pagingItems.get()
                    assertNotNull(items)
                    assertEquals(TOTAL_ITEMS, items.itemCount)
                    assertTrue(items.loadedItemCount <= MAX_JUMP_LOADED_ITEMS)
                    assertNull(items.peek(0))
                    assertEquals(TOTAL_ITEMS - 1, items.peek(TOTAL_ITEMS - 1)?.id)
                    Log.i(
                        TEST_LOG_TAG,
                        "Million final loaded count: ${items.loadedItemCount}; " +
                            "disposed initial Sessions: ${disposedIds.size}",
                    )
                }
            } finally {
                scenario.onActivity {
                    session.dispose()
                    (container.parent as? ViewGroup)?.removeView(container)
                }
            }
        }
    }

    @Test
    fun boundedPageWindow_dropsOldPagesAndDisposesTheirVisibleSessions() {
        val pager = Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = true,
                initialLoadSize = PAGE_SIZE,
                maxSize = MAX_LOADED_ITEMS,
            ),
            pagingSourceFactory = { BoundedRowPagingSource() },
        )
        val pagingItems = AtomicReference<ViewComposePagingItems<Row>>()
        val recyclerView = AtomicReference<RecyclerView>()
        val activatedIds = Collections.synchronizedSet(mutableSetOf<Int>())
        val disposedIds = Collections.synchronizedSet(mutableSetOf<Int>())
        lateinit var container: FrameLayout
        lateinit var session: RenderSession

        launchDemoActivity(P1CoreCapabilitiesTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                container = FrameLayout(activity).also { host ->
                    activity.addContentView(
                        host,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                session = renderInto(container) {
                    val items = pager.flow.collectAsViewComposePagingItems(
                        lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    )
                    pagingItems.set(items)
                    PagingLazyColumn(
                        items = items,
                        key = Row::id,
                        contentType = { "paging-row" },
                        contentRevision = Row::id,
                        placeholderContentRevision = "placeholder-v1",
                        placeholderContent = { index ->
                            Text(
                                text = "placeholder-$index",
                                modifier = Modifier.height(ITEM_HEIGHT),
                            )
                        },
                        placeholderContentType = "paging-row",
                        modifier = Modifier.fillMaxSize(),
                    ) { row ->
                        DisposableEffect(row.id) {
                            activatedIds += row.id
                            onDispose {
                                activatedIds -= row.id
                                disposedIds += row.id
                            }
                        }
                        Text(
                            text = "row-${row.id}",
                            modifier = Modifier.height(ITEM_HEIGHT),
                        )
                    }
                }
            }

            try {
                waitUntil(scenario, "initial bounded presentation") { _ ->
                    val list = container.findRecyclerView()?.also(recyclerView::set)
                    list?.adapter?.itemCount == BOUNDED_TOTAL_ITEMS &&
                        pagingItems.get()?.loadedItemCount?.let { it in 1..MAX_LOADED_ITEMS } == true
                }
                scenario.onActivity {
                    val loadedIndices = pagingItems.get().loadedIndices(BOUNDED_TOTAL_ITEMS)
                    Log.i(TEST_LOG_TAG, "Initial bounded loaded indices: $loadedIndices")
                    assertTrue("Initial loaded indices were $loadedIndices.", 0 in loadedIndices)
                }
                val initiallyActivated = synchronized(activatedIds) { activatedIds.toSet() }
                assertTrue("At least one initial loaded row must own an item Session.", initiallyActivated.isNotEmpty())

                DROP_SCROLL_TARGETS.forEach { target ->
                    scenario.onActivity {
                        val list = recyclerView.get()
                        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, 0)
                        list.requestLayout()
                    }
                    waitUntil(scenario, "loaded bounded row $target") { _ ->
                        pagingItems.get()?.peek(target)?.id == target
                    }
                }

                waitUntil(scenario, "dropped initial page") { _ ->
                    pagingItems.get()?.peek(0) == null
                }
                waitUntil(scenario, "disposed initial visible item Sessions") { _ ->
                    synchronized(disposedIds) { disposedIds.containsAll(initiallyActivated) }
                }
                scenario.onActivity {
                    val items = pagingItems.get()
                    assertNotNull(items)
                    assertEquals(BOUNDED_TOTAL_ITEMS, items.itemCount)
                    assertTrue(items.loadedItemCount <= MAX_LOADED_ITEMS)
                    assertNull(items.peek(0))
                    assertEquals(DROP_SCROLL_TARGETS.last(), items.peek(DROP_SCROLL_TARGETS.last())?.id)
                    Log.i(
                        TEST_LOG_TAG,
                        "Bounded final loaded count: ${items.loadedItemCount}; " +
                            "disposed initial Sessions: ${disposedIds.size}",
                    )
                }
            } finally {
                scenario.onActivity {
                    session.dispose()
                    (container.parent as? ViewGroup)?.removeView(container)
                }
            }
        }
    }

    private fun waitUntil(
        scenario: ActivityScenario<P1CoreCapabilitiesTestActivity>,
        description: String,
        condition: (P1CoreCapabilitiesTestActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MS
        var satisfied = false
        while (!satisfied && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> satisfied = condition(activity) }
            if (!satisfied) Thread.sleep(POLL_INTERVAL_MS)
        }
        assertTrue("Timed out waiting for $description.", satisfied)
    }

    private fun forceGc() {
        repeat(2) {
            System.gc()
            System.runFinalization()
            Thread.sleep(GC_SETTLE_MS)
        }
    }

    private fun View.findRecyclerView(): RecyclerView? {
        if (this is RecyclerView) return this
        if (this is ViewGroup) {
            repeat(childCount) { index ->
                getChildAt(index).findRecyclerView()?.let { return it }
            }
        }
        return null
    }

    private data class Row(val id: Int)

    private class MillionRowPagingSource : PagingSource<Int, Row>() {
        override val jumpingSupported: Boolean = true

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Row> {
            val start = (params.key ?: 0).coerceIn(0, TOTAL_ITEMS - 1)
            val endExclusive = (start + params.loadSize).coerceAtMost(TOTAL_ITEMS)
            Log.i(TEST_LOG_TAG, "Million load ${params::class.java.simpleName}: $start until $endExclusive")
            return LoadResult.Page(
                data = (start until endExclusive).map(::Row),
                prevKey = start.takeIf { it > 0 }?.let { (it - PAGE_SIZE).coerceAtLeast(0) },
                nextKey = endExclusive.takeIf { it < TOTAL_ITEMS },
                itemsBefore = start,
                itemsAfter = TOTAL_ITEMS - endExclusive,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, Row>): Int? {
            return state.anchorPosition?.let { anchor ->
                (anchor - state.config.initialLoadSize / 2).coerceIn(0, TOTAL_ITEMS - 1)
            }
        }
    }

    private class BoundedRowPagingSource : PagingSource<Int, Row>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Row> {
            val start = (params.key ?: 0).coerceIn(0, BOUNDED_TOTAL_ITEMS - 1)
            val endExclusive = (start + params.loadSize).coerceAtMost(BOUNDED_TOTAL_ITEMS)
            Log.i(TEST_LOG_TAG, "Bounded load ${params::class.java.simpleName}: $start until $endExclusive")
            return LoadResult.Page(
                data = (start until endExclusive).map(::Row),
                prevKey = start.takeIf { it > 0 }?.let { (it - PAGE_SIZE).coerceAtLeast(0) },
                nextKey = endExclusive.takeIf { it < BOUNDED_TOTAL_ITEMS },
                itemsBefore = start,
                itemsAfter = BOUNDED_TOTAL_ITEMS - endExclusive,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, Row>): Int? = state.anchorPosition?.let { anchor ->
            (anchor - state.config.initialLoadSize / 2).coerceIn(0, BOUNDED_TOTAL_ITEMS - 1)
        }
    }

    private fun ViewComposePagingItems<Row>.loadedIndices(total: Int): List<Int> = buildList {
        repeat(total) { index ->
            if (peek(index) != null) add(index)
        }
    }

    private companion object {
        const val TOTAL_ITEMS = 1_000_000
        const val BOUNDED_TOTAL_ITEMS = 512
        const val PAGE_SIZE = 32
        const val PREFETCH_DISTANCE = 2
        const val MAX_LOADED_ITEMS = 96
        const val MAX_JUMP_LOADED_ITEMS = PAGE_SIZE * 3
        const val JUMP_THRESHOLD = 64
        const val MAX_INITIAL_PSS_DELTA_KB = 64 * 1024
        const val MAX_JUMP_DURATION_MS = 15_000L
        const val WAIT_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 25L
        const val GC_SETTLE_MS = 100L
        const val TEST_LOG_TAG = "PagingCompactDevice"
        val DROP_SCROLL_TARGETS = listOf(31, 63, 95, 127, 159, 191)
        val ITEM_HEIGHT = 48.dp
    }
}
