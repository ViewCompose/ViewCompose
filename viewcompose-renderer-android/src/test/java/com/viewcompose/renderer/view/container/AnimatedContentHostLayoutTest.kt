package com.viewcompose.renderer.view.container

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric

@RunWith(RobolectricTestRunner::class)
class AnimatedContentHostLayoutTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `size transform preserves committed size across segment retargeting`() {
        val host = DeclarativeAnimatedContentHostLayout(context)
        val first = fixedView(width = 100, height = 40)
        host.addView(first)
        host.measure(atMost(400), atMost(400))
        assertEquals(100, host.measuredWidth)
        assertEquals(40, host.measuredHeight)

        val second = fixedView(width = 200, height = 80)
        host.addView(second)
        host.sizeTransformEnabled = true
        host.segmentId = 1L
        host.sizeProgress = 0f
        host.measure(atMost(400), atMost(400))
        assertEquals(100, host.measuredWidth)
        assertEquals(40, host.measuredHeight)

        host.sizeProgress = 0.5f
        host.measure(atMost(400), atMost(400))
        assertEquals(150, host.measuredWidth)
        assertEquals(60, host.measuredHeight)

        host.removeView(first)
        host.addView(fixedView(width = 50, height = 20))
        host.segmentId = 2L
        host.sizeProgress = 0f
        host.measure(atMost(400), atMost(400))
        assertEquals(150, host.measuredWidth)
        assertEquals(60, host.measuredHeight)
    }

    @Test
    fun `content alignment and clipping update existing children`() {
        val host = DeclarativeAnimatedContentHostLayout(context)
        val child = fixedView(width = 40, height = 20)
        host.addView(child)

        host.contentGravity = Gravity.BOTTOM or Gravity.END
        host.clipToBounds = true
        host.measure(exactly(100), exactly(80))
        host.layout(0, 0, 100, 80)

        assertEquals(60, child.left)
        assertEquals(60, child.top)
        assertTrue(host.clipChildren)
        assertTrue(host.clipToPadding)
    }

    @Test
    fun `size transform preserves exact parent constraints`() {
        val host = DeclarativeAnimatedContentHostLayout(context)
        host.addView(fixedView(width = 40, height = 20))
        host.measure(exactly(100), exactly(80))

        host.addView(fixedView(width = 60, height = 30))
        host.sizeTransformEnabled = true
        host.segmentId = 1L
        host.sizeProgress = 0.5f
        host.measure(exactly(100), exactly(80))

        assertEquals(100, host.measuredWidth)
        assertEquals(80, host.measuredHeight)
    }

    @Test
    fun `inactive item is renderable but excluded from input focus and accessibility`() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val item = DeclarativeAnimatedContentItemLayout(activity)
        val child = RecordingView(activity).apply {
            isFocusable = true
        }
        item.addView(child)
        root.addView(item, FrameLayout.LayoutParams(100, 60))
        item.measure(exactly(100), exactly(60))
        item.layout(0, 0, 100, 60)
        assertTrue(child.requestFocus())

        item.contentActive = false
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val key = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)

        assertFalse(item.dispatchTouchEvent(down))
        assertFalse(item.dispatchKeyEvent(key))
        val focusables = arrayListOf<View>()
        item.addFocusables(focusables, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        assertTrue(focusables.isEmpty())
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, item.descendantFocusability)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, item.importantForAccessibility)
        assertEquals(0, child.touchCount)
        down.recycle()
        activityController.pause().destroy()
    }

    @Test
    fun `fractional translation and transform origin follow measured item size`() {
        val item = DeclarativeAnimatedContentItemLayout(context).apply {
            translationXFraction = -0.5f
            translationYFraction = 0.25f
            pivotFractionX = 0.2f
            pivotFractionY = 0.8f
        }

        item.measure(exactly(120), exactly(80))
        item.layout(0, 0, 120, 80)

        assertEquals(-60f, item.translationX, 0f)
        assertEquals(20f, item.translationY, 0f)
        assertEquals(24f, item.pivotX, 0f)
        assertEquals(64f, item.pivotY, 0f)
    }

    private fun fixedView(width: Int, height: Int): View {
        return View(context).apply {
            layoutParams = FrameLayout.LayoutParams(width, height)
        }
    }

    private fun atMost(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private class RecordingView(context: Context) : View(context) {
        var touchCount: Int = 0

        override fun onTouchEvent(event: MotionEvent): Boolean {
            touchCount += 1
            return true
        }
    }
}
