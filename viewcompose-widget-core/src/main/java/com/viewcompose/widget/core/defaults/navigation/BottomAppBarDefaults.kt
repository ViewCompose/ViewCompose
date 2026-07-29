package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * BottomAppBar DSL 的默认容器、尺寸和阴影 token。
 * Default container, sizing, and elevation tokens for the BottomAppBar DSL.
 */
object BottomAppBarDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun height(): UiDp = Theme.controls.appBar.bottomHeight

    fun horizontalPadding(): UiDp = Theme.controls.appBar.bottomHorizontalPadding

    fun elevation(): UiDp = Theme.controls.appBar.bottomElevation
}
