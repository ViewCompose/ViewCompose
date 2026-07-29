package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * Badge DSL 的默认颜色和尺寸 token。
 * Default color and sizing tokens for the Badge DSL.
 */
object BadgeDefaults {
    fun containerColor(): Int = Theme.colors.error

    fun contentColor(): Int = Theme.colors.onError

    fun dotSize(): UiDp = Theme.controls.badge.dotSize

    fun pillHeight(): UiDp = Theme.controls.badge.pillHeight

    fun pillMinWidth(): UiDp = Theme.controls.badge.pillMinWidth

    fun pillHorizontalPadding(): UiDp = Theme.controls.badge.pillHorizontalPadding

    fun textStyle(): UiTextStyle = TextDefaults.labelSmallStyle()
}
