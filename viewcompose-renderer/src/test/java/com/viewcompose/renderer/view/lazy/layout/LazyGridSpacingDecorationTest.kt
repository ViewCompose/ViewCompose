package com.viewcompose.renderer.view.lazy.layout

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
