package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

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

    fun elevation(): UiDp = Theme.controls.menu.elevation

    fun minWidth(): UiDp = Theme.controls.menu.minWidth

    fun verticalPadding(): UiDp = Theme.controls.menu.verticalPadding

    fun itemHeight(): UiDp = Theme.controls.menu.itemHeight

    fun itemHorizontalPadding(): UiDp = Theme.controls.menu.itemHorizontalPadding

    fun iconSize(): UiDp = Theme.controls.menu.iconSize

    fun iconToTextSpacing(): UiDp = Theme.controls.menu.iconToTextSpacing

    fun trailingTextColor(): Int = Theme.colors.onSurfaceVariant

    fun disabledAlpha(): Float = 0.38f
}
