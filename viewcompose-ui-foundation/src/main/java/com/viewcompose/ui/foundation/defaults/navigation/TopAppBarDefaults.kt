package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default visual, typography, and size tokens for top app bars. */
object TopAppBarDefaults {
    /** Returns the app-bar container color. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the app-bar title color. */
    fun titleColor(): Int = Theme.colors.onSurface

    /** Returns the app-bar title text style. */
    fun titleStyle(): UiTextStyle = TextDefaults.titleMediumStyle()

    /** Returns the app-bar height. */
    fun height(): UiDp = Theme.controls.appBar.topHeight

    /** Returns the horizontal padding around app-bar content. */
    fun horizontalPadding(): UiDp = Theme.controls.appBar.topHorizontalPadding

    /** Returns the spacing between navigation content and the title. */
    fun titleStartPadding(): UiDp = Theme.controls.appBar.topTitleStartPadding
}
