package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

object TooltipDefaults {
    fun containerColor(): Int = Theme.colors.inverseSurface

    fun contentColor(): Int = Theme.colors.inverseOnSurface

    fun textStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

    fun shape(): UiShape = Theme.shapes.small

    fun horizontalPadding(): Int = Theme.controls.tooltip.horizontalPadding

    fun verticalPadding(): Int = Theme.controls.tooltip.verticalPadding
}
