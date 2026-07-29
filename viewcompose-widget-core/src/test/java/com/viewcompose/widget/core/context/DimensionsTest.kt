package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core context 中的 Dimensions 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Dimensions behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.unit.UiDensity
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
                        fontScale = 1.5f,
                    ),
                    locales = UiLocaleList.of("en-US"),
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
    @Test
    fun `control defaults can resolve from explicit density without an environment`() {
        val controls = UiControlSizeDefaults.default(
            density = UiDensity(
                density = 3f,
                fontScale = 4f / 3f,
            ),
        )

        assertEquals(132, controls.button.mediumHeight)
        assertEquals(126, controls.segmentedControl.mediumHeight)
        assertEquals(240, controls.navigationBar.height)
        assertEquals(168, controls.fab.mediumSize)
    }

    @Test
    fun `theme defaults pass explicit density to every size domain`() {
        val tokens = UiThemeDefaults.light(
            density = UiDensity(
                density = 2f,
                fontScale = 1.5f,
            ),
        )

        assertEquals(88, tokens.controls.button.mediumHeight)
        assertEquals(84, tokens.controls.segmentedControl.mediumHeight)
        assertEquals(160, tokens.controls.navigationBar.height)
        assertEquals(112, tokens.controls.fab.mediumSize)
        assertEquals(40, tokens.shapes.medium.uniformAbsoluteSizeOrNull)
    }
}
