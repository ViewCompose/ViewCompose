package com.viewcompose.widget.core

/**
 * Creates an immutable partial override of this theme snapshot.
 *
 * When [colors] is replaced without [stateColors], state colors are re-derived from the replacement
 * scheme. Otherwise unspecified families preserve their current values. The result always reports
 * [UiThemeOrigin.Override] while retaining the current darkness and revision metadata.
 *
 * @return a new theme snapshot; this instance is never mutated
 */
fun UiThemeTokens.override(
    colors: UiColors? = null,
    stateColors: UiStateColors? = null,
    typography: UiTypography? = null,
    shapes: UiShapes? = null,
    controls: UiControlSizing? = null,
    overlays: UiOverlays? = null,
): UiThemeTokens {
    return copy(
        colors = colors ?: this.colors,
        stateColors = stateColors
            ?: colors?.let(UiStateColorDefaults::from)
            ?: this.stateColors,
        typography = typography ?: this.typography,
        shapes = shapes ?: this.shapes,
        controls = controls ?: this.controls,
        overlays = overlays ?: this.overlays,
        metadata = metadata.copy(origin = UiThemeOrigin.Override),
    )
}
