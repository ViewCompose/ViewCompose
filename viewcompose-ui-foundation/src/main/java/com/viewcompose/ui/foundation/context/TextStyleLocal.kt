package com.viewcompose.ui.foundation

/** Default text style inherited by text-bearing components. */
val LocalTextStyle = uiLocalOf(
    debugName = "TextStyle",
    debugValueFormatter = { style -> "${style.fontSizeSp}sp, weight=${style.fontWeight}" },
) { Theme.typography.bodyMedium }

/** Exposes the default text style for the current composition scope. */
object TextStyle {
    /** Current text style, defaulting to [Theme.typography]'s medium body style. */
    val current: UiTextStyle
        get() = UiLocals.current(LocalTextStyle)
}
