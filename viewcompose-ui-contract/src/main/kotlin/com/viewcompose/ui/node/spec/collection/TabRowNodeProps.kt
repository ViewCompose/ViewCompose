package com.viewcompose.ui.node.spec
import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.node.collection.TabRowTab
import com.viewcompose.ui.state.PagerState

/**
 * Immutable renderer properties for a tab row and its selection indicator.
 *
 * @property tabs ordered tab models
 * @property selectedIndex externally selected tab index
 * @property onTabSelected callback receiving an accepted tab index
 * @property pagerState optional pager state used to synchronize selection and indicator progress
 * @property indicatorColor selected-tab indicator color
 * @property indicatorHeight indicator cross-axis thickness
 * @property indicatorCornerRadius indicator corner radius
 * @property indicatorPosition edge at which the indicator is placed
 * @property indicatorWidthMode strategy used to determine indicator width
 * @property indicatorFixedWidth width used by the fixed-width strategy
 * @property containerColor tab-row surface color
 * @property scrollable whether tabs may exceed and scroll within the available width
 * @property equalWidth whether available width is divided equally among tabs
 * @property rippleColor pressed-state ripple color
 * @property itemSpacing spacing between adjacent tabs
 * @property itemPaddingHorizontal horizontal padding inside each tab
 * @property itemPaddingVertical vertical padding inside each tab
 * @property minItemWidth minimum width of each tab
 */
data class TabRowNodeProps(
    val tabs: List<TabRowTab>,
    val selectedIndex: Int,
    val onTabSelected: ((Int) -> Unit)?,
    val pagerState: PagerState?,
    val indicatorColor: Int,
    val indicatorHeight: UiDp,
    val indicatorCornerRadius: UiDp,
    val indicatorPosition: TabIndicatorPosition,
    val indicatorWidthMode: TabIndicatorWidthMode,
    val indicatorFixedWidth: UiDp,
    val containerColor: Int,
    val scrollable: Boolean,
    val equalWidth: Boolean,
    val rippleColor: Int,
    val itemSpacing: UiDp,
    val itemPaddingHorizontal: UiDp,
    val itemPaddingVertical: UiDp,
    val minItemWidth: UiDp,
) : NodeSpec
