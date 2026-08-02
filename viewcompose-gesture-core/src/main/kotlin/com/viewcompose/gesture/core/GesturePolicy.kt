package com.viewcompose.gesture.core

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.SwipeDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/** Identifies the dominant axis selected after pointer movement crosses touch slop. */
enum class LockedAxis {
    /** The gesture is locked to horizontal movement. */
    Horizontal,

    /** The gesture is locked to vertical movement. */
    Vertical,
}

/** Selects the physical axis used to translate a drag into a logical swipe direction. */
enum class SwipeDecisionAxis {
    /** Positive motion maps to [SwipeDirection.StartToEnd]. */
    Horizontal,

    /** Positive motion maps to [SwipeDirection.TopToBottom]. */
    Vertical,
}

/**
 * Describes the action a renderer should take when a drag ends.
 *
 * A decision either emits a directional swipe, settles an anchored value, or performs no action.
 * Use [resolveSwipeDecision] to derive this result from distance and velocity.
 */
sealed interface SwipeDecision {
    /**
     * Requests a directional swipe callback.
     *
     * @property direction logical horizontal or physical vertical direction of the swipe
     */
    data class Swipe(
        val direction: SwipeDirection,
    ) : SwipeDecision

    /**
     * Requests convergence to one of the two supplied anchors.
     *
     * @property target endpoint closest to the projected drag position
     */
    data class Settle(
        val target: SwipeSettleTarget,
    ) : SwipeDecision

    /** Indicates that neither a swipe nor an anchored settle operation is available. */
    data object None : SwipeDecision
}

/** Selects the lower or upper endpoint used when a drag does not become a swipe. */
enum class SwipeSettleTarget {
    /** Settle to the supplied minimum anchor. */
    Min,

    /** Settle to the supplied maximum anchor. */
    Max,
}

/** Identifies the rule that selected an anchored drag's final offset. */
enum class AnchoredSettleReason {
    /** Fling velocity crossed the configured minimum. */
    Velocity,

    /** Drag distance crossed the configured segment threshold. */
    Distance,

    /** Neither threshold was crossed, so the closest anchor was selected. */
    Nearest,
}

/**
 * Configures the distance and velocity thresholds used to settle an anchored drag.
 *
 * The distance threshold is the greater of `touchSlopPx * slopMultiplier` and the adjacent
 * segment length multiplied by [segmentFraction]. A velocity at or above the effective fling
 * threshold takes precedence over distance. This value is immutable and platform-neutral; all
 * velocity and distance inputs remain the renderer's responsibility.
 *
 * @property slopMultiplier positive multiplier applied to the renderer's touch slop
 * @property segmentFraction fraction in `(0, 1]` applied to the adjacent anchor segment
 * @property minFlingVelocityOverridePxPerSecond optional non-negative velocity threshold that
 * replaces the renderer-provided minimum
 * @throws IllegalArgumentException if a multiplier is non-finite or outside its accepted range,
 * or if the velocity override is negative
 */
data class AnchoredThresholdPolicy(
    val slopMultiplier: Float = 2f,
    val segmentFraction: Float = 0.35f,
    val minFlingVelocityOverridePxPerSecond: Float? = null,
) {
    init {
        require(slopMultiplier > 0f && slopMultiplier.isFinite()) {
            "slopMultiplier must be positive and finite."
        }
        require(segmentFraction > 0f && segmentFraction <= 1f && segmentFraction.isFinite()) {
            "segmentFraction must be in (0, 1]."
        }
        require(minFlingVelocityOverridePxPerSecond == null || minFlingVelocityOverridePxPerSecond >= 0f) {
            "minFlingVelocityOverridePxPerSecond must be >= 0 when provided."
        }
    }
}

/**
 * Reports the anchor selected when an anchored drag settles.
 *
 * @property targetOffsetPx exact pixel offset from the validated anchor list
 * @property reason rule that selected [targetOffsetPx]
 */
data class AnchoredSettleResult(
    val targetOffsetPx: Float,
    val reason: AnchoredSettleReason,
)

/**
 * Validates the canonical ordering required by anchored gesture policies.
 *
 * @param anchorsPx anchor offsets in physical pixels, ordered from minimum to maximum
 * @throws IllegalArgumentException if the list is empty, contains a non-finite value, or is not
 * strictly increasing
 */
