package com.viewcompose.widget.core

val LocalTextStyle = uiLocalOf(
    debugName = "TextStyle",
    debugValueFormatter = { style -> "${style.fontSizeSp}sp, weight=${style.fontWeight}" },
) { Theme.typography.bodyMedium }

/**
 * 当前默认文本样式。
 * Current default text style.
 */
object TextStyle {
    val current: UiTextStyle
        get() = UiLocals.current(LocalTextStyle)
}
