package com.viewcompose.renderer.view.container

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LayoutConstraintHostTest {
    @Test
    fun `maximum width caps intrinsic child measurement`() {
        val host = hostWithChild(desiredWidth = 300, desiredHeight = 40)
        host.bind(100, null, null, false, false, false)

        host.measure(atMost(400), atMost(400))

        assertEquals(100, host.measuredWidth)
        assertEquals(40, host.measuredHeight)
    }

    @Test
    fun `bounded fill width drives aspect ratio`() {
        val host = hostWithChild(desiredWidth = 20, desiredHeight = 20)
        host.bind(200, null, 2f, false, true, false)

        host.measure(atMost(400), atMost(400))

        assertEquals(200, host.measuredWidth)
        assertEquals(100, host.measuredHeight)
        assertEquals(200, host.getChildAt(0).measuredWidth)
        assertEquals(100, host.getChildAt(0).measuredHeight)
    }

    @Test
    fun `height-first aspect ratio uses bounded fill height`() {
        val host = hostWithChild(desiredWidth = 20, desiredHeight = 20)
        host.bind(null, 90, 2f, true, false, true)

        host.measure(atMost(300), atMost(300))

        assertEquals(180, host.measuredWidth)
        assertEquals(90, host.measuredHeight)
    }

    @Test
    fun `parent bound wins over declared maximum`() {
        val host = hostWithChild(desiredWidth = 300, desiredHeight = 40)
        host.bind(200, null, null, false, true, false)

        host.measure(atMost(80), atMost(400))

        assertEquals(80, host.measuredWidth)
    }

    @Test
    fun `exact parent constraint wins when declared maximum is smaller`() {
        val host = hostWithChild(desiredWidth = 40, desiredHeight = 40)
        host.bind(100, null, null, false, false, false)

        host.measure(exactly(200), atMost(400))

        assertEquals(200, host.measuredWidth)
        assertEquals(200, host.getChildAt(0).measuredWidth)
    }

    @Test
    fun `declared maximum constrains an unspecified parent`() {
        val host = hostWithChild(desiredWidth = 300, desiredHeight = 40)
        host.bind(100, null, null, false, false, false)

        host.measure(unspecified(), unspecified())

        assertEquals(100, host.measuredWidth)
        assertEquals(40, host.measuredHeight)
    }

    @Test
    fun `incompatible exact axes win over aspect ratio`() {
        val host = hostWithChild(desiredWidth = 20, desiredHeight = 20)
        host.bind(null, null, 1f, false, false, false)

        host.measure(exactly(200), exactly(100))

        assertEquals(200, host.measuredWidth)
        assertEquals(100, host.measuredHeight)
    }

    private fun hostWithChild(desiredWidth: Int, desiredHeight: Int): DeclarativeLayoutConstraintHost {
        val context = RuntimeEnvironment.getApplication()
        return DeclarativeLayoutConstraintHost(context).apply {
            addView(
                object : View(context) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        setMeasuredDimension(
                            resolveSize(desiredWidth, widthMeasureSpec),
                            resolveSize(desiredHeight, heightMeasureSpec),
                        )
                    }
                },
            )
        }
    }

    private fun atMost(size: Int): Int =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private fun exactly(size: Int): Int =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun unspecified(): Int =
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
}
