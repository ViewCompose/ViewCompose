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
import kotlin.math.roundToInt

/**
 * ScrollableRow 使用的 HorizontalScrollView 宿主。
 * HorizontalScrollView host used by ScrollableRow.
 *
 * 它补齐水平滚动的 nested scrolling child 行为，使父级 nested scroll host 可协同消费滚动。
 * It adds horizontal nested-scrolling child behavior so parent nested scroll hosts can coordinate consumption.
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

    init {
        super.addView(
            innerLayout,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
        isFillViewport = true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val transformed = MotionEvent.obtain(event)
        var stopNestedScroll = false
        var cancelNativeFling = false
        var scrollDeltaX = 0
        var preConsumedX = 0
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
            }

            MotionEvent.ACTION_CANCEL -> {
                stopNestedScroll = true
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
}
