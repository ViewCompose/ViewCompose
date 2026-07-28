package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.math.cos
import kotlin.math.exp
import kotlinx.coroutines.isActive

internal suspend fun MonotonicFrameClock.awaitFrameNanos(): Long {
    return withFrameNanos { it }
}

/**
 * 协程动画循环的结束原因。
 * Terminal reason for a coroutine animation loop.
 */
enum class AnimationRunResult {
    Completed,
    Cancelled,
}

/**
 * 按 [animationSpec] 驱动一段动画，并在每帧通过 [onValue] 发布采样值。
 * Drives one animation described by [animationSpec] and publishes sampled values through [onValue].
 */
suspend fun <T> runAnimation(
    frameClock: MonotonicFrameClock,
    startValue: T,
    endValue: T,
    animationSpec: AnimationSpec,
    converter: AnimationConverter<T>,
    onValue: (T) -> Unit,
): AnimationRunResult {
    return when (animationSpec) {
        is InfiniteRepeatableSpec -> runInfiniteAnimation(
            frameClock = frameClock,
            startValue = startValue,
            endValue = endValue,
            animationSpec = animationSpec,
            converter = converter,
            onValue = onValue,
        )

        else -> runFiniteAnimation(
            frameClock = frameClock,
            startValue = startValue,
            endValue = endValue,
            animationSpec = animationSpec,
            converter = converter,
            onValue = onValue,
        )
    }
}

private suspend fun <T> runFiniteAnimation(
    frameClock: MonotonicFrameClock,
    startValue: T,
    endValue: T,
    animationSpec: AnimationSpec,
    converter: AnimationConverter<T>,
    onValue: (T) -> Unit,
): AnimationRunResult {
    val totalDurationNanos = animationDurationNanos(animationSpec)
    if (totalDurationNanos <= 0L) {
        onValue(
            sampleAnimationValue(
                startValue = startValue,
                endValue = endValue,
                animationSpec = animationSpec,
                converter = converter,
                playTimeNanos = 0L,
            ),
        )
        return AnimationRunResult.Completed
    }
    val startNanos = frameClock.awaitFrameNanos()
    var completed = false
    while (kotlin.coroutines.coroutineContext.isActive) {
        val frameNanos = frameClock.awaitFrameNanos()
        val playNanos = (frameNanos - startNanos).coerceAtLeast(0L)
        onValue(
            sampleAnimationValue(
                startValue = startValue,
                endValue = endValue,
                animationSpec = animationSpec,
                converter = converter,
                playTimeNanos = playNanos,
            ),
        )
        if (playNanos >= totalDurationNanos) {
            completed = true
            break
        }
    }
    if (completed) {
        // 循环结束后再发布一次精确终点，避免最后一帧略小于总时长造成残留误差。
        // Publish the exact terminal value after completion to avoid residual last-frame error.
        onValue(
            sampleAnimationValue(
                startValue = startValue,
                endValue = endValue,
                animationSpec = animationSpec,
                converter = converter,
                playTimeNanos = totalDurationNanos,
            ),
        )
        return AnimationRunResult.Completed
    }
    return AnimationRunResult.Cancelled
}

private suspend fun <T> runInfiniteAnimation(
    frameClock: MonotonicFrameClock,
    startValue: T,
    endValue: T,
    animationSpec: InfiniteRepeatableSpec,
    converter: AnimationConverter<T>,
    onValue: (T) -> Unit,
): AnimationRunResult {
    val startNanos = frameClock.awaitFrameNanos()
    while (kotlin.coroutines.coroutineContext.isActive) {
        val frameNanos = frameClock.awaitFrameNanos()
        val playNanos = (frameNanos - startNanos).coerceAtLeast(0L)
        onValue(
            sampleAnimationValue(
                startValue = startValue,
                endValue = endValue,
                animationSpec = animationSpec,
                converter = converter,
                playTimeNanos = playNanos,
            ),
        )
    }
    return AnimationRunResult.Cancelled
}

private data class AnimationTimingNanos(
    val delayNanos: Long,
    val durationNanos: Long,
)

/**
 * 计算规格的总时长；无限重复使用 [Long.MAX_VALUE] 作为哨兵值。
 * Calculates total duration; infinite repeats use [Long.MAX_VALUE] as a sentinel.
 */
