package com.viewcompose.widget.core

/**
 * Badge DSL 的默认颜色和尺寸 token。
 * Default color and sizing tokens for the Badge DSL.
 */
object BadgeDefaults {
    fun containerColor(): Int = Theme.colors.error

    fun contentColor(): Int = Theme.colors.onError

    fun dotSize(): Int = Theme.controls.badge.dotSize

    fun pillHeight(): Int = Theme.controls.badge.pillHeight

    fun pillMinWidth(): Int = Theme.controls.badge.pillMinWidth

    fun pillHorizontalPadding(): Int = Theme.controls.badge.pillHorizontalPadding

    fun textStyle(): UiTextStyle = TextDefaults.labelSmallStyle()
}
