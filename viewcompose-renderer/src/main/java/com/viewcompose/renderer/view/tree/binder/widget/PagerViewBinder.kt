package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.ui.state.PagerState
import com.viewcompose.ui.shape.UiShape

/**
 * 绑定分页、分段控件和标签栏节点，保持 PagerState 与 Android 容器滚动状态一致。
 * Binds pagers, segmented controls, and tab rows while keeping PagerState aligned with Android
 * container scroll state.
 */
internal object PagerViewBinder {
    data class SegmentedControlSpec(
        val items: List<SegmentedControlItem>,
        val selectedIndex: Int,
        val onSelectionChange: ((Int) -> Unit)?,
        val enabled: Boolean,
        val backgroundColor: Int,
        val indicatorColor: Int,
        val shape: UiShape,
        val textColor: Int,
        val selectedTextColor: Int,
        val rippleColor: Int,
        val textSizeSp: Int,
        val fontWeight: Int? = null,
        val fontFamily: UiFontFamily? = null,
        val letterSpacingEm: Float? = null,
        val lineHeightSp: Int? = null,
        val includeFontPadding: Boolean = false,
        val paddingHorizontal: Int,
        val paddingVertical: Int,
    )

    data class HorizontalPagerSpec(
        val pages: List<LazyListItem>,
        val currentPage: Int,
        val onPageChanged: ((Int) -> Unit)?,
        val offscreenPageLimit: Int,
        val pagerState: PagerState?,
        val userScrollEnabled: Boolean,
        val reusePolicy: CollectionReusePolicy,
        val motionPolicy: CollectionMotionPolicy,
    )

    data class VerticalPagerSpec(
        val pages: List<LazyListItem>,
        val currentPage: Int,
        val onPageChanged: ((Int) -> Unit)?,
        val offscreenPageLimit: Int,
        val pagerState: PagerState?,
        val userScrollEnabled: Boolean,
        val reusePolicy: CollectionReusePolicy,
        val motionPolicy: CollectionMotionPolicy,
        val focusFollowKeyboard: Boolean,
    )

    data class TabRowSpec(
        val tabs: List<com.viewcompose.ui.node.collection.TabRowTab>,
        val selectedIndex: Int,
        val onTabSelected: ((Int) -> Unit)?,
        val pagerState: PagerState?,
        val indicatorColor: Int,
        val indicatorHeight: Int,
        val indicatorCornerRadius: Int,
        val indicatorPosition: TabIndicatorPosition,
        val indicatorWidthMode: TabIndicatorWidthMode,
        val indicatorFixedWidth: Int,
        val containerColor: Int,
        val scrollable: Boolean,
        val equalWidth: Boolean,
        val rippleColor: Int,
        val itemSpacing: Int,
        val itemPaddingHorizontal: Int,
        val itemPaddingVertical: Int,
        val minItemWidth: Int,
    )

    fun bindSegmentedControl(
        view: DeclarativeSegmentedControlLayout,
        spec: SegmentedControlSpec,
    ) {
        view.bind(
            items = spec.items,
            selectedIndex = spec.selectedIndex,
            onSelectionChange = spec.onSelectionChange,
            enabled = spec.enabled,
            backgroundColor = spec.backgroundColor,
            indicatorColor = spec.indicatorColor,
            shape = spec.shape,
            textColor = spec.textColor,
            selectedTextColor = spec.selectedTextColor,
            rippleColor = spec.rippleColor,
            textSizeSp = spec.textSizeSp,
            fontWeight = spec.fontWeight,
            fontFamily = spec.fontFamily,
            letterSpacingEm = spec.letterSpacingEm,
            lineHeightSp = spec.lineHeightSp,
            includeFontPadding = spec.includeFontPadding,
            paddingHorizontal = spec.paddingHorizontal,
            paddingVertical = spec.paddingVertical,
        )
    }

    fun bindHorizontalPager(
        view: DeclarativeHorizontalPagerLayout,
        spec: HorizontalPagerSpec,
    ) {
        view.applyRecyclerDefaults(
            sharePool = spec.reusePolicy.sharePool,
            disableItemAnimator = spec.motionPolicy.disableItemAnimator,
            animateInsert = spec.motionPolicy.animateInsert,
            animateRemove = spec.motionPolicy.animateRemove,
            animateMove = spec.motionPolicy.animateMove,
            animateChange = spec.motionPolicy.animateChange,
        )
        view.bind(
            pages = spec.pages,
            currentPage = spec.currentPage,
            onPageChanged = spec.onPageChanged,
            offscreenPageLimit = spec.offscreenPageLimit,
            pagerState = spec.pagerState,
            userScrollEnabled = spec.userScrollEnabled,
        )
    }

