package com.viewcompose.ui.foundation

/**
 * Creates an immutable partial override of this theme snapshot.
 *
 * When [colors] is replaced without [stateColors], state colors are re-derived from the replacement
 * scheme. Otherwise unspecified families preserve their current values. The result always reports
 * [UiThemeOrigin.Override] while retaining the current darkness and revision metadata.
 *
 * @receiver immutable theme snapshot used as the fallback for omitted families
 * @param colors optional complete semantic color replacement
 * @param stateColors optional state-aware color replacement; omitted values re-derive from [colors]
 * when that family changes
 * @param typography optional typography replacement
 * @param shapes optional component shape replacement
 * @param controls optional component sizing replacement
 * @param interactions optional transient interaction-opacity replacement
 * @param overlays optional modal overlay replacement
 * @return a new theme snapshot; this instance is never mutated
 */
fun UiThemeTokens.override(
    colors: UiColors? = null,
    stateColors: UiStateColors? = null,
    typography: UiTypography? = null,
    shapes: UiShapes? = null,
    controls: UiControlSizing? = null,
    interactions: UiInteractionTokens? = null,
    overlays: UiOverlays? = null,
): UiThemeTokens {
    val overriddenFamilies = buildSet {
        if (colors != null) {
            add("colors")
            add("stateColors")
        }
        if (stateColors != null) add("stateColors")
        if (typography != null) add("typography")
        if (shapes != null) add("shapes")
        if (controls != null) add("controls")
        if (interactions != null) add("interactions")
        if (overlays != null) add("overlays")
    }
    return copy(
        colors = colors ?: this.colors,
        stateColors = stateColors
            ?: colors?.let(UiStateColorDefaults::from)
            ?: this.stateColors,
        typography = typography ?: this.typography,
        shapes = shapes ?: this.shapes,
        controls = controls ?: this.controls,
        interactions = interactions ?: this.interactions,
        overlays = overlays ?: this.overlays,
        metadata = metadata.copy(
            origin = UiThemeOrigin.Override,
            provenance = metadata.provenance.withOrigins(
                tokenPaths = overriddenFamilies,
                origin = UiThemeOrigin.Override,
            ),
        ),
    )
}
