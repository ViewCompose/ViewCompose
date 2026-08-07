package com.viewcompose.material3

import com.viewcompose.ui.foundation.UiColors
import com.viewcompose.ui.foundation.UiControlSizeDefaults
import com.viewcompose.ui.foundation.UiControlSizing
import com.viewcompose.ui.foundation.UiInteractionTokens
import com.viewcompose.ui.foundation.UiShapes
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeMetadata
import com.viewcompose.ui.foundation.UiThemeOrigin
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTypography
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/**
 * Creates static Material 3 token snapshots without reading Android theme resources.
 *
 * These snapshots use the non-Expressive Material 3 baseline bundled with Material Components
 * 1.13.0. Android applications should normally use [Material3ThemeBridge], which replaces the
 * static colors, typography, and shapes with values from the active Android theme while retaining
 * these values as deterministic fallbacks.
 */
object Material3ThemeDefaults {
    /**
     * Creates the standard light Material 3 token snapshot.
     *
     * @return a new immutable light snapshot with a complete color, type, shape, and sizing scale
     */
    fun light(): UiThemeTokens = createTokens(
        colors = lightColors(),
        isDark = false,
    )

    /**
     * Creates the standard dark Material 3 token snapshot.
     *
     * @return a new immutable dark snapshot with a complete color, type, shape, and sizing scale
     */
    fun dark(): UiThemeTokens = createTokens(
        colors = darkColors(),
        isDark = true,
    )

    private fun createTokens(
        colors: UiColors,
        isDark: Boolean,
    ): UiThemeTokens {
        return UiThemeTokens(
            colors = colors,
            typography = typography(),
            shapes = shapes(),
            controls = controls(),
            interactions = interactions(),
            metadata = UiThemeMetadata(
                origin = UiThemeOrigin.Custom,
                isDark = isDark,
            ),
        )
    }

    private fun interactions(): UiInteractionTokens {
        return UiInteractionTokens(
            pressedStateLayerOpacity = 0.10f,
            focusedStateLayerOpacity = 0.10f,
            hoveredStateLayerOpacity = 0.08f,
        )
    }

    private fun shapes(): UiShapes {
        return UiShapes(
            extraSmall = UiShape.rounded(4.dp),
            small = UiShape.rounded(8.dp),
            medium = UiShape.rounded(12.dp),
            large = UiShape.rounded(16.dp),
            extraLarge = UiShape.rounded(28.dp),
            full = UiShape.roundedRelative(0.5f),
        )
    }

    private fun typography(): UiTypography {
        return UiTypography(
            displayLarge = textStyle(size = 57, lineHeight = 64, weight = 400, letterSpacingEm = -0.004386f),
            displayMedium = textStyle(size = 45, lineHeight = 52, weight = 400),
            displaySmall = textStyle(size = 36, lineHeight = 44, weight = 400),
            headlineLarge = textStyle(size = 32, lineHeight = 40, weight = 400),
            headlineMedium = textStyle(size = 28, lineHeight = 36, weight = 400),
            headlineSmall = textStyle(size = 24, lineHeight = 32, weight = 400),
            titleLarge = textStyle(size = 22, lineHeight = 28, weight = 400),
            titleMedium = textStyle(size = 16, lineHeight = 24, weight = 500, letterSpacingEm = 0.009375f),
            titleSmall = textStyle(size = 14, lineHeight = 20, weight = 500, letterSpacingEm = 0.007143f),
            bodyLarge = textStyle(size = 16, lineHeight = 24, weight = 400, letterSpacingEm = 0.03125f),
            bodyMedium = textStyle(size = 14, lineHeight = 20, weight = 400, letterSpacingEm = 0.017857f),
            bodySmall = textStyle(size = 12, lineHeight = 16, weight = 400, letterSpacingEm = 0.033333f),
            labelLarge = textStyle(size = 14, lineHeight = 20, weight = 500, letterSpacingEm = 0.007143f),
            labelMedium = textStyle(size = 12, lineHeight = 16, weight = 500, letterSpacingEm = 0.041667f),
            labelSmall = textStyle(size = 11, lineHeight = 16, weight = 500, letterSpacingEm = 0.045455f),
        )
    }

    private fun textStyle(
        size: Int,
        lineHeight: Int,
        weight: Int,
        letterSpacingEm: Float = 0f,
    ): UiTextStyle {
        return UiTextStyle(
            fontSizeSp = size.sp,
            fontWeight = weight,
            letterSpacingEm = letterSpacingEm,
            lineHeightSp = lineHeight.sp,
            includeFontPadding = false,
        )
    }

    private fun controls(): UiControlSizing {
        val base = UiControlSizeDefaults.default()
        return base.copy(
            minimumInteractiveHeight = 48.dp,
            button = base.button.copy(
                compactHeight = 48.dp,
                mediumHeight = 48.dp,
                largeHeight = 56.dp,
                compactHorizontalPadding = 12.dp,
                mediumHorizontalPadding = 24.dp,
                largeHorizontalPadding = 32.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 8.dp,
                largeVerticalPadding = 8.dp,
                compactVisualHeight = 40.dp,
                mediumVisualHeight = 40.dp,
                largeVisualHeight = 48.dp,
            ),
            textField = base.textField.copy(
                compactHeight = 48.dp,
                mediumHeight = 56.dp,
                largeHeight = 64.dp,
                compactHorizontalPadding = 16.dp,
                mediumHorizontalPadding = 16.dp,
                largeHorizontalPadding = 16.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 8.dp,
                largeVerticalPadding = 8.dp,
            ),
            segmentedControl = base.segmentedControl.copy(
                compactHeight = 48.dp,
                mediumHeight = 48.dp,
                largeHeight = 56.dp,
                compactHorizontalPadding = 12.dp,
                mediumHorizontalPadding = 16.dp,
                largeHorizontalPadding = 20.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 8.dp,
                largeVerticalPadding = 8.dp,
            ),
            progressIndicator = base.progressIndicator.copy(
                linearTrackThickness = 4.dp,
                circularSize = 40.dp,
                circularTrackThickness = 4.dp,
            ),
            fab = base.fab.copy(
                smallIconSize = 24.dp,
            ),
            searchBar = base.searchBar.copy(
                elevation = 6.dp,
            ),
            badge = base.badge.copy(
                dotSize = 6.dp,
            ),
        )
    }

