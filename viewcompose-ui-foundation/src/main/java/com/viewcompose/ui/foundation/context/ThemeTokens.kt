package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiSp
import kotlin.math.roundToInt

/**
 * Stores the complete semantic color scheme as packed ARGB integers.
 *
 * Container roles are paired with `on*` content roles. Defaults derive missing content colors from
 * luminance and map newer surface/container roles onto the required base colors, so custom themes
 * can start with the smaller required constructor surface and override roles incrementally.
 *
 * @property background application background color
 * @property onBackground content color placed on [background]
 * @property surface default component surface color
 * @property surfaceVariant alternate component surface color
 * @property surfaceDim dimmest surface-container role
 * @property surfaceBright brightest surface-container role
 * @property surfaceContainerLowest lowest-emphasis surface container
 * @property surfaceContainerLow low-emphasis surface container
 * @property surfaceContainer standard surface container
 * @property surfaceContainerHigh high-emphasis surface container
 * @property surfaceContainerHighest highest-emphasis surface container
 * @property onSurface primary content color placed on surface roles
 * @property onSurfaceVariant secondary content color placed on surface roles
 * @property primary primary accent and action color
 * @property onPrimary content color placed on [primary]
 * @property primaryContainer lower-emphasis primary container color
 * @property onPrimaryContainer content color placed on [primaryContainer]
 * @property secondary secondary accent and action color
 * @property onSecondary content color placed on [secondary]
 * @property secondaryContainer lower-emphasis secondary container color
 * @property onSecondaryContainer content color placed on [secondaryContainer]
 * @property tertiary tertiary accent color
 * @property onTertiary content color placed on [tertiary]
 * @property tertiaryContainer lower-emphasis tertiary container color
 * @property onTertiaryContainer content color placed on [tertiaryContainer]
 * @property error error accent and action color
 * @property onError content color placed on [error]
 * @property errorContainer lower-emphasis error container color
 * @property onErrorContainer content color placed on [errorContainer]
 * @property success semantic success color
 * @property warning semantic warning color
 * @property info semantic informational color
 * @property outline high-emphasis outline and divider color
 * @property outlineVariant low-emphasis outline and divider color
 * @property surfaceTint tint used to communicate tonal surface elevation
 * @property inverseSurface high-contrast surface color for inverse regions
 * @property inverseOnSurface content color placed on [inverseSurface]
 * @property inversePrimary primary accent suitable for [inverseSurface]
 * @property scrim color placed behind modal surfaces before opacity is applied
 */
data class UiColors(
    val background: Int,
    val onBackground: Int = contentColorFor(background),
    val surface: Int,
    val surfaceVariant: Int,
    val surfaceDim: Int = surface,
    val surfaceBright: Int = surface,
    val surfaceContainerLowest: Int = background,
    val surfaceContainerLow: Int = surface,
    val surfaceContainer: Int = surface,
    val surfaceContainerHigh: Int = surfaceVariant,
    val surfaceContainerHighest: Int = surfaceVariant,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int = contentColorFor(primary),
    val primaryContainer: Int = primary,
    val onPrimaryContainer: Int = contentColorFor(primaryContainer),
    val secondary: Int,
    val onSecondary: Int = contentColorFor(secondary),
    val secondaryContainer: Int = secondary,
    val onSecondaryContainer: Int = contentColorFor(secondaryContainer),
    val tertiary: Int = secondary,
    val onTertiary: Int = contentColorFor(tertiary),
    val tertiaryContainer: Int = tertiary,
    val onTertiaryContainer: Int = contentColorFor(tertiaryContainer),
    val error: Int,
    val onError: Int = contentColorFor(error),
    val errorContainer: Int = error,
    val onErrorContainer: Int = contentColorFor(errorContainer),
    val success: Int,
    val warning: Int,
    val info: Int,
    val outline: Int,
    val outlineVariant: Int = outline,
    val surfaceTint: Int = primary,
    val inverseSurface: Int = onSurface,
    val inverseOnSurface: Int = background,
    val inversePrimary: Int = primary,
    val scrim: Int = 0xFF000000.toInt(),
)

/**
 * Stores one semantic color across enabled and interactive states.
 *
 * [resolve] uses disabled, pressed, focused, checked, selected, then default precedence. This means
 * callers should pass the complete simultaneous state instead of preselecting one branch.
 *
 * @sample com.viewcompose.ui.foundation.samples.themeStateColorSample
 * @property defaultColor color used when no higher-priority state is active
 * @property disabledColor color used whenever the component is disabled
 * @property pressedColor color used while an enabled component is pressed
 * @property focusedColor color used while enabled and focused but not pressed
 * @property checkedColor color used while enabled and checked without higher-priority state
 * @property selectedColor color used while enabled and selected without higher-priority state
 */
