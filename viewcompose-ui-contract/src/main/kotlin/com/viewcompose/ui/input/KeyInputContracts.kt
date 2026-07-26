package com.viewcompose.ui.input

enum class KeyEventType {
    KeyDown,
    KeyUp,
    Unknown,
}

enum class Key {
    Unknown,
    Character,
    Enter,
    NumPadEnter,
    Tab,
    Spacebar,
    DirectionLeft,
    DirectionRight,
    DirectionUp,
    DirectionDown,
    PageUp,
    PageDown,
    MoveHome,
    MoveEnd,
    Back,
    Escape,
    Backspace,
    Delete,
}

/**
 * Platform-independent hardware key event.
 *
 * [nativeKeyCode] remains available for Android-specific keys without forcing the common
 * contract to mirror the entire platform key-code table.
 */
data class KeyEvent(
    val key: Key,
    val type: KeyEventType,
    val nativeKeyCode: Int,
    val unicodeCodePoint: Int = 0,
    val repeatCount: Int = 0,
    val isAltPressed: Boolean = false,
    val isCtrlPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
)
