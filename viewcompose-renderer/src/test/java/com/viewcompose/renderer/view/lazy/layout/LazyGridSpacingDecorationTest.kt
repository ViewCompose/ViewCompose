package com.viewcompose.renderer.view.lazy.layout

/*
 * 测试职责：覆盖 renderer view/lazy/layout 中的 Lazy Grid Spacing Decoration 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy Grid Spacing Decoration behavior in renderer view/lazy/layout and guards render and patch contracts against regressions.
 */

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyGridSpacingDecorationTest {
    @Test
    fun `update reports whether grid item offsets changed`() {
        val decoration = LazyGridSpacingDecoration(
            horizontalSpacing = 8,
            verticalSpacing = 12,
            spanCount = 2,
        )

        assertFalse(decoration.update(horizontalSpacing = 8, verticalSpacing = 12, spanCount = 2))
        assertTrue(decoration.update(horizontalSpacing = 16, verticalSpacing = 12, spanCount = 2))
        assertTrue(decoration.update(horizontalSpacing = 16, verticalSpacing = 12, spanCount = 3))
    }
}