data class UiStateColor(
    val defaultColor: Int,
    val disabledColor: Int = defaultColor,
    val pressedColor: Int = defaultColor,
    val focusedColor: Int = pressedColor,
    val checkedColor: Int = defaultColor,
    val selectedColor: Int = checkedColor,
) {
    /**
     * Resolves the color for a complete interaction-state snapshot.
     *
     * @return the first matching state color in disabled, pressed, focused, checked, selected order
     */
    fun resolve(
        enabled: Boolean = true,
        pressed: Boolean = false,
        focused: Boolean = false,
        checked: Boolean = false,
        selected: Boolean = false,
    ): Int {
        return when {
            !enabled -> disabledColor
            pressed -> pressedColor
            focused -> focusedColor
            checked -> checkedColor
            selected -> selectedColor
            else -> defaultColor
        }
    }
}

/**
 * Groups state-aware colors commonly consumed by component defaults.
 *
 * @property primaryText primary text state colors
 * @property secondaryText secondary text state colors
 * @property control normal control state colors
 * @property controlActivated activated control state colors
 */
data class UiStateColors(
    val primaryText: UiStateColor,
    val secondaryText: UiStateColor,
    val control: UiStateColor,
    val controlActivated: UiStateColor,
)

/**
 * Defines design-system-neutral opacity policy for transient component state layers.
 *
 * Values are fractions in `0f..1f`. Component defaults combine them with their semantic content
 * role before emitting a renderer contract; renderers never interpret these values directly.
 *
 * @property pressedStateLayerOpacity opacity used while an enabled component is pressed
 * @property focusedStateLayerOpacity opacity used while enabled and focused but not pressed
 * @property hoveredStateLayerOpacity opacity used while enabled and hovered without a
 * higher-priority state
 * @throws IllegalArgumentException if any opacity is not finite or is outside `0f..1f`
 */
data class UiInteractionTokens(
    val pressedStateLayerOpacity: Float,
    val focusedStateLayerOpacity: Float = pressedStateLayerOpacity,
    val hoveredStateLayerOpacity: Float = focusedStateLayerOpacity,
) {
    init {
        require(pressedStateLayerOpacity.isFinite() && pressedStateLayerOpacity in 0f..1f) {
            "pressedStateLayerOpacity must be finite and in 0f..1f."
        }
        require(focusedStateLayerOpacity.isFinite() && focusedStateLayerOpacity in 0f..1f) {
            "focusedStateLayerOpacity must be finite and in 0f..1f."
        }
        require(hoveredStateLayerOpacity.isFinite() && hoveredStateLayerOpacity in 0f..1f) {
            "hoveredStateLayerOpacity must be finite and in 0f..1f."
        }
    }
}

/** Derives framework state-color roles from a semantic [UiColors] scheme. */
object UiStateColorDefaults {
    /**
     * Creates state colors from [colors] without retaining the source object.
     *
     * @return a new immutable state-color snapshot
     */
    fun from(colors: UiColors): UiStateColors {
        return UiStateColors(
            primaryText = UiStateColor(
                defaultColor = colors.onSurface,
                disabledColor = colorWithAlpha(colors.onSurface, 0.38f),
            ),
            secondaryText = UiStateColor(
                defaultColor = colors.onSurfaceVariant,
                disabledColor = colorWithAlpha(colors.onSurface, 0.38f),
            ),
            control = UiStateColor(
                defaultColor = colors.onSurfaceVariant,
                disabledColor = colorWithAlpha(colors.onSurface, 0.38f),
                pressedColor = colors.primary,
                focusedColor = colors.primary,
                checkedColor = colors.primary,
                selectedColor = colors.primary,
            ),
            controlActivated = UiStateColor(
                defaultColor = colors.primary,
                disabledColor = colorWithAlpha(colors.onSurface, 0.38f),
                pressedColor = colors.primary,
                focusedColor = colors.primary,
                checkedColor = colors.primary,
                selectedColor = colors.primary,
            ),
        )
    }
}

/**
 * Defines the complete semantic corner scale available to component defaults.
 *
 * The absolute tiers do not prescribe a design system. A design-system adapter supplies the
 * concrete geometry, while [full] expresses a pill or circle independently of final bounds.
 * Existing three-tier themes remain source-compatible because the additional tiers derive from
 * [small] and [large] when omitted.
 *
 * @property small shape for compact controls and small surfaces
 * @property medium shape for standard controls and surfaces
 * @property large shape for prominent surfaces
 * @property extraSmall shape for controls with minimal corner treatment
 * @property extraLarge shape for prominent modal surfaces
 * @property full bounds-relative shape for pill and circular containers
 */
