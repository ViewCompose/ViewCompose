package com.viewcompose.renderer.view.container

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnimatedBoundsLayoutTest {
    @Test
    fun `combined target uses real intermediate layout without per-frame measurement`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeTweenSpecModel(
            durationMillis = 240,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        fixture.measure()
        fixture.host.layout(10, 20, 90, 80)

        fixture.child.desiredWidth = 160
        fixture.child.desiredHeight = 100
        fixture.measure()
        val measurementsAtTarget = fixture.child.measureCount
        fixture.host.layout(110, 140, 270, 240)

        assertEquals(Rect(10, 20, 90, 80), fixture.host.currentBoundsForTest())
        assertEquals(Rect(110, 140, 270, 240), fixture.host.targetBoundsForTest())
        fixture.host.animatorForTest()!!.currentPlayTime = 120L

        val midpoint = fixture.host.currentBoundsForTest()
        assertTrue(midpoint.left in 59..61)
        assertTrue(midpoint.top in 79..81)
        assertTrue(midpoint.width() in 119..121)
        assertTrue(midpoint.height() in 79..81)
        assertEquals(midpoint.width(), fixture.child.width)
        assertEquals(midpoint.height(), fixture.child.height)
        assertEquals(measurementsAtTarget, fixture.child.measureCount)
        val hitRect = Rect()
        fixture.host.getHitRect(hitRect)
        assertEquals(midpoint, hitRect)

        fixture.host.animatorForTest()!!.end()
        assertEquals(Rect(110, 140, 270, 240), fixture.host.currentBoundsForTest())
        assertEquals(measurementsAtTarget, fixture.child.measureCount)
        assertNull(fixture.host.animatorForTest())
    }

    @Test
    fun `position-only target and snap both commit physical bounds`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.measure()
        fixture.host.layout(0, 0, 80, 60)
        fixture.host.animationSpec = ContentSizeTweenSpecModel(
            durationMillis = 200,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )

        fixture.host.layout(100, 40, 180, 100)
        fixture.host.animatorForTest()!!.currentPlayTime = 100L
        assertEquals(Rect(50, 20, 130, 80), fixture.host.currentBoundsForTest())

        fixture.host.animationSpec = com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
        fixture.host.layout(20, 80, 140, 170)
        assertEquals(Rect(20, 80, 140, 170), fixture.host.currentBoundsForTest())
        assertEquals(120, fixture.child.width)
        assertEquals(90, fixture.child.height)
    }

    @Test
    fun `physical retarget retains four-edge velocity and settles at latest target`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeSpringSpecModel(
            dampingRatio = 0.8f,
            stiffness = 180f,
            maxDurationMillis = 2_000,
        )
        fixture.measure()
        fixture.host.layout(0, 0, 80, 60)
        fixture.host.layout(100, 80, 260, 180)
        fixture.host.animatorForTest()!!.currentPlayTime = 80L
        val retainedVelocity = fixture.host.velocityForTest()
        assertNotEquals(0f, retainedVelocity.sumOf { kotlin.math.abs(it.toDouble()) }.toFloat())

        fixture.host.layout(40, 120, 180, 220)

        assertArrayEquals(retainedVelocity, fixture.host.velocityForTest(), 0f)
        fixture.host.animatorForTest()!!.end()
        assertEquals(Rect(40, 120, 180, 220), fixture.host.currentBoundsForTest())
        assertNull(fixture.host.animatorForTest())
    }

    @Test
    fun `duration retarget starts from sampled rectangle and resets physical velocity`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeTweenSpecModel(
            durationMillis = 200,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        fixture.measure()
        fixture.host.layout(0, 0, 80, 60)
        fixture.host.layout(100, 40, 180, 100)
        fixture.host.animatorForTest()!!.currentPlayTime = 80L
        assertEquals(Rect(40, 16, 120, 76), fixture.host.currentBoundsForTest())

        fixture.host.layout(200, 120, 360, 220)
        fixture.host.animatorForTest()!!.currentPlayTime = 100L

        assertEquals(Rect(120, 68, 240, 148), fixture.host.currentBoundsForTest())
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), fixture.host.velocityForTest(), 0f)
    }

    @Test
    fun `parent relayout at the accepted target does not restart active motion`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeTweenSpecModel(
            durationMillis = 200,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        fixture.measure()
        fixture.host.layout(0, 0, 80, 60)
        fixture.host.layout(100, 40, 180, 100)
        val animator = fixture.host.animatorForTest()!!
        animator.currentPlayTime = 80L
        val sampled = fixture.host.currentBoundsForTest()

        fixture.host.layout(100, 40, 180, 100)

        assertSame(animator, fixture.host.animatorForTest())
        assertEquals(sampled, fixture.host.currentBoundsForTest())
    }

    @Test
    fun `detach cancels ownership and next layout settles without replay`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeTweenSpecModel(
            durationMillis = 200,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.addView(fixture.host)
        fixture.measure()
        fixture.host.layout(0, 0, 80, 60)
        fixture.host.layout(100, 40, 180, 100)
        assertTrue(fixture.host.animatorForTest() != null)

        root.removeView(fixture.host)

        assertNull(fixture.host.animatorForTest())
        assertNull(fixture.host.targetBoundsForTest())
        fixture.measure()
        fixture.host.layout(20, 30, 140, 120)
        assertEquals(Rect(20, 30, 140, 120), fixture.host.currentBoundsForTest())
        assertNull(fixture.host.animatorForTest())
    }

    @Test
    fun `focused content retains focus and clipping through physical motion`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeTweenSpecModel(
            durationMillis = 200,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        fixture.child.isFocusableInTouchMode = true
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.addView(fixture.host)
        fixture.measure()
        fixture.host.layout(0, 0, 80, 60)
        assertTrue(fixture.child.requestFocus())

        fixture.host.layout(100, 40, 220, 130)
        fixture.host.animatorForTest()!!.currentPlayTime = 100L

        assertTrue(fixture.child.hasFocus())
        assertTrue(fixture.host.clipChildren)
        assertTrue(fixture.host.clipToPadding)
        assertEquals(fixture.host.width, fixture.child.width)
        assertEquals(fixture.host.height, fixture.child.height)
    }

    private fun fixture(width: Int, height: Int): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val child = MutableSizeView(width, height)
        val host = DeclarativeAnimatedBoundsHostLayout(context)
        host.addView(child)
        return Fixture(host, child)
    }

    private class MutableSizeView(
        var desiredWidth: Int,
        var desiredHeight: Int,
    ) : View(RuntimeEnvironment.getApplication()) {
        var measureCount: Int = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            measureCount += 1
            setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec),
            )
        }
    }

    private data class Fixture(
        val host: DeclarativeAnimatedBoundsHostLayout,
        val child: MutableSizeView,
    ) {
        fun measure() {
            val atMost = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.AT_MOST)
            host.measure(atMost, atMost)
        }
    }
}
