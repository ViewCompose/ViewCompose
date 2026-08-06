package com.viewcompose.renderer.layout

/*
 * 测试职责：覆盖 renderer layout 中的 Cross Axis Placement Calculator 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Cross Axis Placement Calculator behavior in renderer layout and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossAxisPlacementCalculatorTest {
    @Test
    fun `center horizontal alignment respects margins`() {
        val result = CrossAxisPlacementCalculator.calculateHorizontal(
            containerSize = 120,
            childSize = 40,
            leadingMargin = 10,
            trailingMargin = 6,
            alignment = HorizontalAlignment.Center,
        )

        assertEquals(42, result)
    }

    @Test
    fun `end vertical alignment respects margins`() {
        val result = CrossAxisPlacementCalculator.calculateVertical(
            containerSize = 140,
            childSize = 30,
            leadingMargin = 8,
            trailingMargin = 12,
            alignment = VerticalAlignment.Bottom,
        )

        assertEquals(98, result)
    }

    @Test
    fun `oversized child clamps to leading margin`() {
        val result = CrossAxisPlacementCalculator.calculateHorizontal(
            containerSize = 60,
            childSize = 80,
            leadingMargin = 6,
            trailingMargin = 4,
            alignment = HorizontalAlignment.End,
        )

        assertEquals(6, result)
    }
}
