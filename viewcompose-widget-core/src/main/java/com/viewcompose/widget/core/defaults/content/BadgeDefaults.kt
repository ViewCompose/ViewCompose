package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/** Default color, size, and typography tokens for badge components. */
object BadgeDefaults {
    /** Returns the badge container color for the current theme. */
    fun containerColor(): Int = Theme.colors.error

    /** Returns the content color rendered on the badge container. */
    fun contentColor(): Int = Theme.colors.onError

    /** Returns the diameter of a badge that has no textual content. */
    fun dotSize(): UiDp = Theme.controls.badge.dotSize

    /** Returns the height of a badge that contains text. */
    fun pillHeight(): UiDp = Theme.controls.badge.pillHeight

    /** Returns the minimum width of a badge that contains text. */
    fun pillMinWidth(): UiDp = Theme.controls.badge.pillMinWidth

    /** Returns the horizontal padding applied around badge text. */
    fun pillHorizontalPadding(): UiDp = Theme.controls.badge.pillHorizontalPadding

    /** Returns the text style used for badge content. */
    fun textStyle(): UiTextStyle = TextDefaults.labelSmallStyle()
}
