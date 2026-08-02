package com.viewcompose.ui.input

/**
 * Classifies a platform-independent key event as a press, release, or unrecognized action.
 */
enum class KeyEventType {
    KeyDown,
    KeyUp,
    Unknown,
}

/**
 * Identifies the logical key meanings recognized by ViewCompose input contracts.
 *
 * [Character] represents printable input whose code point is carried by
 * [KeyEvent.unicodeCodePoint]. [Unknown] preserves an event that has no logical mapping; consumers
 * may inspect [KeyEvent.nativeKeyCode] when Android-specific handling is required.
 */
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
 *
 * @property key logical key meaning, or [Key.Unknown] when no mapping exists
 * @property type press/release classification
 * @property nativeKeyCode unmodified platform key code, with platform-defined units
 * @property unicodeCodePoint Unicode code point for character input, or `0` when unavailable
 * @property repeatCount number of repeated key-down events reported by the platform
 * @property isAltPressed whether an Alt modifier was active for this event
 * @property isCtrlPressed whether a Control modifier was active for this event
 * @property isMetaPressed whether a Meta modifier was active for this event
 * @property isShiftPressed whether a Shift modifier was active for this event
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
