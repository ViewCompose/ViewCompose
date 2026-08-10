package com.viewcompose.ui.shape

import com.viewcompose.ui.unit.dp
/*
 * 测试职责：覆盖 UI contract 中的 Ui Shape 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Ui Shape behavior in UI contract and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiShapeTest {
    @Test
    fun `uniform absolute shape exposes size`() {
        assertEquals(18.dp, UiShape.rounded(18.dp).uniformAbsoluteSizeOrNull)
        assertEquals(20.dp, UiShape.continuous(20.dp).uniformAbsoluteSizeOrNull)
        assertEquals(
            UiCornerFamily.Continuous,
            UiShape.continuous(20.dp).topStart.family,
        )
        assertNull(UiShape.roundedRelative(0.5f).uniformAbsoluteSizeOrNull)
    }

    @Test
    fun `inset reduces absolute corners and preserves relative corners`() {
        val shape = UiShape(
            topStart = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(10.dp)),
            topEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Relative(0.5f)),
            bottomEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(2.dp)),
            bottomStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(6.dp)),
        )

        val inset = shape.inset(4.dp)

        assertEquals(UiCornerSize.Absolute(6.dp), inset.topStart.size)
        assertEquals(UiCornerSize.Relative(0.5f), inset.topEnd.size)
        assertEquals(UiCornerSize.Absolute(0.dp), inset.bottomEnd.size)
        assertEquals(UiCornerSize.Absolute(2.dp), inset.bottomStart.size)
    }
}
