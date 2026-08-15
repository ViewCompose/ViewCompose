package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import com.viewcompose.ui.state.ScrollState
import kotlin.math.roundToInt

/**
 * HorizontalScrollView host used by ScrollableRow.
 *
 * Adds horizontal nested-scrolling-child behavior so parent nested-scroll hosts can cooperatively consume scroll deltas.
 */
internal class DeclarativeScrollableRowLayout(
    context: Context,
) : HorizontalScrollView(context), ChildHostViewGroup {
    internal val innerLayout = DeclarativeLinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    override val childHost: ViewGroup
        get() = innerLayout
    private val nestedChildHelper = NestedScrollingChildHelper(this).apply {
        isNestedScrollingEnabled = true
    }
    private val maximumFlingVelocity =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private val minimumFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private var velocityTracker: VelocityTracker? = null
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var lastMotionX: Float = 0f
    private var accumulatedNestedOffsetX: Float = 0f
    private var userScrollEnabled = true
    private val scrollStateController by lazy(LazyThreadSafetyMode.NONE) {
        EagerScrollStateController(
            host = this,
            currentLogicalValue = {
                if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                    (horizontalScrollRange() - scrollX).coerceAtLeast(0)
                } else {
                    scrollX
                }
            },
            currentMaxValue = ::horizontalScrollRange,
            currentViewportSize = { (width - paddingLeft - paddingRight).coerceAtLeast(0) },
            performScroll = { value, animated ->
                val physicalValue = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                    horizontalScrollRange() - value
                } else {
                    value
                }
                if (animated) smoothScrollTo(physicalValue, 0) else scrollTo(physicalValue, 0)
            },
        )
    }

    init {
        super.addView(
            innerLayout,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
        isFillViewport = true
    }

    fun bindScrollState(state: ScrollState?, userScrollEnabled: Boolean) {
        scrollStateController.bind(state, userScrollEnabled) { enabled ->
            this.userScrollEnabled = enabled
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!userScrollEnabled) return super.dispatchTouchEvent(event)
        val transformed = MotionEvent.obtain(event)
        var stopNestedScroll = false
        var cancelNativeFling = false
        var scrollDeltaX = 0
        var preConsumedX = 0
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollStateController.onTouchStarted()
                activePointerId = event.getPointerId(0)
                lastMotionX = event.x
                accumulatedNestedOffsetX = 0f
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                nestedChildHelper.startNestedScroll(
                    ViewCompat.SCROLL_AXIS_HORIZONTAL,
                    ViewCompat.TYPE_TOUCH,
                )
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                lastMotionX = event.getX(event.actionIndex)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    val replacementIndex = if (event.actionIndex == 0) 1 else 0
                    if (replacementIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(replacementIndex)
                        lastMotionX = event.getX(replacementIndex)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    val currentX = event.getX(pointerIndex)
                    scrollDeltaX = (lastMotionX - currentX).roundToInt()
                    lastMotionX = currentX
                    val parentConsumed = IntArray(2)
                    nestedChildHelper.dispatchNestedPreScroll(
                        scrollDeltaX,
                        0,
                        parentConsumed,
                        null,
                        ViewCompat.TYPE_TOUCH,
                    )
                    preConsumedX = parentConsumed[0]
                    accumulatedNestedOffsetX += preConsumedX
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.apply {
                    addMovement(transformed)
                    computeCurrentVelocity(1000, maximumFlingVelocity)
                    val scrollVelocityX = -xVelocity
                    if (kotlin.math.abs(scrollVelocityX) >= minimumFlingVelocity) {
                        cancelNativeFling = nestedChildHelper.dispatchNestedPreFling(
                            scrollVelocityX,
                            0f,
                        )
                        if (!cancelNativeFling) {
                            nestedChildHelper.dispatchNestedFling(
                                scrollVelocityX,
                                0f,
                                canScrollHorizontally(if (scrollVelocityX > 0f) 1 else -1),
                            )
                        }
                    }
                }
                stopNestedScroll = true
                scrollStateController.onTouchEnded()
            }

            MotionEvent.ACTION_CANCEL -> {
                stopNestedScroll = true
                scrollStateController.onTouchEnded()
            }
        }

        if (event.actionMasked != MotionEvent.ACTION_DOWN && accumulatedNestedOffsetX != 0f) {
            transformed.offsetLocation(accumulatedNestedOffsetX, 0f)
        }
        if (event.actionMasked != MotionEvent.ACTION_UP) {
            velocityTracker?.addMovement(transformed)
        }
        val beforeScrollX = scrollX
        val handled = if (cancelNativeFling) {
            transformed.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(transformed)
        } else {
            super.dispatchTouchEvent(transformed)
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && scrollDeltaX != 0) {
            val selfConsumedX = scrollX - beforeScrollX
            val unconsumedX = scrollDeltaX - preConsumedX - selfConsumedX
            nestedChildHelper.dispatchNestedScroll(
                selfConsumedX,
                0,
                unconsumedX,
                0,
                null,
                ViewCompat.TYPE_TOUCH,
                IntArray(2),
            )
        }
        transformed.recycle()
        if (stopNestedScroll) {
            nestedChildHelper.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            activePointerId = MotionEvent.INVALID_POINTER_ID
            velocityTracker?.recycle()
            velocityTracker = null
        }
        return handled
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (l != oldl) scrollStateController.onScrollPositionChanged()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        scrollStateController.onLayoutChanged()
    }

    fun dispose() {
        scrollStateController.dispose()
    }

    private fun horizontalScrollRange(): Int {
        return (innerLayout.measuredWidth - (width - paddingLeft - paddingRight)).coerceAtLeast(0)
    }
}
