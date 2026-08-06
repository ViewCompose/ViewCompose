package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * Resolves SearchBar defaults from the current theme.
 *
 * Editable text uses large body typography for search readability.
 */
object SearchBarDefaults {
    /** Returns the current variant surface color. */
    fun containerColor(): Int = Theme.colors.surfaceVariant

    /** Returns primary content color for text. */
    fun contentColor(): Int = Theme.colors.onSurface

    /** Returns secondary content color for placeholder text. */
    fun placeholderColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns secondary content color for icons. */
    fun iconColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns minimum search-bar height. */
    fun height(): UiDp = Theme.controls.searchBar.height

    /** Returns the current large theme shape. */
    fun shape(): UiShape = Theme.shapes.large

    /** Returns start and end content padding. */
    fun horizontalPadding(): UiDp = Theme.controls.searchBar.horizontalPadding

    /** Returns leading and trailing icon size. */
    fun iconSize(): UiDp = Theme.controls.searchBar.iconSize

    /** Returns spacing between an icon and editable text. */
    fun iconSpacing(): UiDp = Theme.controls.searchBar.iconSpacing

    /** Returns large body typography for editable text. */
    fun textStyle(): UiTextStyle = TextDefaults.bodyLargeStyle()

    /** Returns resting search-bar elevation. */
    fun elevation(): UiDp = Theme.controls.searchBar.elevation
}
