package com.viewcompose

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class AnimationBoundsDeviceTest {
    @Test
    fun combinedBounds_movesRealInteractiveAndAccessibleGeometry() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.bounds",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            lateinit var startBounds: Rect
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_animation_bounds_target,
                )
                startBounds = target.globalBounds()
                assertEquals((152f * activity.resources.displayMetrics.density).roundToInt(), target.width)
                assertEquals((48f * activity.resources.displayMetrics.density).roundToInt(), target.height)
            }

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_animation_bounds_primary_action)
            }
            SystemClock.sleep(1_100L)

            lateinit var endBounds: Rect
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewById<View>(R.id.demo_animation_bounds_target)
                endBounds = target.globalBounds()
                assertEquals((204f * activity.resources.displayMetrics.density).roundToInt(), target.width)
                assertEquals((58f * activity.resources.displayMetrics.density).roundToInt(), target.height)
                assertTrue("Expected the endpoint target to remain shown: ${target.debugHierarchy()}", target.isShown)
                assertTrue(
                    "Expected the endpoint target to retain visible bounds: ${target.debugHierarchy()}",
                    target.getGlobalVisibleRect(Rect()),
                )
                val clickableTarget = target.requireClickableDescendant()
                val accessibilityBounds = Rect()
                val accessibilityNode = clickableTarget.createAccessibilityNodeInfo()
                accessibilityNode.getBoundsInScreen(accessibilityBounds)
                assertEquals(
                    "The interactive descendant must occupy the animated endpoint",
                    endBounds,
                    clickableTarget.globalBounds(),
                )
                assertEquals(
                    "Accessibility geometry must follow the real interactive endpoint",
                    endBounds,
                    accessibilityBounds,
                )
                assertTrue("Expected the endpoint semantics to remain clickable", accessibilityNode.isClickable)
            }
            assertNotEquals("Expected the combined target to move", startBounds, endBounds)

            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val decorLocation = IntArray(2)
                decor.getLocationOnScreen(decorLocation)
                val eventTime = SystemClock.uptimeMillis()
                val x = (endBounds.centerX() - decorLocation[0]).toFloat()
                val y = (endBounds.centerY() - decorLocation[1]).toFloat()
                val down = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(eventTime, eventTime + 16L, MotionEvent.ACTION_UP, x, y, 0)
                try {
                    assertTrue("Expected the endpoint to accept ACTION_DOWN at $endBounds", activity.dispatchTouchEvent(down))
                    assertTrue("Expected the endpoint to accept ACTION_UP at $endBounds", activity.dispatchTouchEvent(up))
                } finally {
                    down.recycle()
                    up.recycle()
                }
            }
            SystemClock.sleep(250L)
            scenario.onActivity { activity ->
                val state = activity.requireScenarioViewById<TextView>(R.id.demo_animation_bounds_state)
                assertTrue(
                    "Expected the visible endpoint to accept touch, actual=${state.text}",
                    state.text.toString().endsWith("1"),
                )
            }
        }
    }
}

private fun View.requireClickableDescendant(): View = clickableDescendantOrNull()
    ?: error("Expected an interactive descendant: ${debugHierarchy()}")

private fun View.clickableDescendantOrNull(): View? {
    if (isClickable) return this
    if (this !is ViewGroup) return null
    repeat(childCount) { index ->
        getChildAt(index).clickableDescendantOrNull()?.let { return it }
    }
    return null
}

private fun View.globalBounds(): Rect {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return Rect(location[0], location[1], location[0] + width, location[1] + height)
}

private fun View.debugHierarchy(): String = generateSequence(this) { current ->
    current.parent as? View
}.joinToString(" <- ") { current ->
    "${current.javaClass.simpleName}(bounds=${current.globalBounds()}, " +
        "local=[${current.left},${current.top},${current.right},${current.bottom}], " +
        "measured=${current.measuredWidth}x${current.measuredHeight}, shown=${current.isShown})"
}
