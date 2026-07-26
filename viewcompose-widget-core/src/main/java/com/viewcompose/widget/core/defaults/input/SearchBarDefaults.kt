package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

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
