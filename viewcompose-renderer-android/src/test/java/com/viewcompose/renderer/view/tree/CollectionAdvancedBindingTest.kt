package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.renderer.view.container.DeclarativeLazyListView
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.LazyGridCellsPx
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.asLazyItemTable
import com.viewcompose.ui.node.lazyListItemSessionStrategy
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CollectionAdvancedBindingTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `lazy column maps asymmetric padding reverse scrolling and prefetch`() {
        val view = DeclarativeLazyListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = LazyListAdapter()
        }
        CollectionViewBinder.bindLazyColumn(
            view = view,
            spec = CollectionViewBinder.LazyColumnSpec(
                contentPadding = PaddingPx(
                    left = 3,
                    top = 5,
                    right = 7,
                    bottom = 11,
                ),
                spacing = 13,
                items = listOf(item("row")).asLazyItemTable(),
                state = null,
                reverseLayout = true,
                userScrollEnabled = false,
                prefetchPolicy = LazyLayoutPrefetchPolicy(
                    nestedInitialPrefetchItemCount = 6,
                    itemViewCacheSize = 4,
                ),
                reusePolicy = CollectionReusePolicy(),
                motionPolicy = CollectionMotionPolicy(),
            ),
        )

        val layoutManager = view.layoutManager as LinearLayoutManager
        assertTrue(layoutManager.reverseLayout)
        assertEquals(6, layoutManager.initialPrefetchItemCount)
        assertFalse(view.userScrollEnabled)
        assertEquals(3, view.paddingStart)
        assertEquals(5, view.paddingTop)
        assertEquals(7, view.paddingEnd)
        assertEquals(11, view.paddingBottom)
        assertFalse(view.clipToPadding)
    }

    @Test
    fun `lazy grid clamps full line and custom spans to span count`() {
        val view = DeclarativeLazyVerticalGridLayout(context)
        view.bind(
            cells = com.viewcompose.renderer.view.container.LazyGridCellsPx.Fixed(3),
            contentPadding = PaddingPx(0, 0, 0, 0),
            horizontalSpacing = 0,
            verticalSpacing = 0,
            items = listOf(
                item(key = "header", span = com.viewcompose.ui.node.policy.GridItemSpan.FullLine),
                item(key = "wide", span = com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2)),
                item(key = "single"),
            ).asLazyItemTable(),
            state = null,
            reverseLayout = true,
            userScrollEnabled = false,
            prefetchPolicy = LazyLayoutPrefetchPolicy(
                nestedInitialPrefetchItemCount = 5,
                itemViewCacheSize = 3,
            ),
            mountedTreeCacheSize = 2,
        )

        val layoutManager = view.layoutManager as GridLayoutManager
        assertEquals(3, layoutManager.spanSizeLookup.getSpanSize(0))
        assertEquals(2, layoutManager.spanSizeLookup.getSpanSize(1))
        assertEquals(1, layoutManager.spanSizeLookup.getSpanSize(2))
        assertEquals(5, layoutManager.initialPrefetchItemCount)
        assertTrue(layoutManager.reverseLayout)
        assertFalse(view.userScrollEnabled)
    }

    @Test
    fun `adaptive grid recomputes columns without replacing adapter`() {
        val view = DeclarativeLazyVerticalGridLayout(context)
        view.bind(
            cells = com.viewcompose.renderer.view.container.LazyGridCellsPx.Adaptive(100),
            contentPadding = PaddingPx(0, 0, 0, 0),
            horizontalSpacing = 0,
            verticalSpacing = 0,
            items = listOf(
                item(
                    key = "header",
                    span = com.viewcompose.ui.node.policy.GridItemSpan.FullLine,
                ),
            ).asLazyItemTable(),
            state = null,
            reverseLayout = false,
            userScrollEnabled = true,
            prefetchPolicy = LazyLayoutPrefetchPolicy(),
            mountedTreeCacheSize = 0,
        )
        val adapter = view.adapter

        view.measure(exactly(250), exactly(400))
        assertEquals(2, (view.layoutManager as GridLayoutManager).spanCount)
        assertEquals(2, (view.layoutManager as GridLayoutManager).spanSizeLookup.getSpanSize(0))

        view.measure(exactly(350), exactly(400))
        assertEquals(3, (view.layoutManager as GridLayoutManager).spanCount)
        assertEquals(3, (view.layoutManager as GridLayoutManager).spanSizeLookup.getSpanSize(0))
        assertSame(adapter, view.adapter)
    }

    @Test
    fun `adaptive grid keeps a positive physical minimum for subpixel dp`() {
        val cells = CollectionViewBinder.run {
            GridCells.Adaptive(0.1f.dp).toPixels(
                UiEnvironmentValues(density = UiDensity(density = 1f, fontScale = 1f)),
            )
        }

        assertEquals(LazyGridCellsPx.Adaptive(minSize = 1), cells)
    }

    @Test
    fun `adaptive grid column resolution does not overflow with extreme spacing`() {
        val view = DeclarativeLazyVerticalGridLayout(context)
        view.bind(
            cells = LazyGridCellsPx.Adaptive(1),
            contentPadding = PaddingPx(0, 0, 0, 0),
            horizontalSpacing = Int.MAX_VALUE,
            verticalSpacing = 0,
            items = emptyList<LazyListItem>().asLazyItemTable(),
            state = null,
            reverseLayout = false,
            userScrollEnabled = true,
            prefetchPolicy = LazyLayoutPrefetchPolicy(),
            mountedTreeCacheSize = 0,
        )

        view.measure(exactly(350), exactly(400))

        assertEquals(1, (view.layoutManager as GridLayoutManager).spanCount)
    }

    private fun item(
        key: Any,
        span: com.viewcompose.ui.node.policy.GridItemSpan = com.viewcompose.ui.node.policy.GridItemSpan.Single,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = key,
            span = span,
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

    private fun exactly(size: Int): Int =
        android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
}
