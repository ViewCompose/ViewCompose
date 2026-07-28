package com.viewcompose.gesture.core

/*
 * 测试职责：覆盖 gesture core 中的 Gesture Policy 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Gesture Policy behavior in gesture core and guards the contract against regressions.
 */

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GesturePolicyTest {
    @Test
    fun resolveLockAxis_horizontal_locksOnlyWhenHorizontalDominatesAndExceedsSlop() {
        assertNull(
            resolveLockAxis(
                dx = 7.9f,
                dy = 1f,
                orientation = GestureOrientation.Horizontal,
                touchSlop = 8f,
            ),
        )
        assertNull(
            resolveLockAxis(
                dx = 20f,
                dy = 24f,
                orientation = GestureOrientation.Horizontal,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            LockedAxis.Horizontal,
            resolveLockAxis(
                dx = 16f,
                dy = 6f,
                orientation = GestureOrientation.Horizontal,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun resolveLockAxis_vertical_locksOnlyWhenVerticalDominatesAndExceedsSlop() {
        assertNull(
            resolveLockAxis(
                dx = 1f,
                dy = 7.9f,
                orientation = GestureOrientation.Vertical,
                touchSlop = 8f,
            ),
        )
        assertNull(
            resolveLockAxis(
                dx = 20f,
                dy = 16f,
                orientation = GestureOrientation.Vertical,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            LockedAxis.Vertical,
            resolveLockAxis(
                dx = 4f,
                dy = 20f,
                orientation = GestureOrientation.Vertical,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun resolveLockAxis_free_selectsDominantAxisAboveSlop() {
        assertNull(
            resolveLockAxis(
                dx = 5f,
                dy = 6f,
                orientation = GestureOrientation.Free,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            LockedAxis.Horizontal,
            resolveLockAxis(
                dx = 16f,
                dy = 12f,
                orientation = GestureOrientation.Free,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            LockedAxis.Vertical,
            resolveLockAxis(
                dx = 10f,
                dy = 18f,
                orientation = GestureOrientation.Free,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun shouldActivateTransform_returnsFalse_whenAllMotionsBelowSlop() {
        val active = shouldActivateTransform(
            panMotion = 7.9f,
            zoomMotion = 5.0f,
            rotationMotion = 2.0f,
            touchSlop = 8f,
        )
        assertFalse(active)
    }

    @Test
    fun shouldActivateTransform_returnsTrue_whenAnyMotionExceedsSlop() {
        val active = shouldActivateTransform(
            panMotion = 8.1f,
            zoomMotion = 0f,
            rotationMotion = 0f,
            touchSlop = 8f,
        )
        assertTrue(active)
    }

    @Test
    fun resolveSwipeDecision_prefersVelocityOverDistance() {
        val decision = resolveSwipeDecision(
            axis = SwipeDecisionAxis.Horizontal,
            total = 8f,
            velocity = 1800f,
            minAnchor = 0f,
            maxAnchor = 120f,
            startAnchor = 0f,
            touchSlop = 8f,
            minFlingVelocity = 50f,
        )
        assertEquals(
            SwipeDecision.Swipe(SwipeDirection.StartToEnd),
            decision,
        )
    }

    @Test
    fun resolveSwipeDecision_usesDistanceThreshold_whenVelocityLow() {
        val decision = resolveSwipeDecision(
            axis = SwipeDecisionAxis.Vertical,
            total = -100f,
            velocity = -10f,
            minAnchor = 0f,
            maxAnchor = 200f,
            startAnchor = 0f,
            touchSlop = 8f,
            minFlingVelocity = 300f,
        )
        assertEquals(
            SwipeDecision.Swipe(SwipeDirection.BottomToTop),
            decision,
        )
    }

    @Test
    fun resolveSwipeDecision_settlesToNearestAnchor_whenBelowThreshold() {
        val decision = resolveSwipeDecision(
            axis = SwipeDecisionAxis.Horizontal,
            total = 40f,
            velocity = 0f,
            minAnchor = 0f,
            maxAnchor = 120f,
            startAnchor = 0f,
            touchSlop = 8f,
            minFlingVelocity = 300f,
        )
        assertEquals(
            SwipeDecision.Settle(SwipeSettleTarget.Min),
            decision,
        )
    }

    @Test
    fun resolveSwipeDecision_settlesToMax_whenProjectedNearMax() {
        val decision = resolveSwipeDecision(
            axis = SwipeDecisionAxis.Horizontal,
            total = 10f,
            velocity = 0f,
            minAnchor = 0f,
            maxAnchor = 120f,
            startAnchor = 100f,
            touchSlop = 8f,
            minFlingVelocity = 1_000f,
        )
        assertEquals(
            SwipeDecision.Settle(SwipeSettleTarget.Max),
            decision,
        )
    }

    @Test
    fun resolveSwipeDecision_returnsNone_whenNoAnchorsAndNoThresholdHit() {
        val decision = resolveSwipeDecision(
            axis = SwipeDecisionAxis.Horizontal,
            total = 4f,
            velocity = 0f,
            minAnchor = null,
            maxAnchor = null,
            startAnchor = 0f,
            touchSlop = 8f,
            minFlingVelocity = 300f,
        )
        assertEquals(SwipeDecision.None, decision)
    }

    @Test
    fun resolveAnchoredSettleTarget_prefersVelocityAndMovesToNextAnchor() {
        val result = resolveAnchoredSettleTarget(
            anchorsPx = listOf(-120f, 0f, 120f),
            startOffsetPx = 0f,
            currentOffsetPx = 12f,
            velocityPxPerSecond = 2_000f,
            touchSlopPx = 8f,
            minFlingVelocityPxPerSecond = 500f,
        )
        assertEquals(120f, result.targetOffsetPx)
        assertEquals(AnchoredSettleReason.Velocity, result.reason)
    }

    @Test
    fun resolveAnchoredSettleTarget_usesDistanceThreshold_whenVelocityLow() {
        val result = resolveAnchoredSettleTarget(
            anchorsPx = listOf(-120f, 0f, 120f),
            startOffsetPx = 0f,
            currentOffsetPx = -90f,
            velocityPxPerSecond = -10f,
            touchSlopPx = 8f,
            minFlingVelocityPxPerSecond = 500f,
        )
        assertEquals(-120f, result.targetOffsetPx)
        assertEquals(AnchoredSettleReason.Distance, result.reason)
    }

    @Test
    fun resolveAnchoredSettleTarget_fallsBackToNearestAnchor_whenBelowThreshold() {
        val result = resolveAnchoredSettleTarget(
            anchorsPx = listOf(-120f, 0f, 120f),
            startOffsetPx = 0f,
            currentOffsetPx = 20f,
            velocityPxPerSecond = 0f,
            touchSlopPx = 8f,
            minFlingVelocityPxPerSecond = 500f,
        )
        assertEquals(0f, result.targetOffsetPx)
        assertEquals(AnchoredSettleReason.Nearest, result.reason)
    }

    @Test
    fun resolveAnchoredOffsetOnAnchorUpdate_prefersCurrentValueOffset_thenCurrentOffset() {
        val anchoredByValue = resolveAnchoredOffsetOnAnchorUpdate(
            anchorsPx = listOf(-100f, 0f, 100f),
            currentValueOffsetPx = 0f,
            currentOffsetPx = 80f,
        )
        assertEquals(0f, anchoredByValue)

        val anchoredByOffset = resolveAnchoredOffsetOnAnchorUpdate(
            anchorsPx = listOf(-100f, 0f, 100f),
            currentValueOffsetPx = 30f,
            currentOffsetPx = 80f,
        )
        assertEquals(100f, anchoredByOffset)
    }

    @Test
    fun requireValidAnchorsPx_rejectsInvalidAnchorInputs() {
        assertInvalidAnchors(
            anchorsPx = emptyList(),
            expectedMessagePart = "must not be empty",
        )
        assertInvalidAnchors(
            anchorsPx = listOf(0f, 0f),
            expectedMessagePart = "strictly increasing",
        )
        assertInvalidAnchors(
            anchorsPx = listOf(0f, Float.NaN),
            expectedMessagePart = "not finite",
        )
        assertInvalidAnchors(
            anchorsPx = listOf(0f, Float.POSITIVE_INFINITY),
            expectedMessagePart = "not finite",
        )
    }

    private fun assertInvalidAnchors(
        anchorsPx: List<Float>,
        expectedMessagePart: String,
    ) {
        try {
            requireValidAnchorsPx(anchorsPx)
            fail("Expected IllegalArgumentException for anchors=$anchorsPx")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.contains(expectedMessagePart) == true)
        }
    }
}
