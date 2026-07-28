package com.viewcompose.animation.core

/*
 * 测试职责：覆盖 animation core 中的 Animation Converters 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Animation Converters behavior in animation core and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationConvertersTest {
    @Test
    fun `color converter round-trips argb channels`() {
        val color = 0xCC3366AA.toInt()
        val vector = AnimationConverters.ColorInt.toVector(color)
        val restored = AnimationConverters.ColorInt.fromVector(vector)
        assertEquals(color, restored)
    }
}
