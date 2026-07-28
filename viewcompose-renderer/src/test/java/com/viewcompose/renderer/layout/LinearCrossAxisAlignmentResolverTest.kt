package com.viewcompose.renderer.layout

/*
 * 测试职责：覆盖 renderer layout 中的 Linear Cross Axis Alignment Resolver 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Linear Cross Axis Alignment Resolver behavior in renderer layout and guards render and patch contracts against regressions.
 */

import android.view.Gravity
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class LinearCrossAxisAlignmentResolverTest {
    @Test
    fun `child vertical gravity overrides container gravity`() {
        val result = LinearCrossAxisAlignmentResolver.resolveVertical(
            containerGravity = Gravity.TOP,
            childGravity = Gravity.BOTTOM,
        )

        assertEquals(VerticalAlignment.Bottom, result)
    }

    @Test
    fun `child horizontal gravity overrides container gravity`() {
        val result = LinearCrossAxisAlignmentResolver.resolveHorizontal(
            containerGravity = Gravity.START,
            childGravity = Gravity.CENTER_HORIZONTAL,
        )

        assertEquals(HorizontalAlignment.Center, result)
    }

    @Test
    fun `container gravity is used when child gravity is absent`() {
        val result = LinearCrossAxisAlignmentResolver.resolveHorizontal(
            containerGravity = Gravity.END,
            childGravity = null,
        )

        assertEquals(HorizontalAlignment.End, result)
    }
}
