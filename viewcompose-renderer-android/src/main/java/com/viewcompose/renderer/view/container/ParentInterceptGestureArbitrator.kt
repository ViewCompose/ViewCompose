package com.viewcompose.renderer.view.container

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/** Coordinates touch-stream ownership for scrollables nested in non-cooperating parents. */
internal class ParentInterceptGestureArbitrator(
    private val host: View,
    private val axis: () -> Axis,
) {
    internal enum class Axis {
        Horizontal,
        Vertical,
    }

    private enum class GestureDirection {
        Unresolved,
        Primary,
        Cross,
    }

    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var gestureDirection = GestureDirection.Unresolved

    fun onDispatchTouchEvent(event: MotionEvent, enabled: Boolean) {
        if (!enabled) {
            release()
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                gestureDirection = GestureDirection.Unresolved
                requestDisallowIntercept(
                    canScrollInEitherDirection() && !shouldYieldInitialDownToPullRefresh(),
                )
            }

            MotionEvent.ACTION_MOVE -> {
                resolveGestureDirection(event)
                when (gestureDirection) {
                    GestureDirection.Primary -> {
                        val delta = when (axis()) {
                            Axis.Horizontal -> lastX - event.x
                            Axis.Vertical -> lastY - event.y
                        }
                        if (delta != 0f) {
                            requestDisallowIntercept(canScroll(if (delta > 0f) 1 else -1))
                        }
                    }

                    GestureDirection.Cross -> requestDisallowIntercept(false)
                    GestureDirection.Unresolved -> Unit
                }
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> release()
        }
    }

    fun release() {
        requestDisallowIntercept(false)
        gestureDirection = GestureDirection.Unresolved
    }

    private fun resolveGestureDirection(event: MotionEvent) {
        if (gestureDirection != GestureDirection.Unresolved) return
        val horizontalDistance = abs(event.x - downX)
        val verticalDistance = abs(event.y - downY)
        if (horizontalDistance <= touchSlop && verticalDistance <= touchSlop) return
        val primaryDistance = when (axis()) {
            Axis.Horizontal -> horizontalDistance
            Axis.Vertical -> verticalDistance
        }
        val crossDistance = when (axis()) {
            Axis.Horizontal -> verticalDistance
            Axis.Vertical -> horizontalDistance
        }
        gestureDirection = if (primaryDistance >= crossDistance) {
            GestureDirection.Primary
        } else {
            GestureDirection.Cross
        }
    }

    private fun canScrollInEitherDirection(): Boolean = canScroll(-1) || canScroll(1)

    private fun canScroll(direction: Int): Boolean = when (axis()) {
        Axis.Horizontal -> host.canScrollHorizontally(direction)
        Axis.Vertical -> host.canScrollVertically(direction)
    }

    private fun shouldYieldInitialDownToPullRefresh(): Boolean {
        if (axis() != Axis.Vertical || host.canScrollVertically(-1)) return false
        var ancestor = host.parent
        while (ancestor is View) {
            if (ancestor is SwipeRefreshLayout) {
                return ancestor.isEnabled && !ancestor.isRefreshing
            }
            ancestor = ancestor.parent
        }
        return false
    }

    private fun requestDisallowIntercept(disallow: Boolean) {
        host.parent?.requestDisallowInterceptTouchEvent(disallow)
    }
}
