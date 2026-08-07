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

    /** Current transient interaction-state opacity tokens. */
    val interactions: UiInteractionTokens
        get() = current.interactions

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
 *
 * @receiver active tree builder whose current theme scope is replaced for [content]
 * @param colors optional complete semantic color replacement
 * @param stateColors optional state-aware color replacement
 * @param typography optional typography replacement
 * @param shapes optional component shape replacement
 * @param controls optional component sizing replacement
 * @param interactions optional transient interaction-opacity replacement
 * @param overlays optional modal overlay replacement
 * @param content subtree built under the merged immutable theme snapshot
 */
fun UiTreeBuilder.UiThemeOverride(
    colors: UiColors? = null,
    stateColors: UiStateColors? = null,
    typography: UiTypography? = null,
    shapes: UiShapes? = null,
    controls: UiControlSizing? = null,
    interactions: UiInteractionTokens? = null,
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
            interactions = interactions,
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
 *
 * @receiver active tree builder whose current theme scope is transformed for [content]
 * @param colors optional transformation of the current semantic colors
 * @param stateColors optional transformation of the current state-aware colors
 * @param typography optional transformation of the current typography
 * @param shapes optional transformation of the current component shapes
 * @param controls optional transformation of the current component sizing
 * @param interactions optional transformation of the current interaction-opacity policy
 * @param overlays optional transformation of the current modal overlay tokens
 * @param content subtree built under the transformed immutable theme snapshot
 */
fun UiTreeBuilder.UiThemeOverride(
    colors: (UiColors.() -> UiColors)? = null,
    stateColors: (UiStateColors.() -> UiStateColors)? = null,
    typography: (UiTypography.() -> UiTypography)? = null,
    shapes: (UiShapes.() -> UiShapes)? = null,
    controls: (UiControlSizing.() -> UiControlSizing)? = null,
    interactions: (UiInteractionTokens.() -> UiInteractionTokens)? = null,
    overlays: (UiOverlays.() -> UiOverlays)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    UiThemeOverride(
        colors = colors?.invoke(Theme.colors),
        stateColors = stateColors?.invoke(Theme.stateColors),
        typography = typography?.invoke(Theme.typography),
        shapes = shapes?.invoke(Theme.shapes),
        controls = controls?.invoke(Theme.controls),
        interactions = interactions?.invoke(Theme.interactions),
        overlays = overlays?.invoke(Theme.overlays),
        content = content,
    )
}
