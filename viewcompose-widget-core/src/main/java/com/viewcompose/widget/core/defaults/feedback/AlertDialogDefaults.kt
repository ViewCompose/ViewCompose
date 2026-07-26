package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

object AlertDialogDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun titleColor(): Int = Theme.colors.onSurface

    fun textColor(): Int = Theme.colors.onSurfaceVariant

    fun iconTint(): Int = Theme.colors.primary

    fun titleStyle(): UiTextStyle = TextDefaults.titleLargeStyle()

    fun textStyle(): UiTextStyle = TextDefaults.bodyMediumStyle()

    fun shape(): UiShape = Theme.shapes.medium

    fun contentPadding(): Int = 24.dp

    fun titleToTextSpacing(): Int = 16.dp

    fun textToButtonsSpacing(): Int = 24.dp

    fun buttonSpacing(): Int = 8.dp

    fun iconBottomSpacing(): Int = 16.dp

    fun iconSize(): Int = 24.dp

    fun minWidth(): Int = 280.dp
}
