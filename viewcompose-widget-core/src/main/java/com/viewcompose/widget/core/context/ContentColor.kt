package com.viewcompose.widget.core

val LocalContentColor = uiLocalOf(
    debugName = "ContentColor",
    debugValueFormatter = { color -> "0x${color.toUInt().toString(16).padStart(8, '0')}" },
) { Theme.colors.onSurface }

object ContentColor {
    val current: Int
        get() = UiLocals.current(LocalContentColor)
}
