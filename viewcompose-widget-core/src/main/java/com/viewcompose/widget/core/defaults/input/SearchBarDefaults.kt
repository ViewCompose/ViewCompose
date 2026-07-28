package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

/**
 * SearchBar 组合控件的默认 token。
 * Default tokens for the SearchBar composite widget.
 *
 * 颜色来自当前 Theme，输入文字样式复用正文大号排版以保持搜索场景的可读性。
 * Colors come from the current Theme, and input text reuses body-large typography for search readability.
 */
object SearchBarDefaults {
    fun containerColor(): Int = Theme.colors.surfaceVariant

    fun contentColor(): Int = Theme.colors.onSurface

    fun placeholderColor(): Int = Theme.colors.onSurfaceVariant

    fun iconColor(): Int = Theme.colors.onSurfaceVariant

    fun height(): Int = Theme.controls.searchBar.height

    fun shape(): UiShape = Theme.shapes.large

    fun horizontalPadding(): Int = Theme.controls.searchBar.horizontalPadding

    fun iconSize(): Int = Theme.controls.searchBar.iconSize

    fun iconSpacing(): Int = Theme.controls.searchBar.iconSpacing

    fun textStyle(): UiTextStyle = TextDefaults.bodyLargeStyle()

    fun elevation(): Int = Theme.controls.searchBar.elevation
}
