package com.viewcompose.ui.node.spec

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
) : NodeSpec
