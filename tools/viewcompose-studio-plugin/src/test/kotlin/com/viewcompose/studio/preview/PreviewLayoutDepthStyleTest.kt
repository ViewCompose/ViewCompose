package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLayoutDepthStyleTest {
    @Test
    fun `deeper Views receive progressively stronger stable warning styles`() {
        val styles = (1..6).map(::previewLayoutDepthStyle)

        assertTrue(styles.zipWithNext().all { (shallower, deeper) ->
            deeper.fillAlpha > shallower.fillAlpha &&
                deeper.strokeAlpha > shallower.strokeAlpha
        })
        assertEquals(styles.last(), previewLayoutDepthStyle(20))
        assertEquals(1, styles.first().strokeWidth)
        assertEquals(2, styles.last().strokeWidth)
    }
}
