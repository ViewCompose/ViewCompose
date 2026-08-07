package com.viewcompose.material3

/*
 * Test responsibility: pins the project Material 3 baseline and verifies that foundation
 * component defaults consume semantic Material roles instead of framework fallback values.
 */

import com.viewcompose.ui.foundation.AlertDialogDefaults
import com.viewcompose.ui.foundation.ButtonDefaults
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.CardDefaults
import com.viewcompose.ui.foundation.CardVariant
import com.viewcompose.ui.foundation.IconButtonDefaults
import com.viewcompose.ui.foundation.ProgressIndicatorDefaults
import com.viewcompose.ui.foundation.SearchBarDefaults
import com.viewcompose.ui.foundation.TextFieldDefaults
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class Material3ThemeDefaultsTest {
    @Test
    fun `light snapshot pins standard Material 3 color roles`() {
        val theme = Material3ThemeDefaults.light()

        assertEquals(0xFFFEF7FF.toInt(), theme.colors.surface)
        assertEquals(0xFF6750A4.toInt(), theme.colors.primary)
        assertEquals(0xFFE8DEF8.toInt(), theme.colors.secondaryContainer)
        assertEquals(0xFFE6E0E9.toInt(), theme.colors.surfaceContainerHighest)
        assertEquals(0xFF79747E.toInt(), theme.colors.outline)
        assertEquals(0xFFCAC4D0.toInt(), theme.colors.outlineVariant)
        assertEquals(false, theme.metadata.isDark)
    }

    @Test
    fun `snapshot exposes complete Material 3 type and shape scales`() {
        val theme = Material3ThemeDefaults.light()

        assertEquals(57.sp, theme.typography.displayLarge.fontSizeSp)
        assertEquals(64.sp, theme.typography.displayLarge.lineHeightSp)
        assertEquals(32.sp, theme.typography.headlineLarge.fontSizeSp)
        assertEquals(24.sp, theme.typography.headlineSmall.fontSizeSp)
        assertEquals(22.sp, theme.typography.titleLarge.fontSizeSp)
        assertEquals(16.sp, theme.typography.bodyLarge.fontSizeSp)
        assertEquals(14.sp, theme.typography.labelLarge.fontSizeSp)

        assertEquals(UiShape.rounded(4.dp), theme.shapes.extraSmall)
        assertEquals(UiShape.rounded(8.dp), theme.shapes.small)
        assertEquals(UiShape.rounded(12.dp), theme.shapes.medium)
        assertEquals(UiShape.rounded(16.dp), theme.shapes.large)
        assertEquals(UiShape.rounded(28.dp), theme.shapes.extraLarge)
        assertEquals(UiShape.roundedRelative(0.5f), theme.shapes.full)
    }

    @Test
    fun `snapshot pins component sizing values selected for this baseline`() {
        val theme = Material3ThemeDefaults.light()

        assertEquals(48.dp, theme.controls.button.mediumHeight)
        assertEquals(48.dp, theme.controls.minimumInteractiveHeight)
        assertEquals(40.dp, theme.controls.button.compactVisualHeight)
        assertEquals(40.dp, theme.controls.button.mediumVisualHeight)
        assertEquals(48.dp, theme.controls.button.largeVisualHeight)
        assertEquals(24.dp, theme.controls.button.mediumHorizontalPadding)
        assertEquals(56.dp, theme.controls.textField.mediumHeight)
        assertEquals(16.dp, theme.controls.textField.mediumHorizontalPadding)
        assertEquals(4.dp, theme.controls.progressIndicator.linearTrackThickness)
        assertEquals(40.dp, theme.controls.progressIndicator.circularSize)
        assertEquals(6.dp, theme.controls.badge.dotSize)
        assertEquals(0.10f, theme.interactions.pressedStateLayerOpacity)
        assertEquals(0.10f, theme.interactions.focusedStateLayerOpacity)
        assertEquals(0.08f, theme.interactions.hoveredStateLayerOpacity)
    }

    @Test
    fun `component defaults consume Material 3 semantic roles`() {
        val theme = Material3ThemeDefaults.light()
        var buttonShape = UiShape.rounded(0.dp)
        var textFieldShape = UiShape.rounded(0.dp)
        var dialogShape = UiShape.rounded(0.dp)
        var buttonContent = 0
        var progressTrack = 0
        var filledTextField = 0
        var elevatedCard = 0
        var searchContainer = 0
        var dialogTitleSize = 0.sp

        buildVNodeTree {
            UiTheme(theme) {
                buttonShape = ButtonDefaults.shape()
                textFieldShape = TextFieldDefaults.shape()
                dialogShape = AlertDialogDefaults.shape()
                buttonContent = ButtonDefaults.contentColor(ButtonVariant.Outlined)
                progressTrack = ProgressIndicatorDefaults.linearTrackColor()
                filledTextField = TextFieldDefaults.containerColor()
                elevatedCard = CardDefaults.containerColor(CardVariant.Elevated)
                searchContainer = SearchBarDefaults.containerColor()
                dialogTitleSize = AlertDialogDefaults.titleStyle().fontSizeSp
            }
        }

        assertEquals(theme.shapes.full, buttonShape)
        assertEquals(theme.shapes.extraSmall, textFieldShape)
        assertEquals(theme.shapes.extraLarge, dialogShape)
        assertEquals(theme.colors.primary, buttonContent)
        assertEquals(theme.colors.secondaryContainer, progressTrack)
        assertEquals(theme.colors.surfaceContainerHighest, filledTextField)
        assertEquals(theme.colors.surfaceContainerLow, elevatedCard)
        assertEquals(theme.colors.surfaceContainerHigh, searchContainer)
        assertEquals(theme.typography.headlineSmall.fontSizeSp, dialogTitleSize)
    }

    @Test
    fun `button state layers use variant content roles and standard opacities`() {
        listOf(Material3ThemeDefaults.light(), Material3ThemeDefaults.dark()).forEach { theme ->
            var primaryPressed = 0
            var primaryFocused = 0
            var primaryHovered = 0
            var tonalPressed = 0
            var outlinedPressed = 0
            var iconPressed = 0

            buildVNodeTree {
                UiTheme(theme) {
                    ButtonDefaults.stateLayerColors(ButtonVariant.Primary).let { colors ->
                        primaryPressed = colors.pressedColor
                        primaryFocused = colors.focusedColor
                        primaryHovered = colors.hoveredColor
                    }
                    tonalPressed = ButtonDefaults.stateLayerColors(ButtonVariant.Tonal).pressedColor
                    outlinedPressed =
                        ButtonDefaults.stateLayerColors(ButtonVariant.Outlined).pressedColor
                    iconPressed = IconButtonDefaults.stateLayerColors().pressedColor
                }
            }

            assertEquals(theme.colors.onPrimary.withStateAlpha(0x1A), primaryPressed)
            assertEquals(theme.colors.onPrimary.withStateAlpha(0x1A), primaryFocused)
            assertEquals(theme.colors.onPrimary.withStateAlpha(0x14), primaryHovered)
            assertEquals(theme.colors.onSecondaryContainer.withStateAlpha(0x1A), tonalPressed)
            assertEquals(theme.colors.primary.withStateAlpha(0x1A), outlinedPressed)
            assertEquals(theme.colors.onSurfaceVariant.withStateAlpha(0x1A), iconPressed)
        }
    }

    private fun Int.withStateAlpha(alpha: Int): Int = (alpha shl 24) or (this and 0x00FFFFFF)
}
