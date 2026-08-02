package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiSp

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
 * @property ripple default pressed-state ripple color
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
    val ripple: Int = pressedOverlayColorFor(onSurface),
)

/**
 * Stores one semantic color across enabled and interactive states.
 *
 * [resolve] uses disabled, pressed, focused, checked, selected, then default precedence. This means
 * callers should pass the complete simultaneous state instead of preselecting one branch.
 *
 * @sample com.viewcompose.widget.core.samples.themeStateColorSample
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
 * @property controlHighlight transient pressed/focused highlight colors
 */
data class UiStateColors(
    val primaryText: UiStateColor,
    val secondaryText: UiStateColor,
    val control: UiStateColor,
    val controlActivated: UiStateColor,
    val controlHighlight: UiStateColor,
)

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
                disabledColor = colors.onSurfaceVariant,
            ),
            secondaryText = UiStateColor(
                defaultColor = colors.onSurfaceVariant,
                disabledColor = colors.outline,
            ),
            control = UiStateColor(
                defaultColor = colors.outline,
                disabledColor = colors.outlineVariant,
                pressedColor = colors.primary,
                focusedColor = colors.primary,
                checkedColor = colors.primary,
                selectedColor = colors.primary,
            ),
            controlActivated = UiStateColor(
                defaultColor = colors.primary,
                disabledColor = colors.outlineVariant,
                pressedColor = colors.primary,
                focusedColor = colors.primary,
                checkedColor = colors.primary,
                selectedColor = colors.primary,
            ),
            controlHighlight = UiStateColor(
                defaultColor = colors.ripple,
                disabledColor = 0x00000000,
                pressedColor = colors.ripple,
                focusedColor = colors.ripple,
                checkedColor = colors.ripple,
                selectedColor = colors.ripple,
            ),
        )
    }
}

/**
 * Defines small, medium, and large component shape tiers.
 *
 * @property small shape for compact controls and small surfaces
 * @property medium shape for standard controls and surfaces
 * @property large shape for prominent or large surfaces
 */
data class UiShapes(
    val small: UiShape,
    val medium: UiShape,
    val large: UiShape = medium,
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
 * Groups title, body, and label typography at large, medium, and small tiers.
 *
 * The medium styles are required and serve as defaults for omitted size tiers.
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
)

/**
 * Immutable theme snapshot consumed by component defaults and renderers.
 *
 * @property colors semantic color scheme
 * @property typography component-independent text styles
 * @property stateColors state-aware colors; derived from [colors] by default
 * @property shapes component shape tiers
 * @property controls core component sizing tokens
 * @property overlays modal overlay tokens
 * @property metadata origin, brightness, and revision diagnostics
 */
data class UiThemeTokens(
    val colors: UiColors,
    val typography: UiTypography,
    val stateColors: UiStateColors = UiStateColorDefaults.from(colors),
    val shapes: UiShapes = UiShapeDefaults.default(),
    val controls: UiControlSizing = UiControlSizeDefaults.default(),
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
 * Carries non-visual theme diagnostics without affecting token resolution.
 *
 * @property origin source that produced the current snapshot
 * @property isDark whether the producer classifies the scheme as dark, or `null` when unspecified
 * @property revision producer-owned change counter used to expose theme refreshes
 */
data class UiThemeMetadata(
    val origin: UiThemeOrigin = UiThemeOrigin.Custom,
    val isDark: Boolean? = null,
    val revision: Long = 0L,
)

/**
 * Builds a pressed-state overlay color from the content color.
 */
internal fun pressedOverlayColorFor(contentColor: Int): Int {
    val base = contentColor and 0x00FFFFFF
    return 0x22000000 or base
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
