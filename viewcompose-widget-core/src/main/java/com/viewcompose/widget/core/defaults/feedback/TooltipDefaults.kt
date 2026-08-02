package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Default visual and spacing tokens for plain tooltips. */
object TooltipDefaults {
    /** Returns the tooltip container color. */
    fun containerColor(): Int = Theme.colors.inverseSurface

    /** Returns the tooltip content color. */
    fun contentColor(): Int = Theme.colors.inverseOnSurface

    /** Returns the text style used by tooltip content. */
    fun textStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

    /** Returns the tooltip container shape. */
    fun shape(): UiShape = Theme.shapes.small

    /** Returns the horizontal padding around tooltip content. */
    fun horizontalPadding(): UiDp = Theme.controls.tooltip.horizontalPadding

    /** Returns the vertical padding around tooltip content. */
    fun verticalPadding(): UiDp = Theme.controls.tooltip.verticalPadding
}
