package com.viewcompose.widget.core

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
