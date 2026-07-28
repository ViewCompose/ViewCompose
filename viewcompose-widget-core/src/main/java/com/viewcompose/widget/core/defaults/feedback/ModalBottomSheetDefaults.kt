package com.viewcompose.widget.core

/**
 * ModalBottomSheet overlay 的默认颜色和遮罩 token。
 * Default color and scrim tokens for the ModalBottomSheet overlay.
 */
object ModalBottomSheetDefaults {
    fun scrimOpacity(): Float = Theme.overlays.scrimOpacity
    fun navigationBarColor(): Int = Theme.colors.surface
    fun containerColor(): Int = Theme.colors.surface
    fun contentColor(): Int = Theme.colors.onSurface
}
