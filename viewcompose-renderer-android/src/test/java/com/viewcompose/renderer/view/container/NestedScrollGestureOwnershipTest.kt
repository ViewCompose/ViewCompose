package com.viewcompose.renderer.view.container

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NestedScrollGestureOwnershipTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `vertical child releases a cross-axis gesture to its parent`() {
        val parent = RecordingInterceptLayout(context)
        val child = ScrollCapabilityView(context, backward = true, forward = true)
        parent.addView(child)
        val arbitrator = ParentInterceptGestureArbitrator(child) {
            ParentInterceptGestureArbitrator.Axis.Vertical
        }
        val downTime = SystemClock.uptimeMillis()

        dispatch(arbitrator, downTime, MotionEvent.ACTION_DOWN, x = 50f, y = 50f)
        assertTrue(parent.interceptionRequests.last())

        dispatch(
            arbitrator,
            downTime,
            MotionEvent.ACTION_MOVE,
            x = 5f,
            y = 48f,
            eventOffset = 16L,
        )
        assertFalse(parent.interceptionRequests.last())
    }

    @Test
    fun `vertical child releases a same-axis gesture at its matching edge`() {
        val parent = RecordingInterceptLayout(context)
        val child = ScrollCapabilityView(context, backward = false, forward = true)
        parent.addView(child)
        val arbitrator = ParentInterceptGestureArbitrator(child) {
            ParentInterceptGestureArbitrator.Axis.Vertical
        }
        val downTime = SystemClock.uptimeMillis()

        dispatch(arbitrator, downTime, MotionEvent.ACTION_DOWN, x = 50f, y = 50f)
        assertTrue(parent.interceptionRequests.last())

        dispatch(
            arbitrator,
            downTime,
            MotionEvent.ACTION_MOVE,
            x = 50f,
            y = 90f,
            eventOffset = 16L,
        )
        assertFalse(parent.interceptionRequests.last())
    }

    @Test
    fun `child at the top yields initial ownership to active pull refresh`() {
        val root = RecordingInterceptLayout(context)
        val refresh = DeclarativePullToRefreshLayout(context)
        val child = ScrollCapabilityView(context, backward = false, forward = true)
        root.addView(refresh)
        refresh.addView(child)
        val arbitrator = ParentInterceptGestureArbitrator(child) {
            ParentInterceptGestureArbitrator.Axis.Vertical
        }

        dispatch(
            arbitrator,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN,
            x = 50f,
            y = 50f,
        )

        assertFalse(root.interceptionRequests.contains(true))
    }

    @Test
    fun `pull refresh consults the actual scrollable below decoration wrappers`() {
        val refresh = DeclarativePullToRefreshLayout(context)
        val wrapper = FrameLayout(context)
        val scrollable = MutableScrollCapabilityView(context, backward = false, forward = true)
        wrapper.addView(
            scrollable,
            FrameLayout.LayoutParams(100, 100),
        )
        refresh.addView(wrapper)
        measureAndLayout(refresh, width = 100, height = 100)

        assertFalse(refresh.canChildScrollUp())

        scrollable.backward = true

        assertTrue(refresh.canChildScrollUp())
    }

    @Test
    fun `lazy list reserves a same-axis stream while it can scroll`() {
        val parent = RecordingInterceptLayout(context)
        val list = DeclarativeLazyListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = FixedHeightAdapter(count = 4, itemHeight = 80)
        }
        parent.addView(list, FrameLayout.LayoutParams(100, 100))
        measureAndLayout(parent, width = 100, height = 100)
        assertTrue(list.canScrollVertically(1))

        val downTime = SystemClock.uptimeMillis()
        MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 50f, 50f, 0).also {
            list.dispatchTouchEvent(it)
            it.recycle()
        }

        assertTrue(parent.interceptionRequests.contains(true))
    }

    private fun dispatch(
        arbitrator: ParentInterceptGestureArbitrator,
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
        eventOffset: Long = 0L,
    ) {
        MotionEvent.obtain(downTime, downTime + eventOffset, action, x, y, 0).also {
            arbitrator.onDispatchTouchEvent(it, enabled = true)
            it.recycle()
        }
    }

    private fun measureAndLayout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private class ScrollCapabilityView(
        context: android.content.Context,
        private val backward: Boolean,
        private val forward: Boolean,
    ) : View(context) {
        override fun canScrollVertically(direction: Int): Boolean =
            if (direction < 0) backward else forward
    }

    private class MutableScrollCapabilityView(
        context: android.content.Context,
        var backward: Boolean,
        private val forward: Boolean,
    ) : View(context) {
        override fun canScrollVertically(direction: Int): Boolean =
            if (direction < 0) backward else forward
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

    private class FixedHeightAdapter(
        private val count: Int,
        private val itemHeight: Int,
    ) : RecyclerView.Adapter<FixedHeightHolder>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FixedHeightHolder {
            return FixedHeightHolder(
                View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        itemHeight,
                    )
                },
            )
        }

        override fun onBindViewHolder(holder: FixedHeightHolder, position: Int) = Unit

        override fun getItemCount(): Int = count
    }

    private class FixedHeightHolder(view: View) : RecyclerView.ViewHolder(view)
}