data class UiShapes(
    val small: UiShape,
    val medium: UiShape,
    val large: UiShape = medium,
    val extraSmall: UiShape = small,
    val extraLarge: UiShape = large,
    val full: UiShape = UiShape.roundedRelative(0.5f),
)

/**
 * Defines an Android-rendered text style independent from a particular widget.
 *
 * Null optional values preserve the renderer or component default. The [fontFamily] is an Android
 * `Typeface` because this module is the Android widget contract boundary.
 *
 * @property fontSizeSp text size in scale-independent pixels
 * @property fontWeight optional platform font weight
 * @property fontFamily optional Android typeface
 * @property letterSpacingEm optional letter spacing in em units
 * @property lineHeightSp optional line height in scale-independent pixels
 * @property includeFontPadding whether Android font top and bottom padding is included
 * @property textDecoration optional underline or line-through decoration
 */
data class UiTextStyle(
    val fontSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: android.graphics.Typeface? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val textDecoration: com.viewcompose.ui.node.TextDecoration? = null,
)

/**
 * Groups display, headline, title, body, and label typography at three semantic tiers.
 *
 * The original title, body, and label medium styles remain the minimum constructor surface.
 * Omitted tiers derive from their nearest existing family so non-Material custom themes keep a
 * complete usable scale. Design-system adapters should supply all fifteen roles when their type
 * scale distinguishes them.
 *
 * @property titleMedium standard title style
 * @property bodyMedium standard body style
 * @property labelMedium standard label style
 * @property titleLarge large title style
 * @property titleSmall small title style
 * @property bodyLarge large body style
 * @property bodySmall small body style
 * @property labelLarge large label style
 * @property labelSmall small label style
 * @property headlineLarge large section-heading style
 * @property headlineMedium medium section-heading style
 * @property headlineSmall small section-heading style
 * @property displayLarge largest display style
 * @property displayMedium medium display style
 * @property displaySmall smallest display style
 */
data class UiTypography(
    val titleMedium: UiTextStyle,
    val bodyMedium: UiTextStyle,
    val labelMedium: UiTextStyle,
    val titleLarge: UiTextStyle = titleMedium,
    val titleSmall: UiTextStyle = titleMedium,
    val bodyLarge: UiTextStyle = bodyMedium,
    val bodySmall: UiTextStyle = bodyMedium,
    val labelLarge: UiTextStyle = labelMedium,
    val labelSmall: UiTextStyle = labelMedium,
    val headlineLarge: UiTextStyle = titleLarge,
    val headlineMedium: UiTextStyle = titleMedium,
    val headlineSmall: UiTextStyle = titleSmall,
    val displayLarge: UiTextStyle = headlineLarge,
    val displayMedium: UiTextStyle = headlineMedium,
    val displaySmall: UiTextStyle = headlineSmall,
)

/**
 * Immutable theme snapshot consumed by component defaults and renderers.
 *
 * @property colors semantic color scheme
 * @property typography component-independent text styles
 * @property stateColors state-aware colors; derived from [colors] by default
 * @property shapes component shape tiers
 * @property controls core component sizing tokens
 * @property interactions transient interaction-state opacity tokens
 * @property overlays modal overlay tokens
 * @property metadata origin, brightness, and revision diagnostics
 */
data class UiThemeTokens(
    val colors: UiColors,
    val typography: UiTypography,
    val stateColors: UiStateColors = UiStateColorDefaults.from(colors),
    val shapes: UiShapes = UiShapeDefaults.default(),
    val controls: UiControlSizing = UiControlSizeDefaults.default(),
    val interactions: UiInteractionTokens = defaultInteractionTokens(),
    val overlays: UiOverlays = UiOverlayDefaults.default(),
    val metadata: UiThemeMetadata = UiThemeMetadata(),
)

/**
 * Defines visual tokens shared by modal overlays.
 *
 * @property scrimOpacity opacity applied to the theme scrim color, conventionally in `0f..1f`
 */
data class UiOverlays(
    val scrimOpacity: Float,
)

/** Identifies the source that produced a theme snapshot for diagnostics and host bridging. */
enum class UiThemeOrigin {
    Custom,
    FrameworkDefault,
    AndroidTheme,
    AndroidDynamicColor,
    Override,
}

/**
 * Describes where effective token values originated without changing visual resolution.
 *
 * [defaultOrigin] applies when [tokenOrigins] has no exact token path or enclosing family entry.
 * Paths use stable dotted names such as `colors.primary` or `shapes`; consumers may ask for a
 * concrete value and inherit a family-level source. [sourceId] identifies the named producer of
 * the base snapshot, for example `viewcompose-material3/android-xml`.
 *
 * @property sourceId stable non-blank producer identity for diagnostics
 * @property defaultOrigin fallback source for token paths without a more specific entry
 * @property tokenOrigins exact token- or family-path source overrides
 * @throws IllegalArgumentException when [sourceId] or a token path is blank
 */
