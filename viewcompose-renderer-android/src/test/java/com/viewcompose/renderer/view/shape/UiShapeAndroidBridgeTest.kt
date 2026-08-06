package com.viewcompose.renderer.view.shape

import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.UiDensity

/*
 * 测试职责：覆盖 renderer view/shape 中的 Ui Shape Android Bridge 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Ui Shape Android Bridge behavior in renderer view/shape and guards render and patch contracts against regressions.
 */

import android.graphics.RectF
import android.view.View
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UiShapeDrawableTest {
    private val bounds = RectF(0f, 0f, 200f, 100f)
    private val shape = UiShape(
        topStart = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(12.dp)),
        topEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Relative(0.5f)),
        bottomEnd = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(20.dp)),
        bottomStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(4.dp)),
    )

    @Test
    fun `resolves logical shape in ltr`() {
        val corners = shape.resolveCorners(View.LAYOUT_DIRECTION_LTR, UiDensity.Default, bounds)

        assertEquals(UiCornerFamily.Cut, corners.topLeft.family)
        assertEquals(12f, corners.topLeft.size, 0.001f)
        assertEquals(UiCornerFamily.Rounded, corners.topRight.family)
        assertEquals(50f, corners.topRight.size, 0.001f)
        assertEquals(UiCornerFamily.Cut, corners.bottomRight.family)
        assertEquals(20f, corners.bottomRight.size, 0.001f)
        assertEquals(4f, corners.bottomLeft.size, 0.001f)
    }

    @Test
    fun `swaps logical start and end in rtl`() {
        val corners = shape.resolveCorners(View.LAYOUT_DIRECTION_RTL, UiDensity.Default, bounds)

        assertEquals(UiCornerFamily.Rounded, corners.topLeft.family)
        assertEquals(50f, corners.topLeft.size, 0.001f)
        assertEquals(UiCornerFamily.Cut, corners.topRight.family)
        assertEquals(12f, corners.topRight.size, 0.001f)
        assertEquals(4f, corners.bottomRight.size, 0.001f)
        assertEquals(20f, corners.bottomLeft.size, 0.001f)
    }
}
