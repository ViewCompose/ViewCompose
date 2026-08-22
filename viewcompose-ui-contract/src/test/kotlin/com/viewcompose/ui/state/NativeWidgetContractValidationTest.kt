package com.viewcompose.ui.state

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.aspectRatio
import com.viewcompose.ui.modifier.maxHeight
import com.viewcompose.ui.modifier.maxWidth
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.LayoutConstraintHostNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.ui.unit.dp
import org.junit.Test

class NativeWidgetContractValidationTest {
    @Test(expected = IllegalArgumentException::class)
    fun `slider rejects a range not divisible by step`() {
        slider(min = 0, max = 10, value = 4, step = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slider rejects a value not aligned to step`() {
        slider(min = 0, max = 12, value = 5, step = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fixed grid rejects zero columns`() {
        GridCells.Fixed(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `adaptive grid rejects non finite minimum`() {
        GridCells.Adaptive(Float.NaN.dp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `grid item rejects zero fixed span`() {
        GridItemSpan.Fixed(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `navigation item rejects a negative badge`() {
        NavigationBarItem(
            key = "inbox",
            label = "Inbox",
            icon = ImageSource.Resource(1),
            badgeCount = -1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lazy grid rejects negative spacing`() {
        LazyVerticalGridNodeProps(
            cells = GridCells.Fixed(2),
            contentPadding = LazyContentPadding.None,
            horizontalSpacing = (-1).dp,
            verticalSpacing = 0.dp,
            items = emptyList(),
            state = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `horizontal pager rejects zero offscreen limit`() {
        HorizontalPagerNodeProps(
            pages = emptyList(),
            currentPage = 0,
            onPageChanged = null,
            offscreenPageLimit = 0,
            pagerState = null,
            userScrollEnabled = true,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vertical pager rejects zero offscreen limit`() {
        VerticalPagerNodeProps(
            pages = emptyList(),
            currentPage = 0,
            onPageChanged = null,
            offscreenPageLimit = 0,
            pagerState = null,
            userScrollEnabled = true,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `layout constraint rejects non positive maximum`() {
        Modifier.maxWidth(0.dp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `layout constraint rejects non finite height`() {
        Modifier.maxHeight(Float.POSITIVE_INFINITY.dp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `layout constraint rejects invalid ratio`() {
        Modifier.aspectRatio(Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `layout host spec rejects invalid direct maximum`() {
        LayoutConstraintHostNodeProps(
            maxWidth = Float.NaN.dp,
            maxHeight = null,
            aspectRatio = null,
            matchHeightConstraintsFirst = false,
            fillWidth = false,
            fillHeight = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pager snapshot rejects page outside known count`() {
        PagerStateSnapshot(
            currentPage = 2,
            settledPage = 0,
            targetPage = 0,
            pageOffset = 0f,
            pageCount = 2,
            isScrollInProgress = false,
            canScrollBackward = true,
            canScrollForward = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pager snapshot rejects contradictory forward capability`() {
        PagerStateSnapshot(
            currentPage = 1,
            settledPage = 1,
            targetPage = 1,
            pageOffset = 0f,
            pageCount = 2,
            isScrollInProgress = false,
            canScrollBackward = true,
            canScrollForward = true,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scroll snapshot rejects value past maximum`() {
        ScrollStateSnapshot(
            value = 11,
            maxValue = 10,
            viewportSize = 20,
            isScrollInProgress = false,
            canScrollBackward = true,
            canScrollForward = false,
            lastScrolledBackward = false,
            lastScrolledForward = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scroll snapshot rejects contradictory backward capability`() {
        ScrollStateSnapshot(
            value = 0,
            maxValue = 10,
            viewportSize = 20,
            isScrollInProgress = false,
            canScrollBackward = true,
            canScrollForward = true,
            lastScrolledBackward = false,
            lastScrolledForward = false,
        )
    }

    private fun slider(min: Int, max: Int, value: Int, step: Int) = SliderNodeProps(
        min = min,
        max = max,
        value = value,
        enabled = true,
        thumbColor = 0,
        trackColor = 0,
        onValueChange = null,
        step = step,
    )
}
