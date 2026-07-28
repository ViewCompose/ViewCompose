package com.viewcompose.animation.core

/**
 * 动画采样规格的公共根类型。
 * Common root type for animation sampling specs.
 */
sealed interface AnimationSpec

/**
 * 固定时长 tween 动画，使用 easing 将线性进度映射为视觉进度。
 * Fixed-duration tween animation that maps linear time through an easing curve.
 */
data class TweenSpec(
    val durationMillis: Int = 300,
    val delayMillis: Int = 0,
    val easing: Easing = EasingDefaults.FastOutSlowIn,
) : AnimationSpec

/**
 * 近似弹簧规格，当前实现以给定时长内的阻尼振荡曲线采样。
 * Approximate spring spec sampled as a damped oscillation within the supplied duration.
 */
data class SpringSpec(
    val dampingRatio: Float = 0.8f,
    val stiffness: Float = 250f,
    val durationMillis: Int = 550,
) : AnimationSpec

/**
 * keyframes 规格中的单个时间点，valueFraction 表示 0..1 之间的目标进度。
 * One point in a keyframes spec; valueFraction is the target progress in the 0..1 range.
 */
data class Keyframe(
    val timeMillis: Int,
    val valueFraction: Float,
)

/**
 * 按时间点线性插值的 keyframes 动画。
 * Keyframes animation that linearly interpolates between time points.
 */
data class KeyframesSpec(
    val durationMillis: Int,
    val keyframes: List<Keyframe>,
) : AnimationSpec

/**
 * 立即跳到目标值的无过渡规格。
 * No-transition spec that snaps directly to the target value.
 */
data object SnapSpec : AnimationSpec

/**
 * repeatable 动画的循环模式。
 * Cycle mode for repeatable animation specs.
 */
enum class RepeatMode {
    Restart,
    Reverse,
}

/**
 * 有限次数重复动画。
 * Finite repeat animation.
 */
data class RepeatableSpec(
    val iterations: Int,
    val animation: AnimationSpec,
    val repeatMode: RepeatMode = RepeatMode.Restart,
) : AnimationSpec

/**
 * 无限重复动画，只会在协程取消时结束。
 * Infinite repeat animation that ends only when its coroutine is cancelled.
 */
data class InfiniteRepeatableSpec(
    val animation: AnimationSpec,
    val repeatMode: RepeatMode = RepeatMode.Restart,
) : AnimationSpec

fun tween(
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    easing: Easing = EasingDefaults.FastOutSlowIn,
): TweenSpec = TweenSpec(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = easing,
)

fun spring(
    dampingRatio: Float = 0.8f,
    stiffness: Float = 250f,
    durationMillis: Int = 550,
): SpringSpec = SpringSpec(
    dampingRatio = dampingRatio,
    stiffness = stiffness,
    durationMillis = durationMillis,
)

fun keyframes(
    durationMillis: Int,
    vararg keyframes: Keyframe,
): KeyframesSpec {
    return KeyframesSpec(
        durationMillis = durationMillis,
        keyframes = keyframes.sortedBy { it.timeMillis },
    )
}

fun keyframe(
    timeMillis: Int,
    valueFraction: Float,
): Keyframe = Keyframe(
    timeMillis = timeMillis,
    valueFraction = valueFraction,
)

fun snap(): SnapSpec = SnapSpec

fun repeatable(
    iterations: Int,
    animation: AnimationSpec,
    repeatMode: RepeatMode = RepeatMode.Restart,
): RepeatableSpec = RepeatableSpec(
    iterations = iterations,
    animation = animation,
    repeatMode = repeatMode,
)

fun infiniteRepeatable(
    animation: AnimationSpec,
    repeatMode: RepeatMode = RepeatMode.Restart,
): InfiniteRepeatableSpec = InfiniteRepeatableSpec(
    animation = animation,
    repeatMode = repeatMode,
)
