package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default visual and size tokens for bottom app bars. */
object BottomAppBarDefaults {
    /** Returns the app-bar container color. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the app-bar height. */
    fun height(): UiDp = Theme.controls.appBar.bottomHeight

    /** Returns the horizontal padding around app-bar content. */
    fun horizontalPadding(): UiDp = Theme.controls.appBar.bottomHorizontalPadding

    /** Returns the app-bar elevation. */
    fun elevation(): UiDp = Theme.controls.appBar.bottomElevation
}
