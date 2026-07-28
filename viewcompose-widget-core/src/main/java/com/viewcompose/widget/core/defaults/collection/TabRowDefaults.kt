package com.viewcompose.widget.core

/**
 * TabRow DSL 的默认容器、指示器和 item 间距 token。
 * Default container, indicator, and item spacing tokens for the TabRow DSL.
 */
object TabRowDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun indicatorColor(): Int = Theme.colors.primary

    fun inactiveContentColor(): Int = Theme.colors.onSurfaceVariant

    fun indicatorHeight(): Int = 3.dp

    fun indicatorCornerRadius(): Int = 2.dp

    fun rippleColor(): Int = Theme.colors.ripple

    fun itemPaddingHorizontal(): Int = 16.dp

    fun itemPaddingVertical(): Int = 12.dp

    fun minItemWidth(): Int = 48.dp
}
