package com.viewcompose.renderer.view.container

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.viewcompose.ui.state.ScrollState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class EagerScrollStateControllerTest {
    @Test
    fun `pending target publishes after layout and state replacement detaches old owner`() {
        val host = View(RuntimeEnvironment.getApplication())
        var logicalValue = 0
        val commands = mutableListOf<Pair<Int, Boolean>>()
        val enabledValues = mutableListOf<Boolean>()
        val controller = EagerScrollStateController(
            host = host,
            currentLogicalValue = { logicalValue },
            currentMaxValue = { 120 },
            currentViewportSize = { 40 },
            performScroll = { value, animated ->
                logicalValue = value
                commands += value to animated
            },
        )
        val first = ScrollState(initialValue = 40)

        controller.bind(first, userScrollEnabled = false, enabledValues::add)
        assertEquals(emptyList<Pair<Int, Boolean>>(), commands)

        host.layout(0, 0, 40, 40)
        controller.onLayoutChanged()

        assertEquals(listOf(40 to false), commands)
        assertEquals(40, first.value)
        assertEquals(120, first.maxValue)
        assertEquals(40, first.viewportSize)
        assertTrue(first.canScrollBackward)
        assertTrue(first.canScrollForward)
        assertEquals(listOf(false), enabledValues)

        first.scrollTo(90)
        assertEquals(90 to false, commands.last())
        assertEquals(90, first.value)

        val second = ScrollState()
        controller.bind(second, userScrollEnabled = true, enabledValues::add)
        val commandsAfterReplacement = commands.size
        first.scrollTo(10)

        assertEquals(commandsAfterReplacement, commands.size)
        assertEquals(listOf(false, true), enabledValues)

        controller.dispose()
        val commandsAfterDispose = commands.size
        second.scrollTo(30)
        assertEquals(commandsAfterDispose, commands.size)
    }

    @Test
    fun `scrollable column keeps native position and portable snapshot in sync`() {
        val context = RuntimeEnvironment.getApplication()
        val view = DeclarativeScrollableColumnLayout(context)
        view.childHost.addView(
            View(context),
            LinearLayout.LayoutParams(100, 300),
        )
        val state = ScrollState()
        val snapshots = mutableListOf<com.viewcompose.ui.state.ScrollStateSnapshot>()
        state.addOnSnapshotChangedListener(snapshots::add)
        view.bindScrollState(state, userScrollEnabled = true)

        measureAndLayout(view, width = 100, height = 100)
        state.scrollTo(60)

        assertEquals(60, view.scrollY)
        assertEquals(60, state.value)
        assertEquals(200, state.maxValue)
        assertEquals(100, state.viewportSize)
        assertTrue("${state.snapshot}; history=$snapshots", state.lastScrolledForward)
        assertFalse(state.lastScrolledBackward)
    }

    @Test
    fun `scrollable column owns a same-axis nested gesture until it reaches the scroll edge`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = RecordingInterceptLayout(context)
        val view = DeclarativeScrollableColumnLayout(context)
        view.childHost.addView(
            View(context),
            LinearLayout.LayoutParams(100, 300),
        )
        parent.addView(view, FrameLayout.LayoutParams(100, 100))
        view.bindScrollState(state = null, userScrollEnabled = true)
        measureAndLayout(parent, width = 100, height = 100)

        val downTime = SystemClock.uptimeMillis()
        dispatchTouch(view, downTime, MotionEvent.ACTION_DOWN, y = 75f)
        assertEquals(true, parent.interceptionRequests.last())

        dispatchTouch(view, downTime, MotionEvent.ACTION_MOVE, y = 25f, eventOffset = 16L)
        assertEquals(true, parent.interceptionRequests.last())

        view.scrollTo(0, 200)
        dispatchTouch(view, downTime, MotionEvent.ACTION_MOVE, y = 0f, eventOffset = 32L)
        assertEquals(false, parent.interceptionRequests.last())

        dispatchTouch(view, downTime, MotionEvent.ACTION_CANCEL, y = 0f, eventOffset = 48L)
        assertEquals(false, parent.interceptionRequests.last())
    }

    @Test
    @Config(qualifiers = "ldrtl")
    fun `scrollable row exposes logical start offsets in rtl`() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = DeclarativeScrollableRowLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        view.innerLayout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        view.childHost.addView(
            View(context),
            LinearLayout.LayoutParams(300, 100),
        )
        val state = ScrollState()
        view.bindScrollState(state, userScrollEnabled = true)

        measureAndLayout(view, width = 100, height = 100)
        state.scrollTo(50)

        assertEquals(View.LAYOUT_DIRECTION_RTL, view.layoutDirection)
        assertEquals(150, view.scrollX)
        assertEquals(50, state.value)
        assertEquals(200, state.maxValue)
        assertTrue(state.canScrollBackward)
        assertTrue(state.canScrollForward)
    }

    private fun measureAndLayout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun dispatchTouch(
        view: View,
        downTime: Long,
        action: Int,
        y: Float,
        eventOffset: Long = 0L,
    ) {
        MotionEvent.obtain(
            downTime,
            downTime + eventOffset,
            action,
            50f,
            y,
            0,
        ).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private class RecordingInterceptLayout(
        context: android.content.Context,
    ) : FrameLayout(context) {
        val interceptionRequests = mutableListOf<Boolean>()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            interceptionRequests += disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }
}
