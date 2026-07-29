package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * PlainTooltip 的默认容器、内容色和内边距 token。
 * Default container, content color, and padding tokens for PlainTooltip.
 */
object TooltipDefaults {
    fun containerColor(): Int = Theme.colors.inverseSurface

    fun contentColor(): Int = Theme.colors.inverseOnSurface

    fun textStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

    fun shape(): UiShape = Theme.shapes.small

    fun horizontalPadding(): UiDp = Theme.controls.tooltip.horizontalPadding

    fun verticalPadding(): UiDp = Theme.controls.tooltip.verticalPadding
}
