package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Collection Advanced Binding 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Collection Advanced Binding behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.viewcompose.renderer.view.container.DeclarativeLazyListView
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.focus.LazyLinearLayoutManager
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            layoutManager = LazyLinearLayoutManager(context)
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
                items = listOf(item("row")),
                state = null,
                reverseLayout = true,
                userScrollEnabled = false,
                prefetchPolicy = LazyLayoutPrefetchPolicy(
                    initialPrefetchItemCount = 6,
                    itemViewCacheSize = 4,
                ),
                reusePolicy = CollectionReusePolicy(),
                motionPolicy = CollectionMotionPolicy(),
                focusFollowKeyboard = false,
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
            spanCount = 3,
            contentPadding = PaddingPx(0, 0, 0, 0),
            horizontalSpacing = 0,
            verticalSpacing = 0,
            items = listOf(
                item(key = "header", span = Int.MAX_VALUE),
                item(key = "wide", span = 2),
                item(key = "single", span = 1),
            ),
            state = null,
            reverseLayout = true,
            userScrollEnabled = false,
            prefetchPolicy = LazyLayoutPrefetchPolicy(
                initialPrefetchItemCount = 5,
                itemViewCacheSize = 3,
            ),
        )

        val layoutManager = view.layoutManager as GridLayoutManager
        assertEquals(3, layoutManager.spanSizeLookup.getSpanSize(0))
        assertEquals(2, layoutManager.spanSizeLookup.getSpanSize(1))
        assertEquals(1, layoutManager.spanSizeLookup.getSpanSize(2))
        assertEquals(5, layoutManager.initialPrefetchItemCount)
        assertTrue(layoutManager.reverseLayout)
        assertFalse(view.userScrollEnabled)
    }

    private fun item(
        key: Any,
        span: Int = 1,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = key,
            span = span,
            sessionFactory = LazyListItemSessionFactory {
                object : LazyListItemSession {
                    override fun render() = Unit
                    override fun dispose() = Unit
                }
            },
        )
    }
}
