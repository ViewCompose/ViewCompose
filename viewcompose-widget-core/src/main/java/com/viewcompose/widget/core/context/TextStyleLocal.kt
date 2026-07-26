package com.viewcompose.widget.core

val LocalTextStyle = uiLocalOf(
    debugName = "TextStyle",
    debugValueFormatter = { style -> "${style.fontSizeSp}sp, weight=${style.fontWeight}" },
) { Theme.typography.bodyMedium }

object TextStyle {
    val current: UiTextStyle
        get() = UiLocals.current(LocalTextStyle)
}
