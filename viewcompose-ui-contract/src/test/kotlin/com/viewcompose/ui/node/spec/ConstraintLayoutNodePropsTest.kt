package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConstraintLayoutNodePropsTest {
    @Test
    fun `match constraints keeps mode and bounds in one value`() {
        val dimension = ConstraintDimension.MatchConstraints(
            mode = ConstraintMatchMode.Percent(0.4f),
            min = 24.dp,
            max = 120.dp,
        )

        assertEquals(ConstraintMatchMode.Percent(0.4f), dimension.mode)
        assertEquals(24.dp, dimension.min)
        assertEquals(120.dp, dimension.max)
    }

    @Test
    fun `dimension values reject impossible ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConstraintDimension.Fixed((-1).dp)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConstraintDimension.MatchConstraints(min = 30.dp, max = 20.dp)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConstraintMatchMode.Percent(Float.NaN)
        }
    }

    @Test
    fun `ratio requires positive finite terms`() {
        assertEquals(
            ConstraintRatio(16f, 9f, ConstraintRatioSide.Width),
            ConstraintRatio(16f, 9f, ConstraintRatioSide.Width),
        )
        assertThrows(IllegalArgumentException::class.java) { ConstraintRatio(0f, 9f) }
        assertThrows(IllegalArgumentException::class.java) { ConstraintRatio(16f, Float.POSITIVE_INFINITY) }
    }
}
