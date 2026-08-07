package com.viewcompose.ui.node

/**
 * Defines resolved colors for transient interaction state layers.
 *
 * Renderers use these colors only while an enabled target is pressed, focused, or hovered. The
 * inactive and disabled states remain transparent. When states overlap, pressed takes precedence
 * over focused, and focused takes precedence over hovered. This value contains no design-system
 * roles or opacity policy; component layers must resolve those before creating a node specification.
 *
 * @property pressedColor ARGB color shown while an enabled target is pressed
 * @property focusedColor ARGB color shown while enabled and focused but not pressed
 * @property hoveredColor ARGB color shown while enabled and hovered without a higher-priority state
 */
data class UiStateLayerColors(
    val pressedColor: Int,
    val focusedColor: Int = pressedColor,
    val hoveredColor: Int = focusedColor,
)
