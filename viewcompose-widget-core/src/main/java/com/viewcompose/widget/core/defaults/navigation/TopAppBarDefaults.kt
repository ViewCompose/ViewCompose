package com.viewcompose.widget.core

/**
 * TopAppBar DSL 的默认高度、间距和标题 token。
 * Default height, spacing, and title tokens for the TopAppBar DSL.
 */
object TopAppBarDefaults {
    fun containerColor(): Int = Theme.colors.surface

    fun titleColor(): Int = Theme.colors.onSurface

    fun titleStyle(): UiTextStyle = TextDefaults.titleMediumStyle()

    fun height(): Int = Theme.controls.appBar.topHeight

    fun horizontalPadding(): Int = Theme.controls.appBar.topHorizontalPadding

    fun titleStartPadding(): Int = Theme.controls.appBar.topTitleStartPadding
}