    fun bindTabRow(
        view: DeclarativeTabRowLayout,
        spec: TabRowSpec,
    ) {
        view.bind(
            tabs = spec.tabs,
            selectedIndex = spec.selectedIndex,
            onTabSelected = spec.onTabSelected,
            pagerState = spec.pagerState,
            indicatorColor = spec.indicatorColor,
            indicatorHeight = spec.indicatorHeight,
            indicatorCornerRadius = spec.indicatorCornerRadius,
            indicatorPosition = spec.indicatorPosition,
            indicatorWidthMode = spec.indicatorWidthMode,
            indicatorFixedWidth = spec.indicatorFixedWidth,
            containerColor = spec.containerColor,
            scrollable = spec.scrollable,
            equalWidth = spec.equalWidth,
            rippleColor = spec.rippleColor,
            itemSpacing = spec.itemSpacing,
            itemPaddingHorizontal = spec.itemPaddingHorizontal,
            itemPaddingVertical = spec.itemPaddingVertical,
            minItemWidth = spec.minItemWidth,
        )
    }

    fun bindVerticalPager(
        view: DeclarativeVerticalPagerLayout,
        spec: VerticalPagerSpec,
    ) {
        view.applyRecyclerDefaults(
            sharePool = spec.reusePolicy.sharePool,
            disableItemAnimator = spec.motionPolicy.disableItemAnimator,
            animateInsert = spec.motionPolicy.animateInsert,
            animateRemove = spec.motionPolicy.animateRemove,
            animateMove = spec.motionPolicy.animateMove,
            animateChange = spec.motionPolicy.animateChange,
        )
        view.setFocusFollowKeyboardEnabled(spec.focusFollowKeyboard)
        view.bind(
            pages = spec.pages,
            currentPage = spec.currentPage,
            onPageChanged = spec.onPageChanged,
            offscreenPageLimit = spec.offscreenPageLimit,
            pagerState = spec.pagerState,
            userScrollEnabled = spec.userScrollEnabled,
        )
    }

    fun readSegmentedControlSpec(node: VNode): SegmentedControlSpec {
        val spec = node.requireSpec<SegmentedControlNodeProps>()
        return SegmentedControlSpec(
            items = spec.items,
            selectedIndex = spec.selectedIndex,
            onSelectionChange = spec.onSelectionChange,
            enabled = spec.enabled,
            backgroundColor = spec.backgroundColor,
            indicatorColor = spec.indicatorColor,
            shape = spec.shape,
            textColor = spec.textColor,
            selectedTextColor = spec.selectedTextColor,
            rippleColor = spec.rippleColor,
            textSizeSp = spec.textSizeSp,
            fontWeight = spec.fontWeight,
            fontFamily = spec.fontFamily,
            letterSpacingEm = spec.letterSpacingEm,
            lineHeightSp = spec.lineHeightSp,
            includeFontPadding = spec.includeFontPadding,
            paddingHorizontal = spec.paddingHorizontal,
            paddingVertical = spec.paddingVertical,
        )
    }

    fun readHorizontalPagerSpec(node: VNode): HorizontalPagerSpec {
        val spec = node.requireSpec<HorizontalPagerNodeProps>()
        return HorizontalPagerSpec(
            pages = spec.pages,
            currentPage = spec.currentPage,
            onPageChanged = spec.onPageChanged,
            offscreenPageLimit = spec.offscreenPageLimit,
            pagerState = spec.pagerState,
            userScrollEnabled = spec.userScrollEnabled,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
        )
    }

    fun readTabRowSpec(node: VNode): TabRowSpec {
        val spec = node.requireSpec<TabRowNodeProps>()
        return TabRowSpec(
            tabs = spec.tabs,
            selectedIndex = spec.selectedIndex,
            onTabSelected = spec.onTabSelected,
            pagerState = spec.pagerState,
            indicatorColor = spec.indicatorColor,
            indicatorHeight = spec.indicatorHeight,
            indicatorCornerRadius = spec.indicatorCornerRadius,
            indicatorPosition = spec.indicatorPosition,
            indicatorWidthMode = spec.indicatorWidthMode,
            indicatorFixedWidth = spec.indicatorFixedWidth,
            containerColor = spec.containerColor,
            scrollable = spec.scrollable,
            equalWidth = spec.equalWidth,
            rippleColor = spec.rippleColor,
            itemSpacing = spec.itemSpacing,
            itemPaddingHorizontal = spec.itemPaddingHorizontal,
            itemPaddingVertical = spec.itemPaddingVertical,
            minItemWidth = spec.minItemWidth,
        )
    }

    fun readVerticalPagerSpec(node: VNode): VerticalPagerSpec {
        val spec = node.requireSpec<VerticalPagerNodeProps>()
        return VerticalPagerSpec(
            pages = spec.pages,
            currentPage = spec.currentPage,
            onPageChanged = spec.onPageChanged,
            offscreenPageLimit = spec.offscreenPageLimit,
            pagerState = spec.pagerState,
            userScrollEnabled = spec.userScrollEnabled,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
            focusFollowKeyboard = spec.focusFollowKeyboard,
        )
    }
}
