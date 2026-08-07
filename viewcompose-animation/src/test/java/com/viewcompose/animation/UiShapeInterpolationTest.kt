package com.viewcompose.animation

import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UiShapeInterpolationTest {
    @Test
    fun `compatible absolute corners interpolate without changing family`() {
        val result = interpolateUiShape(
            start = UiShape.continuous(8.dp),
            end = UiShape.continuous(24.dp),
            fraction = 0.25f,
        )

        assertEquals(UiShapeInterpolationMode.Compatible, result.mode)
        assertEquals(UiCornerFamily.Continuous, result.shape.topStart.family)
        assertEquals(UiCornerSize.Absolute(12.dp), result.shape.topStart.size)
    }

    @Test
    fun `compatible relative corners clamp progress before interpolation`() {
        val result = interpolateUiShape(
            start = UiShape.roundedRelative(0.2f),
            end = UiShape.roundedRelative(0.5f),
            fraction = 2f,
        )

        assertEquals(UiShapeInterpolationMode.Compatible, result.mode)
        assertEquals(UiCornerSize.Relative(0.5f), result.shape.topStart.size)
    }

    @Test
    fun `incompatible families select endpoints around midpoint`() {
        val start = UiShape.rounded(12.dp)
        val end = UiShape.cut(12.dp)

        val before = interpolateUiShape(start, end, 0.49f)
        val after = interpolateUiShape(start, end, 0.5f)

        assertEquals(UiShapeInterpolationMode.DiscreteFallback, before.mode)
        assertSame(start, before.shape)
        assertEquals(UiShapeInterpolationMode.DiscreteFallback, after.mode)
        assertSame(end, after.shape)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite progress is rejected`() {
        interpolateUiShape(UiShape.rounded(0.dp), UiShape.rounded(8.dp), Float.NaN)
    }
}
