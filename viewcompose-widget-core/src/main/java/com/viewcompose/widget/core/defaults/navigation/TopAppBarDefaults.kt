package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * TopAppBar DSL 的默认高度、间距和标题 token。
 * Default height, spacing, and title tokens for the TopAppBar DSL.
 */
object TopAppBarDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun titleColor(): Int = Theme.colors.onSurface

    fun titleStyle(): UiTextStyle = TextDefaults.titleMediumStyle()

    fun height(): UiDp = Theme.controls.appBar.topHeight

    fun horizontalPadding(): UiDp = Theme.controls.appBar.topHorizontalPadding

    fun titleStartPadding(): UiDp = Theme.controls.appBar.topTitleStartPadding
}
