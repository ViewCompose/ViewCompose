package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.viewcompose.ui.state.ScrollState
import kotlin.math.abs

/**
 * NestedScrollView host used by ScrollableColumn.
 */
internal class DeclarativeScrollableColumnLayout(
    context: Context,
) : NestedScrollView(context), ChildHostViewGroup {
    internal val innerLayout = DeclarativeLinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    override val childHost: ViewGroup
        get() = innerLayout
    private var userScrollEnabled = true
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchDownY = 0f
    private var lastTouchY = 0f
    private var verticalGestureResolved = false
    private val scrollStateController by lazy(LazyThreadSafetyMode.NONE) {
        EagerScrollStateController(
            host = this,
            currentLogicalValue = { scrollY },
            currentMaxValue = {
                (innerLayout.measuredHeight - (height - paddingTop - paddingBottom)).coerceAtLeast(0)
            },
            currentViewportSize = { (height - paddingTop - paddingBottom).coerceAtLeast(0) },
            performScroll = { value, animated ->
                if (animated) smoothScrollTo(0, value) else scrollTo(0, value)
            },
        )
    }

    init {
        super.addView(
            innerLayout,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        isFillViewport = true
    }

    fun bindScrollState(state: ScrollState?, userScrollEnabled: Boolean) {
        scrollStateController.bind(state, userScrollEnabled) { enabled ->
            this.userScrollEnabled = enabled
            if (!enabled) parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (userScrollEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownY = event.y
                    lastTouchY = event.y
                    verticalGestureResolved = false
                    // RecyclerView is not a nested-scrolling parent. Reserve the stream until the
                    // gesture direction is known, then hand it back at the matching scroll edge.
                    parent?.requestDisallowInterceptTouchEvent(
                        canScrollVertically(-1) || canScrollVertically(1),
                    )
                    scrollStateController.onTouchStarted()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!verticalGestureResolved && abs(touchDownY - event.y) > touchSlop) {
                        verticalGestureResolved = true
                    }
                    if (verticalGestureResolved) {
                        val scrollDelta = lastTouchY - event.y
                        if (scrollDelta != 0f) {
                            val direction = if (scrollDelta > 0f) 1 else -1
                            parent?.requestDisallowInterceptTouchEvent(canScrollVertically(direction))
                        }
                    }
                    lastTouchY = event.y
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    verticalGestureResolved = false
                    scrollStateController.onTouchEnded()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t != oldt) scrollStateController.onScrollPositionChanged()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        scrollStateController.onLayoutChanged()
    }

    fun dispose() {
        scrollStateController.dispose()
    }
}
