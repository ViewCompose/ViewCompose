package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core context 中的 Environment 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Environment behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.unit.UiDensity
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentTest {
    @Test
    fun `environment uses defaults outside provider`() {
        assertEquals(UiLayoutDirection.Ltr, Environment.layoutDirection)
        assertEquals(listOf("und"), Environment.localeTags)
        assertEquals(1f, Environment.density.density)
    }

    @Test
    fun `environment provider exposes nested values`() {
        val customValues = UiEnvironmentValues(
            density = UiDensity(
                density = 2f,
                fontScale = 1.5f,
            ),
            locales = UiLocaleList.of("zh-CN", "en-US"),
            layoutDirection = UiLayoutDirection.Rtl,
        )
        var density = 0f
        var layoutDirection = UiLayoutDirection.Ltr
        var primaryLocale = ""

        buildVNodeTree {
            UiEnvironment(customValues) {
                density = Environment.density.density
                layoutDirection = Environment.layoutDirection
                primaryLocale = Environment.localeTags.first()
            }
        }

        assertEquals(2f, density)
        assertEquals(UiLayoutDirection.Rtl, layoutDirection)
        assertEquals("zh-CN", primaryLocale)
    }

    @Test
    fun `environment is captured by emitted nodes`() {
        val values = UiEnvironmentValues(
            density = UiDensity(
                density = 1.25f,
                fontScale = 1.1f,
            ),
            locales = UiLocaleList.of("en-US"),
            layoutDirection = UiLayoutDirection.Rtl,
        )

        val node = buildVNodeTree {
            UiEnvironment(values) {
                Text("environment")
            }
        }.single()

        assertEquals(values, node.environment)
    }
}
