package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core theme 中的 Theme Token Compatibility 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Theme Token Compatibility behavior in widget-core theme and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.shape.UiShape
import org.junit.Assert.assertEquals
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
                titleMedium = UiTextStyle(fontSizeSp = 26),
                bodyLarge = UiTextStyle(fontSizeSp = 20),
                bodyMedium = UiTextStyle(fontSizeSp = 17),
                labelMedium = UiTextStyle(fontSizeSp = 14),
                labelSmall = UiTextStyle(fontSizeSp = 12),
            ),
            shapes = UiShapes(
                small = UiShape.rounded(22),
                medium = UiShape.rounded(20),
            ),
        )
        var secondaryContainer = 0
        var errorColor = 0
        var compactTextSize = 0
        var largeTextSize = 0
        var listHeadlineSize = 0
        var topTitleSize = 0
        var smallShape = UiShape.rounded(0)

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
        assertEquals(customTheme.colors.onErrorContainer, errorColor)
        assertEquals(customTheme.typography.labelSmall.fontSizeSp, compactTextSize)
        assertEquals(customTheme.typography.bodyLarge.fontSizeSp, largeTextSize)
        assertEquals(customTheme.typography.bodyLarge.fontSizeSp, listHeadlineSize)
        assertEquals(customTheme.typography.titleMedium.fontSizeSp, topTitleSize)
        assertEquals(customTheme.shapes.small, smallShape)
    }

    @Test
    fun `tiered typography defaults map to base tokens when tiers are omitted`() {
        val typography = UiTypography(
            titleMedium = UiTextStyle(fontSizeSp = 30),
            bodyMedium = UiTextStyle(fontSizeSp = 18),
            labelMedium = UiTextStyle(fontSizeSp = 14),
        )

        assertEquals(typography.titleMedium, typography.titleLarge)
        assertEquals(typography.titleMedium, typography.titleSmall)
        assertEquals(typography.bodyMedium, typography.bodyLarge)
        assertEquals(typography.bodyMedium, typography.bodySmall)
        assertEquals(typography.labelMedium, typography.labelLarge)
        assertEquals(typography.labelMedium, typography.labelSmall)
    }
}
