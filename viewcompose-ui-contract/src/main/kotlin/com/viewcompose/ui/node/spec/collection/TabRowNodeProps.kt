package com.viewcompose.ui.node.spec
import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.node.collection.TabRowTab
import com.viewcompose.ui.state.PagerState

/**
 * TabRow 节点的 tab 集合、选中项和指示器属性。
 * Tab collection, selection, and indicator properties for a TabRow node.
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