fun requireValidAnchorsPx(anchorsPx: List<Float>) {
    require(anchorsPx.isNotEmpty()) { "Anchors must not be empty." }
    var previous = Float.NEGATIVE_INFINITY
    anchorsPx.forEachIndexed { index, offset ->
        require(offset.isFinite()) { "Anchor offset at index $index is not finite: $offset." }
        require(offset > previous) {
            "Anchor offsets must be strictly increasing. index=$index offset=$offset previous=$previous"
        }
        previous = offset
    }
}

/**
 * Chooses the offset to preserve after an anchored gesture receives a new anchor list.
 *
 * An exact [currentValueOffsetPx] match wins so a semantic value remains stable. Otherwise the
 * nearest anchor to a finite [currentOffsetPx] is used. When neither input can be preserved, the
 * first anchor becomes the initial offset. Equal-distance ties resolve to the lower list index.
 *
 * @param anchorsPx non-empty, finite, strictly increasing offsets in physical pixels
 * @param currentValueOffsetPx offset associated with the current semantic value, or `null`
 * @param currentOffsetPx current visual offset in physical pixels, or `null`
 * @return one of the exact values in [anchorsPx]
 * @throws IllegalArgumentException if [anchorsPx] fails [requireValidAnchorsPx]
 * @sample com.viewcompose.gesture.core.samples.updateAnchorsWithoutLosingPosition
 */
fun resolveAnchoredOffsetOnAnchorUpdate(
    anchorsPx: List<Float>,
    currentValueOffsetPx: Float?,
    currentOffsetPx: Float?,
): Float {
    requireValidAnchorsPx(anchorsPx)
    if (currentValueOffsetPx != null) {
        val exactMatch = anchorsPx.firstOrNull { it == currentValueOffsetPx }
        if (exactMatch != null) {
            return exactMatch
        }
    }
    if (currentOffsetPx != null && currentOffsetPx.isFinite()) {
        return nearestAnchorOffset(
            anchorsPx = anchorsPx,
            offsetPx = currentOffsetPx,
        )
    }
    return anchorsPx.first()
}

/**
 * Resolves the target anchor for a completed anchored drag.
 *
 * The anchor nearest [startOffsetPx] defines the starting segment. Absolute velocity at or above
 * the effective fling threshold moves exactly one anchor in the velocity direction and wins over
 * distance. Otherwise a drag crossing the configured distance threshold moves one anchor in the
 * drag direction. The closest anchor to [currentOffsetPx] is used when neither threshold wins.
 * Movement is clamped at the first and last anchors.
 *
 * @param anchorsPx non-empty, finite, strictly increasing offsets in physical pixels
 * @param startOffsetPx visual offset at gesture start; non-finite values are not rejected and will
 * resolve to the first anchor through nearest-anchor comparison
 * @param currentOffsetPx visual offset when the gesture ends, in physical pixels
 * @param velocityPxPerSecond signed terminal velocity; positive values move toward larger anchors
 * @param touchSlopPx non-negative finite platform touch slop in physical pixels
 * @param minFlingVelocityPxPerSecond non-negative finite platform fling threshold
 * @param thresholdPolicy threshold overrides and distance multipliers
 * @return the exact target anchor and the rule that selected it
 * @throws IllegalArgumentException if anchors, touch slop, or platform fling threshold are invalid
 * @sample com.viewcompose.gesture.core.samples.resolveAnchoredDrag
 */
