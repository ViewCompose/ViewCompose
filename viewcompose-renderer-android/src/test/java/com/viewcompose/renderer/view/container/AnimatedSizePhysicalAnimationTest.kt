package com.viewcompose.renderer.view.container

/*
 * Test responsibility: verifies that the Android animated-size host consumes the shared physical
 * solver, retains sampled velocity across retargeting, and never retains invalid negative geometry.
 */

import android.animation.ValueAnimator
import android.view.View
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnimatedSizePhysicalAnimationTest {
    @Test
    fun `physical retarget retains sampled size velocity and settles at latest target`() {
        val fixture = fixture(width = 80, height = 60)
        fixture.host.animationSpec = ContentSizeSpringSpecModel(
            dampingRatio = 0.8f,
            stiffness = 180f,
            maxDurationMillis = 2_000,
        )
        fixture.measure()

        fixture.child.desiredWidth = 240
        fixture.child.desiredHeight = 160
        fixture.measure()
        fixture.host.readSizeAnimator().currentPlayTime = 80L
        fixture.measure()
        val retainedVelocity = fixture.host.readAnimatedVelocity()
        assertNotEquals(0f to 0f, retainedVelocity)

        fixture.child.desiredWidth = 140
        fixture.child.desiredHeight = 100
        fixture.measure()

        assertEquals(retainedVelocity, fixture.host.readAnimatedVelocity())
        fixture.host.readSizeAnimator().end()
        fixture.measure()
        assertEquals(140, fixture.host.measuredWidth)
        assertEquals(100, fixture.host.measuredHeight)
    }

    @Test
    fun `under damped collapse terminates at valid target instead of retaining negative size`() {
        val fixture = fixture(width = 240, height = 160)
        fixture.host.animationSpec = ContentSizeSpringSpecModel(
            dampingRatio = 0.1f,
            stiffness = 200f,
            maxDurationMillis = 2_000,
        )
        fixture.measure()

        fixture.child.desiredWidth = 0
        fixture.child.desiredHeight = 0
        fixture.measure()
        fixture.host.readSizeAnimator().currentPlayTime = 250L
        fixture.measure()

        assertEquals(0, fixture.host.measuredWidth)
        assertEquals(0, fixture.host.measuredHeight)
        assertEquals(0f to 0f, fixture.host.readAnimatedVelocity())
    }

    private fun fixture(width: Int, height: Int): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val child = MutableSizeView(width, height)
        val host = DeclarativeAnimatedSizeHostLayout(context)
        host.addView(child)
        return Fixture(host = host, child = child)
    }

    private fun DeclarativeAnimatedSizeHostLayout.readAnimatedVelocity(): Pair<Float, Float> {
        val value = javaClass.getDeclaredField("animatedVelocity").run {
            isAccessible = true
            get(this@readAnimatedVelocity)
        }
        val type = value.javaClass
        val width = type.getDeclaredField("width").run {
            isAccessible = true
            getFloat(value)
        }
        val height = type.getDeclaredField("height").run {
            isAccessible = true
            getFloat(value)
        }
        return width to height
    }

    private fun DeclarativeAnimatedSizeHostLayout.readSizeAnimator(): ValueAnimator {
        return javaClass.getDeclaredField("sizeAnimator").run {
            isAccessible = true
            get(this@readSizeAnimator) as ValueAnimator
        }
    }

    private class MutableSizeView(
        var desiredWidth: Int,
        var desiredHeight: Int,
    ) : View(RuntimeEnvironment.getApplication()) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec),
            )
        }
    }

    private data class Fixture(
        val host: DeclarativeAnimatedSizeHostLayout,
        val child: MutableSizeView,
    ) {
        fun measure() {
            val atMost = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.AT_MOST)
            host.measure(atMost, atMost)
        }
    }
}
