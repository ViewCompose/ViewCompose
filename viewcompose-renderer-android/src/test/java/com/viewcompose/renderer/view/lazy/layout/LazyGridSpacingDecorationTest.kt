package com.viewcompose.renderer.view.lazy.layout

import org.junit.Assert.assertEquals
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

    @Test
    fun `spacing uses span bounds and mirrors physical offsets in rtl`() {
        assertEquals(
            LazyGridItemOffsets(left = 0, top = 0, right = 4),
            calculateGridItemOffsets(
                horizontalSpacing = 8,
                verticalSpacing = 12,
                spanCount = 4,
                spanIndex = 0,
                spanSize = 2,
                spanGroupIndex = 0,
                isRtl = false,
            ),
        )
        assertEquals(
            LazyGridItemOffsets(left = 4, top = 12, right = 0),
            calculateGridItemOffsets(
                horizontalSpacing = 8,
                verticalSpacing = 12,
                spanCount = 4,
                spanIndex = 0,
                spanSize = 2,
                spanGroupIndex = 1,
                isRtl = true,
            ),
        )
        assertEquals(
            LazyGridItemOffsets(left = 0, top = 12, right = 0),
            calculateGridItemOffsets(
                horizontalSpacing = 8,
                verticalSpacing = 12,
                spanCount = 4,
                spanIndex = 0,
                spanSize = 4,
                spanGroupIndex = 2,
                isRtl = false,
            ),
        )
    }

    @Test
    fun `spacing calculation does not overflow before division`() {
        assertEquals(
            LazyGridItemOffsets(
                left = 1_431_655_764,
                top = 0,
                right = 0,
            ),
            calculateGridItemOffsets(
                horizontalSpacing = Int.MAX_VALUE,
                verticalSpacing = 0,
                spanCount = 3,
                spanIndex = 2,
                spanSize = 1,
                spanGroupIndex = 0,
                isRtl = false,
            ),
        )
    }
}
