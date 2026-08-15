package com.viewcompose.renderer.view.tree

import android.view.Gravity
import com.viewcompose.ui.node.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentTextGravityTest {
    @Test
    fun `text alignment contributes only horizontal gravity`() {
        val alignments = mapOf(
            TextAlign.Start to Gravity.START,
            TextAlign.Center to Gravity.CENTER_HORIZONTAL,
            TextAlign.End to Gravity.END,
        )

        alignments.forEach { (alignment, expected) ->
            val actual = ContentViewBinder.toTextGravity(alignment)

            assertEquals(expected, actual)
            assertEquals(0, actual and Gravity.VERTICAL_GRAVITY_MASK)
        }
    }
}
