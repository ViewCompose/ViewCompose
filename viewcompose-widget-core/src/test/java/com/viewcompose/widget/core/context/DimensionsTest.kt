package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core context 中的 Dimensions 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Dimensions behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionsTest {
    @Test
    fun `dp uses current environment density`() {
        var resolved = 0
        var floatResolved = 0

        buildVNodeTree {
            UiEnvironment(
                values = UiEnvironmentValues(
                    density = UiDensity(
                        density = 2f,
                        scaledDensity = 3f,
                    ),
                    localeTags = listOf("en-US"),
                    layoutDirection = UiLayoutDirection.Ltr,
                ),
            ) {
                resolved = 8.dp
                floatResolved = 8.4f.dp
            }
        }

        assertEquals(16, resolved)
        assertEquals(16, floatResolved)
    }

    @Test
    fun `sp keeps semantic text units`() {
        assertEquals(14, 14.sp)
        assertEquals(15, 14.6f.sp)
    }
}
