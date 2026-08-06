package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.view.container.DeclarativeLazyListView
import com.viewcompose.renderer.view.container.DeclarativePullToRefreshLayout
import com.viewcompose.renderer.view.container.HorizontalPagerAdapter
import com.viewcompose.renderer.view.container.VerticalPagerAdapter
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LazyShadowDecorationIntegrationTest {
    @Test
    fun `lazy recycler allows item decoration overflow while container owns viewport clip`() {
        val recyclerView = DeclarativeLazyListView(RuntimeEnvironment.getApplication())

        FrameworkRecyclerViewDefaults.applyLazyColumnDefaults(recyclerView)

        assertFalse(recyclerView.clipChildren)
    }

    @Test
    fun `lazy and pager holders reuse their container as the session decoration host`() {
        val parent = RecyclerView(RuntimeEnvironment.getApplication())

        val lazyHolder = LazyListAdapter().onCreateViewHolder(parent, 0)
        val horizontalPagerHolder = HorizontalPagerAdapter().onCreateViewHolder(parent, 0)
        val verticalPagerHolder = VerticalPagerAdapter().onCreateViewHolder(parent, 0)

        assertTrue(lazyHolder.itemView is ViewDecorationHostLayout)
        assertTrue(horizontalPagerHolder.itemView is ViewDecorationHostLayout)
        assertTrue(verticalPagerHolder.itemView is ViewDecorationHostLayout)
    }

    @Test
    fun `pull to refresh uses a viewport clipped decoration host`() {
        val view = ViewNodeFactory.createView(
            context = RuntimeEnvironment.getApplication(),
            node = VNode(
                type = NodeType.PullToRefresh,
                spec = PullToRefreshNodeProps(
                    isRefreshing = false,
                    onRefresh = null,
                    indicatorColor = 0,
                ),
            ),
            createAndroidView = null,
        )

        assertTrue(view is DeclarativePullToRefreshLayout)
        assertFalse((view as DeclarativePullToRefreshLayout).clipChildren)
    }
}
