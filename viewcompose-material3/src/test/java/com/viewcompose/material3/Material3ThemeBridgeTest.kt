package com.viewcompose.material3

/*
 * 测试职责：覆盖 widget-core theme 中的 Android Theme Bridge 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Android Theme Bridge behavior in widget-core theme and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class Material3ThemeBridgeTest {
    @Test
    fun `theme mapper uses provided attribute colors`() {
        val attrs = mapOf(
            android.R.attr.colorBackground to 1,
            com.google.android.material.R.attr.colorSurface to 2,
            com.google.android.material.R.attr.colorSurfaceVariant to 3,
            android.R.attr.textColorPrimary to 7,
            android.R.attr.textColorSecondary to 8,
            com.google.android.material.R.attr.colorOnSurface to 70,
            com.google.android.material.R.attr.colorOnSurfaceVariant to 80,
            androidx.appcompat.R.attr.colorPrimary to 4,
            com.google.android.material.R.attr.colorOnPrimary to 40,
            com.google.android.material.R.attr.colorPrimaryContainer to 41,
            com.google.android.material.R.attr.colorOnPrimaryContainer to 42,
            com.google.android.material.R.attr.colorSecondary to 5,
            com.google.android.material.R.attr.colorOnSecondary to 50,
            com.google.android.material.R.attr.colorSecondaryContainer to 51,
            com.google.android.material.R.attr.colorOnSecondaryContainer to 52,
            android.R.attr.colorError to 9,
            com.google.android.material.R.attr.colorOnError to 90,
            com.google.android.material.R.attr.colorErrorContainer to 91,
            com.google.android.material.R.attr.colorOnErrorContainer to 92,
            com.google.android.material.R.attr.colorOutline to 6,
            com.google.android.material.R.attr.colorOutlineVariant to 61,
            com.google.android.material.R.attr.colorSurfaceInverse to 63,
            com.google.android.material.R.attr.colorOnSurfaceInverse to 64,
        )

        val tokens = Material3ThemeTokenMapper.fromThemeColors(attrs::get)

        assertEquals(1, tokens.colors.background)
        assertEquals(2, tokens.colors.surface)
        assertEquals(3, tokens.colors.surfaceVariant)
        assertEquals(70, tokens.colors.onSurface)
        assertEquals(80, tokens.colors.onSurfaceVariant)
        assertEquals(4, tokens.colors.primary)
        assertEquals(40, tokens.colors.onPrimary)
        assertEquals(41, tokens.colors.primaryContainer)
        assertEquals(42, tokens.colors.onPrimaryContainer)
        assertEquals(5, tokens.colors.secondary)
        assertEquals(50, tokens.colors.onSecondary)
        assertEquals(51, tokens.colors.secondaryContainer)
        assertEquals(52, tokens.colors.onSecondaryContainer)
        assertEquals(9, tokens.colors.error)
        assertEquals(90, tokens.colors.onError)
        assertEquals(91, tokens.colors.errorContainer)
        assertEquals(92, tokens.colors.onErrorContainer)
        assertEquals(6, tokens.colors.outline)
        assertEquals(61, tokens.colors.outlineVariant)
        assertEquals(4, tokens.colors.surfaceTint)
        assertEquals(63, tokens.colors.inverseSurface)
        assertEquals(64, tokens.colors.inverseOnSurface)
    }

    @Test
    fun `theme mapper falls back to light defaults`() {
        val tokens = Material3ThemeTokenMapper.fromThemeColors(
            readColor = { attr ->
                when (attr) {
                    androidx.appcompat.R.attr.colorPrimary -> 99
                    else -> null
                }
            },
        )

        assertEquals(99, tokens.colors.primary)
        assertEquals(Material3ThemeDefaults.light().colors.surface, tokens.colors.surface)
        assertEquals(Material3ThemeDefaults.light().colors.onSurface, tokens.colors.onSurface)
        assertEquals(Material3ThemeDefaults.light().colors.onSurfaceVariant, tokens.colors.onSurfaceVariant)
        assertEquals(Material3ThemeDefaults.light().typography.bodyMedium.fontSizeSp, tokens.typography.bodyMedium.fontSizeSp)
    }

    @Test
    fun `theme mapper uses dark defaults when isDarkMode is true`() {
        val tokens = Material3ThemeTokenMapper.fromThemeColors(
            readColor = { null },
            isDarkMode = true,
        )

        assertEquals(Material3ThemeDefaults.dark().colors.background, tokens.colors.background)
        assertEquals(Material3ThemeDefaults.dark().colors.surface, tokens.colors.surface)
        assertEquals(Material3ThemeDefaults.dark().colors.primary, tokens.colors.primary)
        assertEquals(Material3ThemeDefaults.dark().colors.onSurface, tokens.colors.onSurface)
        assertEquals(Material3ThemeDefaults.dark().colors.onSurfaceVariant, tokens.colors.onSurfaceVariant)
        assertEquals(Material3ThemeDefaults.dark().typography.titleMedium.fontSizeSp, tokens.typography.titleMedium.fontSizeSp)
    }

    @Test
    fun `theme mapper bridges typography from text appearance attrs`() {
        val textSizes = mapOf(
            android.R.attr.textAppearanceLarge to 28.sp,
            android.R.attr.textAppearanceMedium to 18.sp,
            android.R.attr.textAppearanceSmall to 12.sp,
        )

        val tokens = Material3ThemeTokenMapper.fromThemeColors(
            readColor = { null },
            readTextSizeSp = textSizes::get,
        )

        assertEquals(28.sp, tokens.typography.titleMedium.fontSizeSp)
        assertEquals(18.sp, tokens.typography.bodyMedium.fontSizeSp)
        assertEquals(12.sp, tokens.typography.labelMedium.fontSizeSp)
    }

    @Test
    fun `typography falls back to defaults when text appearances unavailable`() {
        val tokens = Material3ThemeTokenMapper.fromThemeColors(
            readColor = { null },
            readTextSizeSp = { null },
        )

        val fallback = Material3ThemeDefaults.light()
        assertEquals(fallback.typography.titleMedium.fontSizeSp, tokens.typography.titleMedium.fontSizeSp)
        assertEquals(fallback.typography.bodyMedium.fontSizeSp, tokens.typography.bodyMedium.fontSizeSp)
        assertEquals(fallback.typography.labelMedium.fontSizeSp, tokens.typography.labelMedium.fontSizeSp)
    }

    @Test
    fun `partial typography override keeps defaults for missing attrs`() {
        val tokens = Material3ThemeTokenMapper.fromThemeColors(
            readColor = { null },
            readTextSizeSp = { attr ->
                when (attr) {
                    android.R.attr.textAppearanceMedium -> 20.sp
                    else -> null
                }
            },
        )

        val fallback = Material3ThemeDefaults.light()
        assertEquals(fallback.typography.titleMedium.fontSizeSp, tokens.typography.titleMedium.fontSizeSp)
        assertEquals(20.sp, tokens.typography.bodyMedium.fontSizeSp)
        assertEquals(fallback.typography.labelMedium.fontSizeSp, tokens.typography.labelMedium.fontSizeSp)
    }

    @Test
    fun `snapshot mapper bridges semantic shapes ripple and scrim`() {
        val tokens = Material3ThemeTokenMapper.fromSnapshot(
            snapshot = Material3ThemeSnapshot(
                colors = Material3ThemeColorSnapshot(
                    ripple = 77,
                ),
                shapes = Material3ThemeShapeSnapshot(
                    extraSmall = UiShape.cut(4.dp),
                    small = UiShape.rounded(12.dp),
                    medium = UiShape.cut(20.dp),
                    large = UiShape.roundedRelative(0.5f),
                    extraLarge = UiShape.rounded(28.dp),
                ),
                scrimOpacity = 0.58f,
            ),
        )

        assertEquals(77, tokens.colors.ripple)
        assertEquals(0.58f, tokens.overlays.scrimOpacity, 0.0001f)
        assertEquals(UiShape.cut(4.dp), tokens.shapes.extraSmall)
        assertEquals(UiShape.rounded(12.dp), tokens.shapes.small)
        assertEquals(UiShape.cut(20.dp), tokens.shapes.medium)
        assertEquals(UiShape.roundedRelative(0.5f), tokens.shapes.large)
        assertEquals(UiShape.rounded(28.dp), tokens.shapes.extraLarge)
    }

    @Test
    fun `snapshot mapper bridges tiered typography and richer text style fields`() {
        val tokens = Material3ThemeTokenMapper.fromSnapshot(
            snapshot = Material3ThemeSnapshot(
                typography = Material3ThemeTypographySnapshot(
                    displayLarge = Material3TextStyleSnapshot(
                        fontSizeSp = 61.sp,
                    ),
                    headlineSmall = Material3TextStyleSnapshot(
                        fontSizeSp = 25.sp,
                    ),
                    titleLarge = Material3TextStyleSnapshot(
                        fontSizeSp = 30.sp,
                        fontWeight = 700,
                        letterSpacingEm = 0.04f,
                        lineHeightSp = 36.sp,
                        includeFontPadding = true,
                    ),
                    bodyLarge = Material3TextStyleSnapshot(
                        fontSizeSp = 19.sp,
                        fontWeight = 500,
                    ),
                    labelSmall = Material3TextStyleSnapshot(
                        fontSizeSp = 11.sp,
                    ),
                ),
            ),
        )

        assertEquals(61.sp, tokens.typography.displayLarge.fontSizeSp)
        assertEquals(25.sp, tokens.typography.headlineSmall.fontSizeSp)
        assertEquals(30.sp, tokens.typography.titleLarge.fontSizeSp)
        assertEquals(700, tokens.typography.titleLarge.fontWeight)
        assertEquals(0.04f, tokens.typography.titleLarge.letterSpacingEm)
        assertEquals(36.sp, tokens.typography.titleLarge.lineHeightSp)
        assertEquals(true, tokens.typography.titleLarge.includeFontPadding)
        assertEquals(30.sp, tokens.typography.titleMedium.fontSizeSp)
        assertEquals(19.sp, tokens.typography.bodyLarge.fontSizeSp)
        assertEquals(500, tokens.typography.bodyLarge.fontWeight)
        assertEquals(19.sp, tokens.typography.bodyMedium.fontSizeSp)
        assertEquals(11.sp, tokens.typography.labelSmall.fontSizeSp)
        assertEquals(11.sp, tokens.typography.labelMedium.fontSizeSp)
    }
}
