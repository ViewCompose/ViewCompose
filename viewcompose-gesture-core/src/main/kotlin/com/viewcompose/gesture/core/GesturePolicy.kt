package com.viewcompose.gesture.core

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.SwipeDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * 指针移动超过 slop 后锁定的主轴。
 * Primary axis locked after pointer movement crosses touch slop.
 */
enum class LockedAxis {
    Horizontal,
    Vertical,
}

/**
 * swipe 判定时使用的轴向。
 * Axis used when resolving a swipe decision.
 */
enum class SwipeDecisionAxis {
    Horizontal,
    Vertical,
}

/**
 * 拖拽结束后 renderer 应执行的 swipe/settle 决策。
 * Swipe or settle decision a renderer should perform after drag end.
 */
sealed interface SwipeDecision {
    data class Swipe(val direction: SwipeDirection) : SwipeDecision

    data class Settle(val target: SwipeSettleTarget) : SwipeDecision

    data object None : SwipeDecision
}

/**
 * 未形成 swipe 时应收敛到的端点。
 * Endpoint to settle to when the drag does not become a swipe.
 */
enum class SwipeSettleTarget {
    Min,
    Max,
}

/**
 * anchored draggable 选择目标 anchor 的原因。
 * Reason why anchored draggable selected a target anchor.
 */
enum class AnchoredSettleReason {
    Velocity,
    Distance,
    Nearest,
}

/**
 * anchored draggable 的距离和速度阈值策略。
 * Distance and velocity threshold policy for anchored draggable.
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
 * anchored draggable 结束时解析出的目标位置。
 * Target position resolved when anchored draggable settles.
 */
data class AnchoredSettleResult(
    val targetOffsetPx: Float,
    val reason: AnchoredSettleReason,
)

/**
 * 校验 anchor 像素列表必须非空、有限且严格递增。
 * Validates that anchor pixel offsets are non-empty, finite, and strictly increasing.
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
 * anchor 集合变化时，选择应保留的当前 offset。
 * Chooses the current offset to preserve after the anchor set changes.
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
 * 根据起点、当前位置和速度解析 anchored draggable 的最终 anchor。
 * Resolves the final anchor from the start offset, current offset, and velocity.
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
        // 速度优先于距离阈值，匹配用户快速 fling 时的预期。
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
 * 根据累计移动和方向策略决定是否锁定手势轴。
 * Resolves whether accumulated movement should lock the gesture axis.
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
 * 判断 transform 手势是否已越过任一激活阈值。
 * Returns whether a transform gesture crossed any activation threshold.
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
 * 根据拖拽距离、速度和可用 anchor 解析 swipe 决策。
 * Resolves a swipe decision from drag distance, velocity, and available anchors.
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
