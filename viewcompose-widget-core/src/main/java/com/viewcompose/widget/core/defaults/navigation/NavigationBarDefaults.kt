package com.viewcompose.widget.core

/**
 * NavigationBar DSL 的默认颜色、标签和徽标 token。
 * Default color, label, and badge tokens for the NavigationBar DSL.
 */
object NavigationBarDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun selectedIconColor(): Int = Theme.colors.onSecondaryContainer

    fun unselectedIconColor(): Int = Theme.colors.onSurfaceVariant

    fun selectedLabelColor(): Int = Theme.colors.onSecondaryContainer

    fun unselectedLabelColor(): Int = Theme.colors.onSurfaceVariant

    fun indicatorColor(): Int = Theme.colors.secondaryContainer

    fun rippleColor(): Int = Theme.colors.ripple

    fun height(): Int = Theme.controls.navigationBar.height

    fun iconSize(): Int = Theme.controls.navigationBar.iconSize

    fun labelStyle(): UiTextStyle {
        return TextDefaults.labelSmallStyle().copy(
            fontSizeSp = Theme.controls.navigationBar.labelSizeSp,
        )
    }

    fun labelSizeSp(): Int = Theme.controls.navigationBar.labelSizeSp

    fun badgeColor(): Int = Theme.colors.error

    fun badgeTextColor(): Int = Theme.colors.onError
}
