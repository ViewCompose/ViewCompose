package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * TabRow DSL 的默认容器、指示器和 item 间距 token。
 * Default container, indicator, and item spacing tokens for the TabRow DSL.
 */
object TabRowDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun indicatorColor(): Int = Theme.colors.primary

    fun inactiveContentColor(): Int = Theme.colors.onSurfaceVariant

    fun indicatorHeight(): UiDp = 3.dp

    fun indicatorCornerRadius(): UiDp = 2.dp

    fun rippleColor(): Int = Theme.colors.ripple

    fun itemPaddingHorizontal(): UiDp = 16.dp

    fun itemPaddingVertical(): UiDp = 12.dp

    fun minItemWidth(): UiDp = 48.dp
}
