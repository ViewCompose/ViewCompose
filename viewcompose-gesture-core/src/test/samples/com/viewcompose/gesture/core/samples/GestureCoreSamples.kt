package com.viewcompose.gesture.core.samples

import com.viewcompose.gesture.core.AnchoredSettleReason
import com.viewcompose.gesture.core.LockedAxis
import com.viewcompose.gesture.core.SwipeDecision
import com.viewcompose.gesture.core.SwipeDecisionAxis
import com.viewcompose.gesture.core.resolveAnchoredOffsetOnAnchorUpdate
import com.viewcompose.gesture.core.resolveAnchoredSettleTarget
import com.viewcompose.gesture.core.resolveLockAxis
import com.viewcompose.gesture.core.resolveSwipeDecision
import com.viewcompose.ui.gesture.GestureOrientation

fun lockDragAxis() {
    val axis = resolveLockAxis(
        dx = 18f,
        dy = 6f,
        orientation = GestureOrientation.Free,
        touchSlop = 8f,
    )

    check(axis == LockedAxis.Horizontal)
}

fun resolveDragCompletion() {
    val decision = resolveSwipeDecision(
        axis = SwipeDecisionAxis.Horizontal,
        total = 12f,
        velocity = 1_400f,
        minAnchor = 0f,
        maxAnchor = 240f,
        startAnchor = 0f,
        touchSlop = 8f,
        minFlingVelocity = 600f,
    )

    check(decision is SwipeDecision.Swipe)
}

fun updateAnchorsWithoutLosingPosition() {
    val offset = resolveAnchoredOffsetOnAnchorUpdate(
        anchorsPx = listOf(0f, 160f, 320f),
        currentValueOffsetPx = 160f,
        currentOffsetPx = 190f,
    )

    check(offset == 160f)
}

fun resolveAnchoredDrag() {
    val result = resolveAnchoredSettleTarget(
        anchorsPx = listOf(0f, 160f, 320f),
        startOffsetPx = 160f,
        currentOffsetPx = 190f,
        velocityPxPerSecond = 1_200f,
        touchSlopPx = 8f,
        minFlingVelocityPxPerSecond = 600f,
    )

    check(result.targetOffsetPx == 320f)
    check(result.reason == AnchoredSettleReason.Velocity)
}
