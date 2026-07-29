package com.viewcompose.animation

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationUnitConvertersTest {
    @Test
    fun `dp converter preserves logical fractional values`() {
        val vector = AnimationUnitConverters.Dp.toVector(12.5f.dp)
        val restored = AnimationUnitConverters.Dp.fromVector(vector)

        assertEquals(floatArrayOf(12.5f).toList(), vector.toList())
        assertEquals(UiDp(12.5f), restored)
    }
}
