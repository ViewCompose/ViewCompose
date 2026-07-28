package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Container Policy Spec Reader 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Container Policy Spec Reader behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerPolicySpecReaderTest {
    @Test
    fun `lazy column spec uses policy defaults`() {
        val spec = CollectionViewBinder.readLazyColumnSpec(
            node = VNode(
                type = NodeType.LazyColumn,
                spec = LazyColumnNodeProps(
                    contentPadding = LazyContentPadding.None,
                    spacing = 0,
                    items = emptyList(),
                ),
            ),
        )

        assertFalse(spec.reusePolicy.sharePool)
        assertFalse(spec.motionPolicy.disableItemAnimator)
        assertTrue(spec.motionPolicy.animateInsert)
        assertTrue(spec.motionPolicy.animateRemove)
        assertTrue(spec.motionPolicy.animateMove)
        assertTrue(spec.motionPolicy.animateChange)
        assertFalse(spec.focusFollowKeyboard)
    }

    @Test
    fun `lazy and pager specs expose override policies`() {
        val reuse = CollectionReusePolicy(sharePool = true)
        val motion = CollectionMotionPolicy(
            disableItemAnimator = true,
            animateInsert = false,
            animateRemove = true,
            animateMove = false,
            animateChange = true,
        )

        val lazyColumnSpec = CollectionViewBinder.readLazyColumnSpec(
            node = VNode(
                type = NodeType.LazyColumn,
                spec = LazyColumnNodeProps(
                    contentPadding = LazyContentPadding.all(8),
                    spacing = 4,
                    items = emptyList(),
                    reusePolicy = reuse,
                    motionPolicy = motion,
                    focusFollowKeyboard = true,
                ),
            ),
        )
        assertEquals(reuse, lazyColumnSpec.reusePolicy)
        assertEquals(motion, lazyColumnSpec.motionPolicy)
        assertTrue(lazyColumnSpec.focusFollowKeyboard)

        val lazyGridSpec = CollectionViewBinder.readLazyVerticalGridSpec(
            node = VNode(
                type = NodeType.LazyVerticalGrid,
                spec = LazyVerticalGridNodeProps(
                    spanCount = 2,
                    contentPadding = LazyContentPadding.all(8),
                    horizontalSpacing = 4,
                    verticalSpacing = 4,
                    items = emptyList(),
                    state = null,
                    reusePolicy = reuse,
                    motionPolicy = motion,
                    focusFollowKeyboard = true,
                ),
            ),
        )
        assertEquals(reuse, lazyGridSpec.reusePolicy)
        assertEquals(motion, lazyGridSpec.motionPolicy)
        assertTrue(lazyGridSpec.focusFollowKeyboard)

        val horizontalPagerSpec = PagerViewBinder.readHorizontalPagerSpec(
            node = VNode(
                type = NodeType.HorizontalPager,
                spec = HorizontalPagerNodeProps(
                    pages = emptyList(),
                    currentPage = 0,
                    onPageChanged = null,
                    offscreenPageLimit = 1,
                    pagerState = null,
                    userScrollEnabled = true,
                    reusePolicy = reuse,
                    motionPolicy = motion,
                ),
            ),
        )
        assertEquals(reuse, horizontalPagerSpec.reusePolicy)
        assertEquals(motion, horizontalPagerSpec.motionPolicy)

        val verticalPagerSpec = PagerViewBinder.readVerticalPagerSpec(
            node = VNode(
                type = NodeType.VerticalPager,
                spec = VerticalPagerNodeProps(
                    pages = emptyList(),
                    currentPage = 0,
                    onPageChanged = null,
                    offscreenPageLimit = 1,
                    pagerState = null,
                    userScrollEnabled = true,
                    reusePolicy = reuse,
                    motionPolicy = motion,
                    focusFollowKeyboard = true,
                ),
            ),
        )
        assertEquals(reuse, verticalPagerSpec.reusePolicy)
        assertEquals(motion, verticalPagerSpec.motionPolicy)
        assertTrue(verticalPagerSpec.focusFollowKeyboard)
    }

    @Test
    fun `scrollable column spec exposes focus follow flag`() {
        val spec = ScrollableViewBinder.readScrollableColumnSpec(
            node = VNode(
                type = NodeType.ScrollableColumn,
                spec = ScrollableColumnNodeProps(
                    spacing = 12,
                    arrangement = com.viewcompose.ui.layout.MainAxisArrangement.SpaceBetween,
                    horizontalAlignment = com.viewcompose.ui.layout.HorizontalAlignment.Center,
                    focusFollowKeyboard = true,
                ),
            ),
        )

        assertTrue(spec.focusFollowKeyboard)
        assertEquals(12, spec.linearSpec.spacing)
    }
}
