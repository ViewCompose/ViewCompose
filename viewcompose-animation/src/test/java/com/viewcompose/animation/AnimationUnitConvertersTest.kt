package com.viewcompose.animation

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationUnitConvertersTest {
    @Test
    fun `dp converter preserves logical fractional values`() {
        val vector = FloatArray(AnimationUnitConverters.Dp.vectorSize)
        AnimationUnitConverters.Dp.convertToVector(12.5f.dp, vector)
        val restored = AnimationUnitConverters.Dp.convertFromVector(vector)

        assertEquals(floatArrayOf(12.5f).toList(), vector.toList())
        assertEquals(UiDp(12.5f), restored)
    }
}
