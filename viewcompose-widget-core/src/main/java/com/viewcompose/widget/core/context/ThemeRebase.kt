package com.viewcompose.widget.core

/**
 * 基于当前 token 创建覆盖后的主题快照。
 * Creates an overridden theme snapshot from the current tokens.
 *
 * 当 colors 被覆盖但 stateColors 未显式传入时，会重新派生默认状态色。
 * When colors are overridden but stateColors are not provided, default state colors are derived again.
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
