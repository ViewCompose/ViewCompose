package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Default visual, size, and spacing tokens for dropdown menus and their items. */
object DropdownMenuDefaults {
    /** Returns the menu container color. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the primary menu content color. */
    fun contentColor(): Int = Theme.colors.onSurface

    /** Returns the text style used by menu items. */
    fun textStyle(): UiTextStyle = TextDefaults.bodyMediumStyle()

    /** Returns the menu container shape. */
    fun shape(): UiShape = Theme.shapes.medium

    /** Returns the menu elevation. */
    fun elevation(): UiDp = Theme.controls.menu.elevation

    /** Returns the minimum menu width. */
    fun minWidth(): UiDp = Theme.controls.menu.minWidth

    /** Returns the padding above and below the menu's items. */
    fun verticalPadding(): UiDp = Theme.controls.menu.verticalPadding

    /** Returns the default height of one menu item. */
    fun itemHeight(): UiDp = Theme.controls.menu.itemHeight

    /** Returns the horizontal content padding of a menu item. */
    fun itemHorizontalPadding(): UiDp = Theme.controls.menu.itemHorizontalPadding

    /** Returns the square size of a menu-item icon. */
    fun iconSize(): UiDp = Theme.controls.menu.iconSize

    /** Returns the spacing between a leading icon and item text. */
    fun iconToTextSpacing(): UiDp = Theme.controls.menu.iconToTextSpacing

    /** Returns the color used for secondary trailing text. */
    fun trailingTextColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the alpha applied to disabled menu-item content. */
    fun disabledAlpha(): Float = 0.38f
}
