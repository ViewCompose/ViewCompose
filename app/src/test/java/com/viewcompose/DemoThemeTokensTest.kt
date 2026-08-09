package com.viewcompose

/*
 * 测试职责：覆盖 app demo 中的 Demo Theme Tokens 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Demo Theme Tokens behavior in app demo and guards the contract against regressions.
 */

import com.viewcompose.ui.foundation.UiThemeOrigin
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DemoThemeTokensTest {
    @Test
    fun `light theme uses a distinct custom verification palette`() {
        assertEquals(0xFFF4FBF8.toInt(), DemoThemeTokens.light.colors.background)
        assertEquals(0xFFF4FBF8.toInt(), DemoThemeTokens.light.colors.surface)
        assertEquals(0xFF006A60.toInt(), DemoThemeTokens.light.colors.primary)
        assertNotEquals(0xFF7B9E68.toInt(), DemoThemeTokens.light.colors.primary)
        assertEquals(0xFF4A635E.toInt(), DemoThemeTokens.light.colors.secondary)
        assertEquals(0xFFCCE8E1.toInt(), DemoThemeTokens.light.colors.secondaryContainer)
        assertEquals(0xFF06201C.toInt(), DemoThemeTokens.light.colors.onSecondaryContainer)
        assertNotEquals(
            DemoThemeTokens.light.colors.secondary,
            DemoThemeTokens.light.colors.secondaryContainer,
        )
        assertEquals(0xFF161D1B.toInt(), DemoThemeTokens.light.colors.onSurface)
        assertEquals(false, DemoThemeTokens.light.metadata.isDark)
    }

    @Test
    fun `dark theme uses a distinct custom verification palette`() {
        assertEquals(0xFF0E1513.toInt(), DemoThemeTokens.dark.colors.background)
        assertEquals(0xFF0E1513.toInt(), DemoThemeTokens.dark.colors.surface)
        assertEquals(0xFF53DBC8.toInt(), DemoThemeTokens.dark.colors.primary)
        assertNotEquals(0xFF98C27F.toInt(), DemoThemeTokens.dark.colors.primary)
        assertEquals(0xFFB1CCC5.toInt(), DemoThemeTokens.dark.colors.secondary)
        assertEquals(0xFF334B46.toInt(), DemoThemeTokens.dark.colors.secondaryContainer)
        assertEquals(0xFFCCE8E1.toInt(), DemoThemeTokens.dark.colors.onSecondaryContainer)
        assertNotEquals(
            DemoThemeTokens.dark.colors.secondary,
            DemoThemeTokens.dark.colors.secondaryContainer,
        )
        assertEquals(0xFFDDE4E1.toInt(), DemoThemeTokens.dark.colors.onSurface)
        assertEquals(true, DemoThemeTokens.dark.metadata.isDark)
    }

    @Test
    fun `system mode follows passed dark flag`() {
        assertSame(DemoThemeTokens.light, DemoThemeTokens.select(DemoThemeMode.System, isSystemDark = false))
        assertSame(DemoThemeTokens.dark, DemoThemeTokens.select(DemoThemeMode.System, isSystemDark = true))
        assertSame(DemoThemeTokens.light, DemoThemeTokens.select(DemoThemeMode.Light, isSystemDark = true))
        assertSame(DemoThemeTokens.dark, DemoThemeTokens.select(DemoThemeMode.Dark, isSystemDark = false))
    }

    @Test
    fun `theme tokens retain logical dimensions and stable identity`() {
        assertEquals(80.dp, DemoThemeTokens.light.controls.navigationBar.height)
        assertEquals(44.dp, DemoThemeTokens.light.controls.segmentedControl.mediumHeight)
        assertSame(
            DemoThemeTokens.light,
            DemoThemeTokens.select(DemoThemeMode.Light, isSystemDark = true),
        )
    }

    @Test
    fun `theme verification sources stay isolated`() {
        assertNull(DemoThemeSource.AndroidXml.tokens(isDark = false))
        assertEquals(
            0xFFE8DEF8.toInt(),
            DemoThemeSource.Material3Defaults.tokens(isDark = false)?.colors?.secondaryContainer,
        )
        val custom = requireNotNull(DemoThemeSource.DemoCustom.tokens(isDark = false))
        assertNotSame(DemoThemeTokens.light, custom)
        assertEquals(DemoThemeTokens.light.colors, custom.colors)
        assertEquals(DemoThemeTokens.light.typography, custom.typography)
        assertEquals(DemoThemeTokens.light.stateColors, custom.stateColors)
        assertEquals(DemoThemeTokens.light.shapes, custom.shapes)
        assertEquals(DemoThemeTokens.light.controls, custom.controls)
        assertEquals(DemoThemeTokens.light.interactions, custom.interactions)
        assertEquals(DemoThemeTokens.light.overlays, custom.overlays)
        assertEquals(UiThemeOrigin.Override, custom.metadata.origin)
        assertEquals("viewcompose-material3/static", custom.metadata.provenance.sourceId)
        assertEquals(UiThemeOrigin.Override, custom.metadata.provenance.originOf("colors.primary"))
        assertEquals(UiThemeOrigin.Override, custom.metadata.provenance.originOf("shapes.medium"))
        assertEquals(
            DemoThemeSource.Material3Defaults,
            DemoThemeSource.fromId("unknown-source"),
        )
    }
}
