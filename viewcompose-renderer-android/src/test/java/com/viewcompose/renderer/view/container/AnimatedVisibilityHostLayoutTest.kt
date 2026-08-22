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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnimatedVisibilityHostLayoutTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `reveal size participates in layout while aligned child retains full measurement`() {
        val host = DeclarativeAnimatedVisibilityHostLayout(context).apply {
            widthScale = 0.5f
            heightScale = 0.5f
            contentGravity = Gravity.BOTTOM or Gravity.END
        }
        val child = fixedView(width = 100, height = 80)
        host.addView(child)

        host.measure(atMost(400), atMost(400))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)

        assertEquals(50, host.measuredWidth)
        assertEquals(40, host.measuredHeight)
        assertEquals(100, child.measuredWidth)
        assertEquals(80, child.measuredHeight)
        assertEquals(-50, child.left)
        assertEquals(-40, child.top)
    }

    @Test
    fun `fractional translation and scale origin use full host dimensions`() {
        val host = DeclarativeAnimatedVisibilityHostLayout(context).apply {
            widthScale = 0.5f
            heightScale = 0.5f
            visualScaleX = 0.8f
            visualScaleY = 0.7f
            translationXFraction = -0.5f
            translationYFraction = 0.25f
            pivotFractionX = 0.2f
            pivotFractionY = 0.8f
        }
        host.addView(fixedView(width = 120, height = 80))

        host.measure(atMost(400), atMost(400))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)

        assertEquals(-60f, host.translationX, 0f)
        assertEquals(20f, host.translationY, 0f)
        assertEquals(24f, host.pivotX, 0f)
        assertEquals(64f, host.pivotY, 0f)
        assertEquals(0.8f, host.scaleX, 0f)
        assertEquals(0.7f, host.scaleY, 0f)
    }

    @Test
    fun `transform geometry includes every direct child instead of trusting the first child`() {
        val host = DeclarativeAnimatedVisibilityHostLayout(context).apply {
            widthScale = 0.5f
            heightScale = 0.5f
            translationXFraction = -0.5f
            translationYFraction = 0.25f
            pivotFractionX = 1f
            pivotFractionY = 1f
        }
        host.addView(fixedView(width = 60, height = 30))
        host.addView(fixedView(width = 140, height = 90))

        host.measure(atMost(400), atMost(400))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)

        assertEquals(70, host.measuredWidth)
        assertEquals(45, host.measuredHeight)
        assertEquals(-70f, host.translationX, 0f)
        assertEquals(22.5f, host.translationY, 0f)
        assertEquals(140f, host.pivotX, 0f)
        assertEquals(90f, host.pivotY, 0f)
    }

    @Test
    fun `inactive visibility content is excluded from input focus and accessibility`() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val host = DeclarativeAnimatedVisibilityHostLayout(activity)
        val child = RecordingView(activity).apply { isFocusable = true }
        host.addView(child)
        root.addView(host, FrameLayout.LayoutParams(100, 60))
        host.measure(exactly(100), exactly(60))
        host.layout(0, 0, 100, 60)
        assertTrue(child.requestFocus())

        host.contentActive = false
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val key = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)

        assertFalse(host.dispatchTouchEvent(down))
        assertFalse(host.dispatchKeyEvent(key))
        val focusables = arrayListOf<View>()
        host.addFocusables(focusables, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        assertTrue(focusables.isEmpty())
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, host.descendantFocusability)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, host.importantForAccessibility)
        assertEquals(0, child.touchCount)
        down.recycle()
        activityController.pause().destroy()
    }

    @Test
    fun `clipping and active participation restore without replacing the host`() {
        val host = DeclarativeAnimatedVisibilityHostLayout(context)

        host.clipToBounds = false
        host.contentActive = false
        assertFalse(host.clipChildren)
        assertFalse(host.clipToPadding)

        host.clipToBounds = true
        host.contentActive = true
        assertTrue(host.clipChildren)
        assertTrue(host.clipToPadding)
        assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, host.descendantFocusability)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, host.importantForAccessibility)
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