fun resolveAnchoredSettleTarget(
    anchorsPx: List<Float>,
    startOffsetPx: Float,
    currentOffsetPx: Float,
    velocityPxPerSecond: Float,
    touchSlopPx: Float,
    minFlingVelocityPxPerSecond: Float,
    thresholdPolicy: AnchoredThresholdPolicy = AnchoredThresholdPolicy(),
): AnchoredSettleResult {
    requireValidAnchorsPx(anchorsPx)
    require(touchSlopPx >= 0f && touchSlopPx.isFinite()) {
        "touchSlopPx must be finite and >= 0."
    }
    require(minFlingVelocityPxPerSecond >= 0f && minFlingVelocityPxPerSecond.isFinite()) {
        "minFlingVelocityPxPerSecond must be finite and >= 0."
    }
    val startIndex = nearestAnchorIndex(
        anchorsPx = anchorsPx,
        offsetPx = startOffsetPx,
    )
    val minFlingVelocity = thresholdPolicy.minFlingVelocityOverridePxPerSecond
        ?: minFlingVelocityPxPerSecond
    if (abs(velocityPxPerSecond) >= minFlingVelocity) {
        // Velocity wins over distance thresholds to match fast-fling expectations.
        val direction = velocityPxPerSecond.sign.toInt()
        val targetIndex = when {
            direction > 0 -> (startIndex + 1).coerceAtMost(anchorsPx.lastIndex)
            direction < 0 -> (startIndex - 1).coerceAtLeast(0)
            else -> startIndex
        }
        return AnchoredSettleResult(
            targetOffsetPx = anchorsPx[targetIndex],
            reason = AnchoredSettleReason.Velocity,
        )
    }
    val delta = currentOffsetPx - anchorsPx[startIndex]
    if (delta != 0f) {
        val direction = delta.sign.toInt()
        val candidateIndex = when {
            direction > 0 -> (startIndex + 1).coerceAtMost(anchorsPx.lastIndex)
            direction < 0 -> (startIndex - 1).coerceAtLeast(0)
            else -> startIndex
        }
        if (candidateIndex != startIndex) {
            val segmentDistance = abs(anchorsPx[candidateIndex] - anchorsPx[startIndex])
            val distanceThreshold = max(
                touchSlopPx * thresholdPolicy.slopMultiplier,
                segmentDistance * thresholdPolicy.segmentFraction,
            )
            if (abs(delta) >= distanceThreshold) {
                return AnchoredSettleResult(
                    targetOffsetPx = anchorsPx[candidateIndex],
                    reason = AnchoredSettleReason.Distance,
                )
            }
        }
    }
    return AnchoredSettleResult(
        targetOffsetPx = nearestAnchorOffset(
            anchorsPx = anchorsPx,
            offsetPx = currentOffsetPx,
        ),
        reason = AnchoredSettleReason.Nearest,
    )
}

private fun nearestAnchorOffset(
    anchorsPx: List<Float>,
    offsetPx: Float,
): Float {
    return anchorsPx[nearestAnchorIndex(anchorsPx, offsetPx)]
}

private fun nearestAnchorIndex(
    anchorsPx: List<Float>,
    offsetPx: Float,
): Int {
    var nearestIndex = 0
    var nearestDistance = abs(anchorsPx.first() - offsetPx)
    for (index in 1 until anchorsPx.size) {
        val distance = abs(anchorsPx[index] - offsetPx)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
        }
    }
    return nearestIndex
}

/**
 * Resolves an axis lock from accumulated pointer movement.
 *
 * A fixed horizontal or vertical orientation locks only when its own absolute displacement reaches
 * [touchSlop] and is at least as large as the perpendicular displacement. Free orientation chooses
 * the dominant axis after either displacement reaches slop; an exact tie selects horizontal.
 *
 * Inputs are normally physical pixels. This function intentionally does not validate negative or
 * non-finite slop, so callers should pass a platform-derived non-negative finite value.
 *
 * @param dx accumulated horizontal movement in renderer-local units
 * @param dy accumulated vertical movement in renderer-local units
 * @param orientation allowed gesture orientation
 * @param touchSlop minimum absolute movement required to lock
 * @return the locked axis, or `null` while the recognizer should continue waiting
 * @sample com.viewcompose.gesture.core.samples.lockDragAxis
 */
fun resolveLockAxis(
    dx: Float,
    dy: Float,
    orientation: GestureOrientation,
    touchSlop: Float,
): LockedAxis? {
    val absDx = abs(dx)
    val absDy = abs(dy)
    return when (orientation) {
        GestureOrientation.Horizontal -> {
            if (absDx < touchSlop || absDx < absDy) null else LockedAxis.Horizontal
        }

        GestureOrientation.Vertical -> {
            if (absDy < touchSlop || absDy < absDx) null else LockedAxis.Vertical
        }

        GestureOrientation.Free -> {
            if (maxOf(absDx, absDy) < touchSlop) {
                null
            } else if (absDx >= absDy) {
                LockedAxis.Horizontal
            } else {
                LockedAxis.Vertical
            }
        }
    }
}

