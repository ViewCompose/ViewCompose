package com.viewcompose

/*
 * 测试职责：覆盖 app demo 中的 Demo Theme Tokens 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Demo Theme Tokens behavior in app demo and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DemoThemeTokensTest {
    @Test
    fun `light theme matches app day palette`() {
        assertEquals(0xFFF7F2EA.toInt(), DemoThemeTokens.light.colors.background)
        assertEquals(0xFFEFE4D2.toInt(), DemoThemeTokens.light.colors.surface)
        assertEquals(0xFF7B9E68.toInt(), DemoThemeTokens.light.colors.primary)
        assertEquals(0xFF9A7AAE.toInt(), DemoThemeTokens.light.colors.secondary)
        assertEquals(0xFF2F241B.toInt(), DemoThemeTokens.light.colors.onSurface)
        assertEquals(false, DemoThemeTokens.light.metadata.isDark)
    }

    @Test
    fun `dark theme matches app night palette`() {
        assertEquals(0xFF1F1B18.toInt(), DemoThemeTokens.dark.colors.background)
        assertEquals(0xFF2C2621.toInt(), DemoThemeTokens.dark.colors.surface)
        assertEquals(0xFF98C27F.toInt(), DemoThemeTokens.dark.colors.primary)
        assertEquals(0xFFB39AC9.toInt(), DemoThemeTokens.dark.colors.secondary)
        assertEquals(0xFFF4EFE8.toInt(), DemoThemeTokens.dark.colors.onSurface)
        assertEquals(true, DemoThemeTokens.dark.metadata.isDark)
    }

    @Test
    fun `system mode follows passed dark flag`() {
        assertSame(DemoThemeTokens.light, DemoThemeTokens.resolve(DemoThemeMode.System, isSystemDark = false))
        assertSame(DemoThemeTokens.dark, DemoThemeTokens.resolve(DemoThemeMode.System, isSystemDark = true))
        assertSame(DemoThemeTokens.light, DemoThemeTokens.resolve(DemoThemeMode.Light, isSystemDark = true))
        assertSame(DemoThemeTokens.dark, DemoThemeTokens.resolve(DemoThemeMode.Dark, isSystemDark = false))
    }
}
