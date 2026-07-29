package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * NavigationBar 节点的 item 集合和选中状态属性。
 * Item collection and selected-state properties for a NavigationBar node.
 */
data class NavigationBarNodeProps(
    val items: List<NavigationBarItem>,
    val selectedIndex: Int,
    val onItemSelected: ((Int) -> Unit)?,
    val containerColor: Int,
    val selectedIconColor: Int,
    val unselectedIconColor: Int,
    val selectedLabelColor: Int,
    val unselectedLabelColor: Int,
    val indicatorColor: Int,
    val rippleColor: Int,
    val iconSize: UiDp,
    val labelSizeSp: UiSp,
    val labelFontWeight: Int? = null,
    val labelFontFamily: UiFontFamily? = null,
    val labelLetterSpacingEm: Float? = null,
    val labelLineHeightSp: UiSp? = null,
    val labelIncludeFontPadding: Boolean = false,
    val badgeColor: Int,
    val badgeTextColor: Int,
) : NodeSpec
