package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * AlertDialog 组合控件的默认颜色、排版和间距 token。
 * Default color, typography, and spacing tokens for the AlertDialog composite widget.
 */
object AlertDialogDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun titleColor(): Int = Theme.colors.onSurface

    fun textColor(): Int = Theme.colors.onSurfaceVariant

    fun iconTint(): Int = Theme.colors.primary

    fun titleStyle(): UiTextStyle = TextDefaults.titleLargeStyle()

    fun textStyle(): UiTextStyle = TextDefaults.bodyMediumStyle()

    fun shape(): UiShape = Theme.shapes.medium

    fun contentPadding(): UiDp = 24.dp

    fun titleToTextSpacing(): UiDp = 16.dp

    fun textToButtonsSpacing(): UiDp = 24.dp

    fun buttonSpacing(): UiDp = 8.dp

    fun iconBottomSpacing(): UiDp = 16.dp

    fun iconSize(): UiDp = 24.dp

    fun minWidth(): UiDp = 280.dp
}
