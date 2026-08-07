package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core theme 中的 Theme Token Compatibility 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Theme Token Compatibility behavior in widget-core theme and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.shape.UiShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemeTokenCompatibilityTest {
    @Test
    fun `semantic tokens drive defaults and tiered typography`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 1,
                surface = 2,
                surfaceVariant = 3,
                onSurface = 11,
                onSurfaceVariant = 12,
                primary = 4,
                secondary = 44,
                error = 66,
                success = 7,
                warning = 8,
                info = 9,
                outline = 10,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 26.sp),
                bodyLarge = UiTextStyle(fontSizeSp = 20.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 17.sp),
                labelMedium = UiTextStyle(fontSizeSp = 14.sp),
                labelSmall = UiTextStyle(fontSizeSp = 12.sp),
            ),
            shapes = UiShapes(
                small = UiShape.rounded(22.dp),
                medium = UiShape.rounded(20.dp),
            ),
        )
        var secondaryContainer = 0
        var errorColor = 0
        var compactTextSize = 0.sp
        var largeTextSize = 0.sp
        var listHeadlineSize = 0.sp
        var topTitleSize = 0.sp
        var smallShape = UiShape.rounded(0.dp)

        buildVNodeTree {
            UiTheme(customTheme) {
                secondaryContainer = ButtonDefaults.containerColor(ButtonVariant.Secondary)
                errorColor = TextFieldDefaults.hintColor(isError = true)
                compactTextSize = TextFieldDefaults.textStyle(TextFieldSize.Compact).fontSizeSp
                largeTextSize = TextFieldDefaults.textStyle(TextFieldSize.Large).fontSizeSp
                listHeadlineSize = ListItemDefaults.headlineStyle().fontSizeSp
                topTitleSize = TopAppBarDefaults.titleStyle().fontSizeSp
                smallShape = ButtonDefaults.shape()
            }
        }

        assertEquals(customTheme.colors.secondary, secondaryContainer)
        assertEquals(customTheme.colors.onSurfaceVariant, errorColor)
        assertEquals(customTheme.typography.labelSmall.fontSizeSp, compactTextSize)
        assertEquals(customTheme.typography.bodyLarge.fontSizeSp, largeTextSize)
        assertEquals(customTheme.typography.bodyLarge.fontSizeSp, listHeadlineSize)
        assertEquals(customTheme.typography.titleMedium.fontSizeSp, topTitleSize)
        assertEquals(customTheme.shapes.full, smallShape)
    }

    @Test
    fun `tiered typography defaults map to base tokens when tiers are omitted`() {
        val typography = UiTypography(
            titleMedium = UiTextStyle(fontSizeSp = 30.sp),
            bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
            labelMedium = UiTextStyle(fontSizeSp = 14.sp),
        )

        assertEquals(typography.titleMedium, typography.titleLarge)
        assertEquals(typography.titleMedium, typography.titleSmall)
        assertEquals(typography.bodyMedium, typography.bodyLarge)
        assertEquals(typography.bodyMedium, typography.bodySmall)
        assertEquals(typography.labelMedium, typography.labelLarge)
        assertEquals(typography.labelMedium, typography.labelSmall)
        assertEquals(typography.titleLarge, typography.headlineLarge)
        assertEquals(typography.titleMedium, typography.headlineMedium)
        assertEquals(typography.titleSmall, typography.headlineSmall)
        assertEquals(typography.headlineLarge, typography.displayLarge)
        assertEquals(typography.headlineMedium, typography.displayMedium)
        assertEquals(typography.headlineSmall, typography.displaySmall)
    }

    @Test
    fun `interaction tokens reject invalid opacity`() {
        assertThrows(IllegalArgumentException::class.java) {
            UiInteractionTokens(pressedStateLayerOpacity = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UiInteractionTokens(pressedStateLayerOpacity = 1.01f)
        }
    }
}
