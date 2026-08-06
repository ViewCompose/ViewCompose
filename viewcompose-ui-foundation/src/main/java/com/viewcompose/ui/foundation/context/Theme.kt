package com.viewcompose.ui.foundation

private val LocalTheme = uiLocalOf(
    debugName = "Theme",
    debugValueFormatter = { tokens ->
        "${tokens.metadata.origin}, dark=${tokens.metadata.isDark}, revision=${tokens.metadata.revision}"
    },
    defaultFactory = UiThemeDefaults::light,
)

/** Exposes the immutable theme snapshot and its token families for the current composition. */
object Theme {
    /** Current complete theme snapshot. */
    val current: UiThemeTokens
        get() = UiLocals.current(LocalTheme)

    /** Current semantic color scheme. */
    val colors: UiColors
        get() = current.colors

    /** Current state-aware component colors. */
    val stateColors: UiStateColors
        get() = current.stateColors

    /** Current typography tiers. */
    val typography: UiTypography
        get() = current.typography

    /** Current component shape tiers. */
    val shapes: UiShapes
        get() = current.shapes

    /** Current core component sizing tokens. */
    val controls: UiControlSizing
        get() = current.controls

    /** Current modal overlay tokens. */
    val overlays: UiOverlays
        get() = current.overlays
}

/**
 * Provides one platform-independent theme snapshot while building [content].
 *
 * Design-system adapters resolve platform resources into [UiThemeTokens] before entering this
 * provider. When [tokens] is omitted, the framework's neutral light defaults are used. Nested
 * providers restore the previous theme after [content] returns.
 *
 * @sample com.viewcompose.ui.foundation.samples.themeProviderSample
 * @throws IllegalArgumentException if more than one theme source is supplied
 */
fun UiTreeBuilder.UiTheme(
    tokens: UiThemeTokens = UiThemeDefaults.light(),
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(local = LocalTheme, value = tokens) {
        content()
    }
}

/**
 * Provides selected token families over the current theme while building [content].
 *
 * When [colors] changes without an explicit [stateColors], state colors are re-derived from the new
 * scheme. The resulting metadata origin is [UiThemeOrigin.Override].
 */
fun UiTreeBuilder.UiThemeOverride(
    colors: UiColors? = null,
    stateColors: UiStateColors? = null,
    typography: UiTypography? = null,
    shapes: UiShapes? = null,
    controls: UiControlSizing? = null,
    overlays: UiOverlays? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalTheme,
        value = Theme.current.override(
            colors = colors,
            stateColors = stateColors,
            typography = typography,
            shapes = shapes,
            controls = controls,
            overlays = overlays,
        ),
    ) {
        content()
    }
}

/**
 * Computes and provides selected token families from their current values.
 *
 * Every non-null transformation runs immediately and exactly once for this tree build before the
 * delegated provider executes [content]. Color changes without a state-color transformation
 * re-derive state colors through [UiThemeTokens.override].
 */
fun UiTreeBuilder.UiThemeOverride(
    colors: (UiColors.() -> UiColors)? = null,
    stateColors: (UiStateColors.() -> UiStateColors)? = null,
    typography: (UiTypography.() -> UiTypography)? = null,
    shapes: (UiShapes.() -> UiShapes)? = null,
    controls: (UiControlSizing.() -> UiControlSizing)? = null,
    overlays: (UiOverlays.() -> UiOverlays)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    UiThemeOverride(
        colors = colors?.invoke(Theme.colors),
        stateColors = stateColors?.invoke(Theme.stateColors),
        typography = typography?.invoke(Theme.typography),
        shapes = shapes?.invoke(Theme.shapes),
        controls = controls?.invoke(Theme.controls),
        overlays = overlays?.invoke(Theme.overlays),
        content = content,
    )
}