data class UiTokenProvenance(
    val sourceId: String,
    val defaultOrigin: UiThemeOrigin,
    val tokenOrigins: Map<String, UiThemeOrigin> = emptyMap(),
) {
    init {
        require(sourceId.isNotBlank()) { "UiTokenProvenance sourceId must not be blank." }
        require(tokenOrigins.keys.none(String::isBlank)) {
            "UiTokenProvenance token paths must not be blank."
        }
    }

    /**
     * Resolves the most specific recorded source for [tokenPath].
     *
     * Resolution checks the complete path and then removes trailing dotted segments until a
     * recorded family is found. It returns [defaultOrigin] when no entry matches.
     *
     * @param tokenPath non-blank stable token path
     * @return exact, family-inherited, or default origin
     * @throws IllegalArgumentException when [tokenPath] is blank
     */
    fun originOf(tokenPath: String): UiThemeOrigin {
        require(tokenPath.isNotBlank()) { "UiTokenProvenance tokenPath must not be blank." }
        var candidate = tokenPath
        while (true) {
            tokenOrigins[candidate]?.let { return it }
            val separator = candidate.lastIndexOf('.')
            if (separator < 0) return defaultOrigin
            candidate = candidate.substring(0, separator)
        }
    }

    /**
     * Returns a snapshot that attributes every supplied token or family path to [origin].
     *
     * @param tokenPaths non-blank stable token or family paths
     * @param origin source category applied to every path
     * @return this instance when [tokenPaths] is empty, otherwise a copied provenance snapshot
     * @throws IllegalArgumentException when any path is blank
     */
    fun withOrigins(
        tokenPaths: Set<String>,
        origin: UiThemeOrigin,
    ): UiTokenProvenance {
        require(tokenPaths.none(String::isBlank)) {
            "UiTokenProvenance token paths must not be blank."
        }
        if (tokenPaths.isEmpty()) return this
        return copy(tokenOrigins = tokenOrigins + tokenPaths.associateWith { origin })
    }
}

/**
 * Carries non-visual theme diagnostics without affecting token resolution.
 *
 * @property origin source that produced the current snapshot
 * @property isDark whether the producer classifies the scheme as dark, or `null` when unspecified
 * @property revision producer-owned change counter used to expose theme refreshes
 * @property provenance per-token source resolution and stable base-producer identity
 */
data class UiThemeMetadata(
    val origin: UiThemeOrigin = UiThemeOrigin.Custom,
    val isDark: Boolean? = null,
    val revision: Long = 0L,
    val provenance: UiTokenProvenance = UiTokenProvenance(
        sourceId = origin.name,
        defaultOrigin = origin,
    ),
)

/** Returns [color] with its alpha channel replaced by the clamped [alpha] fraction. */
internal fun colorWithAlpha(color: Int, alpha: Float): Int {
    val alphaChannel = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (alphaChannel shl 24) or (color and 0x00FFFFFF)
}

/** Returns [color] with an alpha channel rounded from the clamped state-layer [opacity]. */
internal fun stateLayerColorWithOpacity(color: Int, opacity: Float): Int {
    val alphaChannel = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
    return (alphaChannel shl 24) or (color and 0x00FFFFFF)
}

/** Resolves the current theme's transient interaction opacities against one semantic content role. */
internal fun stateLayerColorsFor(contentColor: Int): UiStateLayerColors {
    return UiStateLayerColors(
        pressedColor = stateLayerColorWithOpacity(
            contentColor,
            Theme.interactions.pressedStateLayerOpacity,
        ),
        focusedColor = stateLayerColorWithOpacity(
            contentColor,
            Theme.interactions.focusedStateLayerOpacity,
        ),
        hoveredColor = stateLayerColorWithOpacity(
            contentColor,
            Theme.interactions.hoveredStateLayerOpacity,
        ),
    )
}

private fun defaultInteractionTokens(): UiInteractionTokens {
    return UiInteractionTokens(
        pressedStateLayerOpacity = 0.10f,
        focusedStateLayerOpacity = 0.10f,
        hoveredStateLayerOpacity = 0.08f,
    )
}

/**
 * Derives black or white content color from background luminance.
 */
internal fun contentColorFor(backgroundColor: Int): Int {
    val red = backgroundColor shr 16 and 0xFF
    val green = backgroundColor shr 8 and 0xFF
    val blue = backgroundColor and 0xFF
    val luma = 0.299 * red + 0.587 * green + 0.114 * blue
    return if (luma >= 186) {
        0xFF000000.toInt()
    } else {
        0xFFFFFFFF.toInt()
    }
}
