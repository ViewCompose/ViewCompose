package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/** Default visual, typography, and size tokens for navigation bars. */
object NavigationBarDefaults {
    /** Returns the navigation-bar container color. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the icon color for a selected destination. */
    fun selectedIconColor(): Int = Theme.colors.onSecondaryContainer

    /** Returns the icon color for an unselected destination. */
    fun unselectedIconColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the label color for a selected destination. */
    fun selectedLabelColor(): Int = Theme.colors.onSecondaryContainer

    /** Returns the label color for an unselected destination. */
    fun unselectedLabelColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the selection-indicator color. */
    fun indicatorColor(): Int = Theme.colors.secondaryContainer

    /** Returns the interaction ripple color. */
    fun rippleColor(): Int = Theme.colors.ripple

    /** Returns the navigation-bar height. */
    fun height(): UiDp = Theme.controls.navigationBar.height

    /** Returns the square size of a destination icon. */
    fun iconSize(): UiDp = Theme.controls.navigationBar.iconSize

    /** Returns the destination-label text style. */
    fun labelStyle(): UiTextStyle {
        return TextDefaults.labelSmallStyle().copy(
            fontSizeSp = Theme.controls.navigationBar.labelSizeSp,
        )
    }

    /** Returns the destination-label font size. */
    fun labelSizeSp(): UiSp = Theme.controls.navigationBar.labelSizeSp

    /** Returns the notification-badge container color. */
    fun badgeColor(): Int = Theme.colors.error

    /** Returns the notification-badge text color. */
    fun badgeTextColor(): Int = Theme.colors.onError
}