fun animationDurationNanos(
    spec: AnimationSpec,
): Long {
    return when (spec) {
        is RepeatableSpec -> multiplyWithSaturation(
            value = animationDurationNanos(spec.animation),
            multiplier = spec.iterations.coerceAtLeast(0).toLong(),
        )

        is InfiniteRepeatableSpec -> Long.MAX_VALUE
        else -> timingNanos(spec).let { timing ->
            timing.delayNanos + timing.durationNanos
        }
    }
}

/**
 * 在给定播放时间采样动画值。
 * Samples an animated value at the supplied play time.
 */
fun <T> sampleAnimationValue(
    startValue: T,
    endValue: T,
    animationSpec: AnimationSpec,
    converter: AnimationConverter<T>,
    playTimeNanos: Long,
): T {
    when (animationSpec) {
        is RepeatableSpec -> return sampleRepeatableValue(
            startValue = startValue,
            endValue = endValue,
            animationSpec = animationSpec,
            converter = converter,
            playTimeNanos = playTimeNanos,
        )

        is InfiniteRepeatableSpec -> return sampleInfiniteRepeatableValue(
            startValue = startValue,
            endValue = endValue,
            animationSpec = animationSpec,
            converter = converter,
            playTimeNanos = playTimeNanos,
        )

        SnapSpec -> return endValue
        else -> Unit
    }
    val timing = timingNanos(animationSpec)
    val delayedPlayNanos = playTimeNanos.coerceAtLeast(0L) - timing.delayNanos
    if (delayedPlayNanos <= 0L) {
        return startValue
    }
    val fraction = (delayedPlayNanos.toDouble() / timing.durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
    val normalized = interpolateFraction(
        animationSpec = animationSpec,
        fraction = fraction,
    )
    val startVector = converter.toVector(startValue)
    val endVector = converter.toVector(endValue)
    val vector = FloatArray(startVector.size) { index ->
        lerp(
            start = startVector[index],
            stop = endVector.getOrElse(index) { startVector[index] },
            fraction = normalized,
        )
    }
    return converter.fromVector(vector)
}

/**
 * 判断有限动画在 [playTimeNanos] 是否已经结束。
 * Returns whether a finite animation has finished at [playTimeNanos].
 */
fun isAnimationFinished(
    spec: AnimationSpec,
    playTimeNanos: Long,
): Boolean {
    if (spec is InfiniteRepeatableSpec) {
        return false
    }
    return playTimeNanos >= animationDurationNanos(spec)
}

private fun timingNanos(spec: AnimationSpec): AnimationTimingNanos {
    return when (spec) {
        is TweenSpec -> AnimationTimingNanos(
            delayNanos = spec.delayMillis.toLong().coerceAtLeast(0L) * 1_000_000L,
            durationNanos = spec.durationMillis.toLong().coerceAtLeast(1L) * 1_000_000L,
        )

        is SpringSpec -> AnimationTimingNanos(
            delayNanos = 0L,
            durationNanos = spec.durationMillis.toLong().coerceAtLeast(1L) * 1_000_000L,
        )

        is KeyframesSpec -> AnimationTimingNanos(
            delayNanos = 0L,
            durationNanos = spec.durationMillis.toLong().coerceAtLeast(1L) * 1_000_000L,
        )

        SnapSpec -> AnimationTimingNanos(delayNanos = 0L, durationNanos = 1L)
        is RepeatableSpec -> timingNanos(spec.animation)
        is InfiniteRepeatableSpec -> timingNanos(spec.animation)
    }
}

private fun interpolateFraction(
    animationSpec: AnimationSpec,
    fraction: Float,
): Float {
    return when (animationSpec) {
        is TweenSpec -> animationSpec.easing.transform(fraction.coerceIn(0f, 1f)).coerceIn(0f, 1f)

        is SpringSpec -> {
            val t = fraction.coerceIn(0f, 1f)
            val damping = exp((-animationSpec.dampingRatio * 6f * t).toDouble()).toFloat()
            val oscillation = cos((animationSpec.stiffness * 0.06f * t).toDouble()).toFloat()
            (1f - damping * oscillation).coerceIn(0f, 1f)
        }

        is KeyframesSpec -> interpolateKeyframes(
            spec = animationSpec,
            fraction = fraction.coerceIn(0f, 1f),
        )

        is RepeatableSpec -> interpolateFraction(animationSpec.animation, fraction)
        is InfiniteRepeatableSpec -> interpolateFraction(animationSpec.animation, fraction)
        SnapSpec -> 1f
    }
}

private fun <T> sampleRepeatableValue(
    startValue: T,
    endValue: T,
    animationSpec: RepeatableSpec,
    converter: AnimationConverter<T>,
    playTimeNanos: Long,
): T {
    val iterations = animationSpec.iterations.coerceAtLeast(0)
    if (iterations == 0) {
        return startValue
    }
    val cycleDurationNanos = animationDurationNanos(animationSpec.animation).coerceAtLeast(1L)
    val totalDurationNanos = multiplyWithSaturation(
        value = cycleDurationNanos,
        multiplier = iterations.toLong(),
    )
    val clampedPlayTime = playTimeNanos.coerceAtLeast(0L)
    if (clampedPlayTime >= totalDurationNanos) {
        return repeatTerminalValue(
            startValue = startValue,
            endValue = endValue,
            repeatMode = animationSpec.repeatMode,
            iterations = iterations,
        )
    }
    val cycleIndex = clampedPlayTime / cycleDurationNanos
    val cyclePlayTime = clampedPlayTime % cycleDurationNanos
    val reverseThisCycle = animationSpec.repeatMode == RepeatMode.Reverse && cycleIndex % 2L == 1L
    val cycleStart = if (reverseThisCycle) endValue else startValue
    val cycleEnd = if (reverseThisCycle) startValue else endValue
    return sampleAnimationValue(
        startValue = cycleStart,
        endValue = cycleEnd,
        animationSpec = animationSpec.animation,
        converter = converter,
        playTimeNanos = cyclePlayTime,
    )
}

private fun <T> sampleInfiniteRepeatableValue(
    startValue: T,
    endValue: T,
    animationSpec: InfiniteRepeatableSpec,
    converter: AnimationConverter<T>,
    playTimeNanos: Long,
): T {
    val cycleDurationNanos = animationDurationNanos(animationSpec.animation).coerceAtLeast(1L)
    val clampedPlayTime = playTimeNanos.coerceAtLeast(0L)
    val cycleIndex = clampedPlayTime / cycleDurationNanos
    val cyclePlayTime = clampedPlayTime % cycleDurationNanos
    val reverseThisCycle = animationSpec.repeatMode == RepeatMode.Reverse && cycleIndex % 2L == 1L
    val cycleStart = if (reverseThisCycle) endValue else startValue
    val cycleEnd = if (reverseThisCycle) startValue else endValue
    return sampleAnimationValue(
        startValue = cycleStart,
        endValue = cycleEnd,
        animationSpec = animationSpec.animation,
        converter = converter,
        playTimeNanos = cyclePlayTime,
    )
}

private fun <T> repeatTerminalValue(
    startValue: T,
    endValue: T,
    repeatMode: RepeatMode,
    iterations: Int,
): T {
    // Reverse 模式偶数轮结束时回到起点，奇数轮结束时落到终点。
    // Reverse mode ends at start after even cycles and at end after odd cycles.
    return if (repeatMode == RepeatMode.Reverse && iterations % 2 == 0) {
        startValue
    } else {
        endValue
    }
}

private fun multiplyWithSaturation(
    value: Long,
    multiplier: Long,
): Long {
    if (value <= 0L || multiplier <= 0L) {
        return 0L
    }
    if (value > Long.MAX_VALUE / multiplier) {
        return Long.MAX_VALUE
    }
    return value * multiplier
}

private fun interpolateKeyframes(
    spec: KeyframesSpec,
    fraction: Float,
): Float {
    if (spec.keyframes.isEmpty()) {
        return fraction
    }
    val duration = spec.durationMillis.coerceAtLeast(1)
    val time = (duration * fraction).toInt()
    val sorted = spec.keyframes.sortedBy { it.timeMillis }
    val before = sorted.lastOrNull { it.timeMillis <= time } ?: Keyframe(0, 0f)
    val after = sorted.firstOrNull { it.timeMillis >= time } ?: Keyframe(duration, 1f)
    if (after.timeMillis == before.timeMillis) {
        return before.valueFraction.coerceIn(0f, 1f)
    }
    val local = ((time - before.timeMillis).toFloat() / (after.timeMillis - before.timeMillis).toFloat())
        .coerceIn(0f, 1f)
    return lerp(before.valueFraction, after.valueFraction, local).coerceIn(0f, 1f)
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction
