package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default visual and spacing tokens for tab-row components. */
object TabRowDefaults {
    /** Returns the tab-row container color for the current theme. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the active-tab indicator color. */
    fun indicatorColor(): Int = Theme.colors.primary

    /** Returns the content color for inactive tabs. */
    fun inactiveContentColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the active-tab indicator height. */
    fun indicatorHeight(): UiDp = 3.dp

    /** Returns the active-tab indicator corner radius. */
    fun indicatorCornerRadius(): UiDp = 2.dp

    /** Returns the tab interaction ripple color. */
    fun rippleColor(): Int = Theme.colors.ripple

    /** Returns the horizontal content padding for each tab. */
    fun itemPaddingHorizontal(): UiDp = 16.dp

    /** Returns the vertical content padding for each tab. */
    fun itemPaddingVertical(): UiDp = 12.dp

    /** Returns the minimum width allocated to a tab. */
    fun minItemWidth(): UiDp = 48.dp
}
