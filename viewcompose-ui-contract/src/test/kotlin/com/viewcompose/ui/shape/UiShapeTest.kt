package com.viewcompose.ui.shape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiShapeTest {
    @Test
    fun `uniform absolute shape exposes size`() {
        assertEquals(18, UiShape.rounded(18).uniformAbsoluteSizeOrNull)
        assertNull(UiShape.roundedRelative(0.5f).uniformAbsoluteSizeOrNull)
    }

    @Test
    fun `inset reduces absolute corners and preserves relative corners`() {
        val shape = UiShape(
            topStart = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(10)),
            topEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Relative(0.5f)),
            bottomEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(2)),
            bottomStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(6)),
        )

        val inset = shape.inset(4)

        assertEquals(UiCornerSize.Absolute(6), inset.topStart.size)
        assertEquals(UiCornerSize.Relative(0.5f), inset.topEnd.size)
        assertEquals(UiCornerSize.Absolute(0), inset.bottomEnd.size)
        assertEquals(UiCornerSize.Absolute(2), inset.bottomStart.size)
    }
}