/**
 * Returns whether accumulated transform motion has crossed an activation threshold.
 *
 * Activation uses a strict greater-than comparison. Values exactly equal to [touchSlop] remain
 * inactive. The caller is responsible for converting pan, zoom, and rotation into comparable
 * non-negative motion magnitudes; raw zoom factors and degrees are not inherently comparable to
 * pixel pan distance.
 *
 * @param panMotion normalized accumulated pan motion
 * @param zoomMotion normalized accumulated zoom motion
 * @param rotationMotion normalized accumulated rotation motion
 * @param touchSlop activation threshold in the same normalized units
 * @return `true` when any motion value is strictly greater than [touchSlop]
 */
fun shouldActivateTransform(
    panMotion: Float,
    zoomMotion: Float,
    rotationMotion: Float,
    touchSlop: Float,
): Boolean {
    return panMotion > touchSlop || zoomMotion > touchSlop || rotationMotion > touchSlop
}

/**
 * Resolves a directional swipe or two-anchor settle decision when a drag ends.
 *
 * Absolute [velocity] at or above [minFlingVelocity] takes precedence. Otherwise distance must
 * reach the greater of twice [touchSlop] and 35 percent of the supplied anchor span. Positive
 * motion maps to start-to-end or top-to-bottom. Below both thresholds, two non-null anchors settle
 * to the endpoint nearest `startAnchor + total`; an exact tie selects [SwipeSettleTarget.Min]. If
 * the anchor pair is incomplete, [SwipeDecision.None] is returned.
 *
 * This low-level policy does not validate anchor ordering, finite inputs, or layout-direction
 * resolution. Horizontal directions remain logical and must be resolved by the renderer.
 *
 * @param axis axis used to translate motion into [SwipeDirection]
 * @param total signed accumulated drag distance in renderer-local units, normally physical pixels
 * @param velocity signed terminal velocity in units per second, normally physical pixels per second
 * @param minAnchor optional minimum endpoint used for span and settle calculations
 * @param maxAnchor optional maximum endpoint used for span and settle calculations
 * @param startAnchor endpoint-relative position at gesture start
 * @param touchSlop platform touch slop in the same units as [total]
 * @param minFlingVelocity absolute velocity threshold in the same units per second as [velocity]
 * @return a directional swipe, anchored settle target, or no action
 * @sample com.viewcompose.gesture.core.samples.resolveDragCompletion
 */
fun resolveSwipeDecision(
    axis: SwipeDecisionAxis,
    total: Float,
    velocity: Float,
    minAnchor: Float?,
    maxAnchor: Float?,
    startAnchor: Float,
    touchSlop: Float,
    minFlingVelocity: Float,
): SwipeDecision {
    val anchorSpan = if (minAnchor != null && maxAnchor != null) {
        abs(maxAnchor - minAnchor)
    } else {
        0f
    }
    val distanceThreshold = max(touchSlop * 2f, anchorSpan * 0.35f)
    val preferVelocity = abs(velocity) >= minFlingVelocity
    val towardMax = when {
        preferVelocity -> velocity > 0f
        abs(total) >= distanceThreshold -> total > 0f
        else -> null
    }
    if (towardMax != null) {
        return SwipeDecision.Swipe(
            direction = when (axis) {
                SwipeDecisionAxis.Horizontal -> if (towardMax) {
                    SwipeDirection.StartToEnd
                } else {
                    SwipeDirection.EndToStart
                }

                SwipeDecisionAxis.Vertical -> if (towardMax) {
                    SwipeDirection.TopToBottom
                } else {
                    SwipeDirection.BottomToTop
                }
            },
        )
    }
    if (minAnchor != null && maxAnchor != null) {
        val projected = startAnchor + total
        val distanceToMin = abs(projected - minAnchor)
        val distanceToMax = abs(projected - maxAnchor)
        return if (distanceToMax < distanceToMin) {
            SwipeDecision.Settle(SwipeSettleTarget.Max)
        } else {
            SwipeDecision.Settle(SwipeSettleTarget.Min)
        }
    }
    return SwipeDecision.None
}
