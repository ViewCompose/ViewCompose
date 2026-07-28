package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

/**
 * DropdownMenu 与 DropdownMenuItem 的默认颜色、尺寸和间距 token。
 * Default color, sizing, and spacing tokens for DropdownMenu and DropdownMenuItem.
 */
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
