package com.viewcompose.shadow.android

import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DecorationChildDrawingOrderTest {
    @Test
    fun `drawing order is stable by zIndex then real child index`() {
        val host = FrameLayout(RuntimeEnvironment.getApplication())
        val first = View(host.context)
        val second = View(host.context)
        val third = View(host.context)
        val fourth = View(host.context)
        host.addView(first)
        host.addView(second)
        host.addView(third)
        host.addView(fourth)

        DecorationChildDrawingOrder.update(first, 2f)
        DecorationChildDrawingOrder.update(second, -1f)
        DecorationChildDrawingOrder.update(third, 2f)
        DecorationChildDrawingOrder.update(fourth, 0f)

        val order = (0 until host.childCount).map { drawingPosition ->
            DecorationChildDrawingOrder.getChildDrawingOrder(
                parent = host,
                childCount = host.childCount,
                drawingPosition = drawingPosition,
            )
        }

        assertEquals(listOf(1, 3, 0, 2), order)
    }

    @Test
    fun `zIndex update invalidates cached order without touching translationZ`() {
        val host = FrameLayout(RuntimeEnvironment.getApplication())
        val first = View(host.context)
        val second = View(host.context)
        host.addView(first)
        host.addView(second)
        DecorationChildDrawingOrder.update(first, 1f)

        assertEquals(
            listOf(1, 0),
            drawingOrder(host),
        )
        assertEquals(0f, first.translationZ)

        assertTrue(DecorationChildDrawingOrder.update(first, -2f))
        assertFalse(DecorationChildDrawingOrder.update(first, -2f))
        assertEquals(
            listOf(0, 1),
            drawingOrder(host),
        )
        assertEquals(0f, first.translationZ)
    }

    private fun drawingOrder(host: FrameLayout): List<Int> {
        return (0 until host.childCount).map { drawingPosition ->
            DecorationChildDrawingOrder.getChildDrawingOrder(
                parent = host,
                childCount = host.childCount,
                drawingPosition = drawingPosition,
            )
        }
    }
}
