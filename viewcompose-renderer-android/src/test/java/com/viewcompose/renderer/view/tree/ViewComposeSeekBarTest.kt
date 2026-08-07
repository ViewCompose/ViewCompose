package com.viewcompose.renderer.view.tree

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViewComposeSeekBarTest {
    @Test
    fun `minimum height expands an unconstrained interactive target`() {
        val view = ViewComposeSeekBar(RuntimeEnvironment.getApplication()).apply {
            minimumHeight = 48
        }

        view.measure(exactly(200), atMost(100))

        assertEquals(48, view.measuredHeight)
    }

    @Test
    fun `exact height remains authoritative over minimum target`() {
        val view = ViewComposeSeekBar(RuntimeEnvironment.getApplication()).apply {
            minimumHeight = 48
        }

        view.measure(exactly(200), exactly(32))

        assertEquals(32, view.measuredHeight)
    }

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun atMost(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)
}
