package com.viewcompose.widget.core

/**
 * BottomAppBar DSL 的默认容器、尺寸和阴影 token。
 * Default container, sizing, and elevation tokens for the BottomAppBar DSL.
 */
object BottomAppBarDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun height(): Int = Theme.controls.appBar.bottomHeight

    fun horizontalPadding(): Int = Theme.controls.appBar.bottomHorizontalPadding

    fun elevation(): Int = Theme.controls.appBar.bottomElevation
}
