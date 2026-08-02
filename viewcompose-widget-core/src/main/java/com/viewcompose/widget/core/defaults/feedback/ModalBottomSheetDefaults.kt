package com.viewcompose.widget.core

/** Default visual tokens for modal bottom sheets. */
object ModalBottomSheetDefaults {
    /** Returns the opacity of the scrim behind a visible sheet. */
    fun scrimOpacity(): Float = Theme.overlays.scrimOpacity

    /** Returns the navigation-bar color while the sheet is visible. */
    fun navigationBarColor(): Int = Theme.colors.surface

    /** Returns the sheet container color. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the default content color inside the sheet. */
    fun contentColor(): Int = Theme.colors.onSurface
}