    private fun lightColors(): UiColors {
        return UiColors(
            background = 0xFFFEF7FF.toInt(),
            onBackground = 0xFF1D1B20.toInt(),
            surface = 0xFFFEF7FF.toInt(),
            surfaceVariant = 0xFFE7E0EC.toInt(),
            surfaceDim = 0xFFDED8E1.toInt(),
            surfaceBright = 0xFFFEF7FF.toInt(),
            surfaceContainerLowest = 0xFFFFFFFF.toInt(),
            surfaceContainerLow = 0xFFF7F2FA.toInt(),
            surfaceContainer = 0xFFF3EDF7.toInt(),
            surfaceContainerHigh = 0xFFECE6F0.toInt(),
            surfaceContainerHighest = 0xFFE6E0E9.toInt(),
            onSurface = 0xFF1D1B20.toInt(),
            onSurfaceVariant = 0xFF49454F.toInt(),
            primary = 0xFF6750A4.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFEADDFF.toInt(),
            onPrimaryContainer = 0xFF21005D.toInt(),
            secondary = 0xFF625B71.toInt(),
            onSecondary = 0xFFFFFFFF.toInt(),
            secondaryContainer = 0xFFE8DEF8.toInt(),
            onSecondaryContainer = 0xFF1D192B.toInt(),
            tertiary = 0xFF7D5260.toInt(),
            onTertiary = 0xFFFFFFFF.toInt(),
            tertiaryContainer = 0xFFFFD8E4.toInt(),
            onTertiaryContainer = 0xFF31111D.toInt(),
            error = 0xFFB3261E.toInt(),
            onError = 0xFFFFFFFF.toInt(),
            errorContainer = 0xFFF9DEDC.toInt(),
            onErrorContainer = 0xFF410E0B.toInt(),
            success = 0xFF006C35.toInt(),
            warning = 0xFF8F4E06.toInt(),
            info = 0xFF1157CE.toInt(),
            outline = 0xFF79747E.toInt(),
            outlineVariant = 0xFFCAC4D0.toInt(),
            surfaceTint = 0xFF6750A4.toInt(),
            inverseSurface = 0xFF322F35.toInt(),
            inverseOnSurface = 0xFFF5EFF7.toInt(),
            inversePrimary = 0xFFD0BCFF.toInt(),
        )
    }

    private fun darkColors(): UiColors {
        return UiColors(
            background = 0xFF141218.toInt(),
            onBackground = 0xFFE6E0E9.toInt(),
            surface = 0xFF141218.toInt(),
            surfaceVariant = 0xFF49454F.toInt(),
            surfaceDim = 0xFF141218.toInt(),
            surfaceBright = 0xFF3B383E.toInt(),
            surfaceContainerLowest = 0xFF0F0D13.toInt(),
            surfaceContainerLow = 0xFF1D1B20.toInt(),
            surfaceContainer = 0xFF211F26.toInt(),
            surfaceContainerHigh = 0xFF2B2930.toInt(),
            surfaceContainerHighest = 0xFF36343B.toInt(),
            onSurface = 0xFFE6E0E9.toInt(),
            onSurfaceVariant = 0xFFCAC4D0.toInt(),
            primary = 0xFFD0BCFF.toInt(),
            onPrimary = 0xFF381E72.toInt(),
            primaryContainer = 0xFF4F378B.toInt(),
            onPrimaryContainer = 0xFFEADDFF.toInt(),
            secondary = 0xFFCCC2DC.toInt(),
            onSecondary = 0xFF332D41.toInt(),
            secondaryContainer = 0xFF4A4458.toInt(),
            onSecondaryContainer = 0xFFE8DEF8.toInt(),
            tertiary = 0xFFEFB8C8.toInt(),
            onTertiary = 0xFF492532.toInt(),
            tertiaryContainer = 0xFF633B48.toInt(),
            onTertiaryContainer = 0xFFFFD8E4.toInt(),
            error = 0xFFF2B8B5.toInt(),
            onError = 0xFF601410.toInt(),
            errorContainer = 0xFF8C1D18.toInt(),
            onErrorContainer = 0xFFF9DEDC.toInt(),
            success = 0xFF80DA88.toInt(),
            warning = 0xFFFCBD00.toInt(),
            info = 0xFFA1C9FF.toInt(),
            outline = 0xFF938F99.toInt(),
            outlineVariant = 0xFF49454F.toInt(),
            surfaceTint = 0xFFD0BCFF.toInt(),
            inverseSurface = 0xFFE6E0E9.toInt(),
            inverseOnSurface = 0xFF322F35.toInt(),
            inversePrimary = 0xFF6750A4.toInt(),
        )
    }
}
