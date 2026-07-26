package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

object DropdownMenuDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun contentColor(): Int = Theme.colors.onSurface

    fun textStyle(): UiTextStyle = TextDefaults.bodyMediumStyle()

    fun shape(): UiShape = Theme.shapes.medium

    fun elevation(): Int = Theme.controls.menu.elevation

    fun minWidth(): Int = Theme.controls.menu.minWidth

    fun verticalPadding(): Int = Theme.controls.menu.verticalPadding

    fun itemHeight(): Int = Theme.controls.menu.itemHeight

    fun itemHorizontalPadding(): Int = Theme.controls.menu.itemHorizontalPadding

    fun iconSize(): Int = Theme.controls.menu.iconSize

    fun iconToTextSpacing(): Int = Theme.controls.menu.iconToTextSpacing

    fun trailingTextColor(): Int = Theme.colors.onSurfaceVariant

    fun disabledAlpha(): Float = 0.38f
}
