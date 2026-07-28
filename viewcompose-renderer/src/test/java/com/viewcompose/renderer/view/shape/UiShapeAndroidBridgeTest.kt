package com.viewcompose.renderer.view.shape

/*
 * 测试职责：覆盖 renderer view/shape 中的 Ui Shape Android Bridge 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Ui Shape Android Bridge behavior in renderer view/shape and guards render and patch contracts against regressions.
 */

import android.graphics.RectF
import android.view.View
import com.google.android.material.shape.CutCornerTreatment
import com.google.android.material.shape.RoundedCornerTreatment
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
class UiShapeAndroidBridgeTest {
    private val bounds = RectF(0f, 0f, 200f, 100f)
    private val shape = UiShape(
        topStart = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(12)),
        topEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Relative(0.5f)),
        bottomEnd = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(20)),
        bottomStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(4)),
    )

    @Test
    fun `maps logical shape to material model in ltr`() {
        val model = shape.toShapeAppearanceModel(View.LAYOUT_DIRECTION_LTR)

        assertTrue(model.topLeftCorner is CutCornerTreatment)
        assertEquals(12f, model.topLeftCornerSize.getCornerSize(bounds), 0.001f)
        assertTrue(model.topRightCorner is RoundedCornerTreatment)
        assertEquals(50f, model.topRightCornerSize.getCornerSize(bounds), 0.001f)
        assertTrue(model.bottomRightCorner is CutCornerTreatment)
        assertEquals(20f, model.bottomRightCornerSize.getCornerSize(bounds), 0.001f)
        assertEquals(4f, model.bottomLeftCornerSize.getCornerSize(bounds), 0.001f)
    }

    @Test
    fun `swaps logical start and end in rtl`() {
        val model = shape.toShapeAppearanceModel(View.LAYOUT_DIRECTION_RTL)

        assertTrue(model.topLeftCorner is RoundedCornerTreatment)
        assertEquals(50f, model.topLeftCornerSize.getCornerSize(bounds), 0.001f)
        assertTrue(model.topRightCorner is CutCornerTreatment)
        assertEquals(12f, model.topRightCornerSize.getCornerSize(bounds), 0.001f)
        assertEquals(4f, model.bottomRightCornerSize.getCornerSize(bounds), 0.001f)
        assertEquals(20f, model.bottomLeftCornerSize.getCornerSize(bounds), 0.001f)
    }
}
