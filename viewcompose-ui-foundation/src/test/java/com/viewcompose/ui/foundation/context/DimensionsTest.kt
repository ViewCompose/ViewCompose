package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core context 中的 Dimensions 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Dimensions behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp
import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionsTest {
    @Test
    fun `dp remains logical inside an overridden environment`() {
        var resolved = UiDp.Zero
        var floatResolved = UiDp.Zero

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

        assertEquals(8.dp, resolved)
        assertEquals(8.4f.dp, floatResolved)
    }

    @Test
    fun `sp keeps semantic text units`() {
        assertEquals(UiSp(14f), 14.sp)
        assertEquals(UiSp(14.6f), 14.6f.sp)
    }

    @Test
    fun `control defaults are density independent`() {
        val controls = UiControlSizeDefaults.default()

        assertEquals(44.dp, controls.button.mediumHeight)
        assertEquals(42.dp, controls.segmentedControl.mediumHeight)
        assertEquals(80.dp, controls.navigationBar.height)
        assertEquals(56.dp, controls.fab.mediumSize)
    }

    @Test
    fun `theme defaults retain logical dimensions`() {
        val tokens = UiThemeDefaults.light()

        assertEquals(44.dp, tokens.controls.button.mediumHeight)
        assertEquals(42.dp, tokens.controls.segmentedControl.mediumHeight)
        assertEquals(80.dp, tokens.controls.navigationBar.height)
        assertEquals(56.dp, tokens.controls.fab.mediumSize)
        assertEquals(20.dp, tokens.shapes.medium.uniformAbsoluteSizeOrNull)
    }
}
