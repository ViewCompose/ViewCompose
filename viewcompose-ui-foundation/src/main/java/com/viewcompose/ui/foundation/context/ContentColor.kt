package com.viewcompose.ui.foundation

/** Default semantic content color inherited by text and icon components. */
val LocalContentColor = uiLocalOf(
    debugName = "ContentColor",
    debugValueFormatter = { color -> "0x${color.toUInt().toString(16).padStart(8, '0')}" },
) { Theme.colors.onSurface }

/** Exposes the current default content color, normally provided by a theme or surface. */
object ContentColor {
    /** Current packed ARGB content color. */
    val current: Int
        get() = UiLocals.current(LocalContentColor)
}
