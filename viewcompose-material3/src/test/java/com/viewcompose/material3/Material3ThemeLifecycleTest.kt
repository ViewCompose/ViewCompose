package com.viewcompose.material3

/*
 * 测试职责：覆盖 widget-core theme 中的 Android Theme Lifecycle 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Android Theme Lifecycle behavior in widget-core theme and guards DSL, state, or theme contracts against regressions.
 */

import android.content.Context
import android.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import com.viewcompose.ui.foundation.UiThemeOrigin
import com.viewcompose.material3.test.R as TestR
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class Material3ThemeLifecycleTest {
    @Test
    fun `dynamic color policy records the resolved token origin`() {
        val tokens = Material3ThemeBridge.fromContext(themedContext())
        val expectedOrigin = if (DynamicColors.isDynamicColorAvailable()) {
            UiThemeOrigin.AndroidDynamicColor
        } else {
            UiThemeOrigin.AndroidTheme
        }

        assertEquals(expectedOrigin, tokens.metadata.origin)
    }

    @Test
    fun `android shape appearance preserves corner family size and percentage`() {
        val context = themedContext()

        val tokens = Material3ThemeBridge.fromContext(
            context = context,
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
        )

        assertEquals(UiCornerFamily.Cut, tokens.shapes.small.topStart.family)
        assertEquals(UiCornerSize.Absolute(12.dp), tokens.shapes.small.topStart.size)
        assertEquals(UiCornerFamily.Rounded, tokens.shapes.small.topEnd.family)
        assertEquals(UiCornerSize.Relative(0.5f), tokens.shapes.small.topEnd.size)
        assertEquals(UiCornerFamily.Cut, tokens.shapes.small.bottomStart.family)
        assertEquals(UiCornerSize.Absolute(20.dp), tokens.shapes.small.bottomStart.size)
        assertEquals(UiThemeOrigin.AndroidTheme, tokens.metadata.origin)
    }

    @Test
    fun `legacy title fallback does not collapse new display and headline roles`() {
        val tokens = Material3ThemeBridge.fromContext(
            context = themedContext(),
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
        )

        assertEquals(30.sp, tokens.typography.titleLarge.fontSizeSp)
        assertEquals(32.sp, tokens.typography.headlineLarge.fontSizeSp)
        assertEquals(57.sp, tokens.typography.displayLarge.fontSizeSp)
    }

    @Test
    fun `android theme bridges extended material roles and state lists`() {
        val tokens = Material3ThemeBridge.fromContext(
            context = themedContext(),
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
        )

        assertEquals(0xFF304050.toInt(), tokens.colors.tertiary)
        assertEquals(0xFFF0F1F2.toInt(), tokens.colors.onTertiary)
        assertEquals(0xFF506070.toInt(), tokens.colors.tertiaryContainer)
        assertEquals(0xFFE0E1E2.toInt(), tokens.colors.onTertiaryContainer)
        assertEquals(0xFF111213.toInt(), tokens.colors.surfaceDim)
        assertEquals(0xFFFAFBFC.toInt(), tokens.colors.surfaceBright)
        assertEquals(0xFF212223.toInt(), tokens.colors.surfaceContainerLowest)
        assertEquals(0xFF313233.toInt(), tokens.colors.surfaceContainerLow)
        assertEquals(0xFF414243.toInt(), tokens.colors.surfaceContainer)
        assertEquals(0xFF515253.toInt(), tokens.colors.surfaceContainerHigh)
        assertEquals(0xFF616263.toInt(), tokens.colors.surfaceContainerHighest)
        assertEquals(0xFF718293.toInt(), tokens.colors.inversePrimary)
        assertEquals(tokens.colors.primary, tokens.colors.surfaceTint)

        assertEquals(0xFF333333.toInt(), tokens.stateColors.primaryText.defaultColor)
        assertEquals(0xFF111111.toInt(), tokens.stateColors.primaryText.disabledColor)
        assertEquals(0xFF222222.toInt(), tokens.stateColors.primaryText.pressedColor)
        assertEquals(0xFF555555.toInt(), tokens.stateColors.secondaryText.defaultColor)
        assertEquals(0xFF444444.toInt(), tokens.stateColors.secondaryText.disabledColor)
        assertEquals(0xFF888888.toInt(), tokens.stateColors.control.defaultColor)
        assertEquals(0xFF666666.toInt(), tokens.stateColors.control.disabledColor)
        assertEquals(0xFF777777.toInt(), tokens.stateColors.control.checkedColor)
        assertEquals(0xFFBBBBBB.toInt(), tokens.stateColors.controlActivated.defaultColor)
        assertEquals(0xFFAAAAAA.toInt(), tokens.stateColors.controlActivated.checkedColor)
        assertEquals(0xFFCCCCCC.toInt(), tokens.stateColors.controlHighlight.pressedColor)
    }

    @Test
    fun `resolved theme refresh preserves stable context identity`() {
        val resolvedTheme = Material3ThemeBridge.resolveContext(
            context = themedContext(),
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
        )
        val stableContext = resolvedTheme.context

        resolvedTheme.refresh()

        assertEquals(stableContext, resolvedTheme.context)
        assertEquals(
            UiThemeOrigin.AndroidTheme,
            Material3ThemeBridge.fromResolvedTheme(resolvedTheme).metadata.origin,
        )
    }

    private fun themedContext(): Context {
        return ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            TestR.style.ViewComposeTestTheme,
        )
    }
}
