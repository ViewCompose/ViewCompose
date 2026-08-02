package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.math.cos
import kotlin.math.exp
import kotlinx.coroutines.isActive

internal suspend fun MonotonicFrameClock.awaitFrameNanos(): Long {
    return withFrameNanos { it }
}

/** Identifies the terminal state observed by an animation loop that returns normally. */
enum class AnimationRunResult {
    /** The finite specification reached its duration and published its terminal sample. */
    Completed,

    /** The coroutine became inactive before a finite animation completed. */
    Cancelled,
}

/**
 * Drives [animationSpec] with [frameClock] and publishes sampled values through [onValue].
 *
 * The function starts by awaiting a frame timestamp, then awaits each frame that produces a sample.
 * A completed finite animation publishes an exact terminal sample after the loop, so [onValue] may
 * observe the terminal value twice. An [InfiniteRepeatableSpec] runs until cancellation.
 *
 * The callback runs in the caller's coroutine on the frame-clock execution context and must not
 * block frame delivery. If cancellation is observed between callbacks, this function returns
 * [AnimationRunResult.Cancelled]. A suspending frame clock commonly throws the coroutine's
 * cancellation exception instead; frame-clock and callback exceptions always propagate. No
 * terminal value is forced after cancellation or failure.
 *
 * Endpoint conversion allocates vectors through [converter] for each sample. Use stable,
 * allocation-conscious converters on frame-sensitive paths.
 *
 * @sample com.viewcompose.animation.core.samples.runAnimationSample
 *
 * @param T domain value interpolated by the animation
 * @param frameClock monotonic frame source that paces the loop
 * @param startValue value used before progress begins and as the interpolation origin
 * @param endValue value reached on successful finite completion
 * @param animationSpec timing, easing, and repetition policy
 * @param converter converter whose stable vector dimensions are interpolated independently
 * @param onValue callback invoked for each sampled value on the animation coroutine
 * @return [AnimationRunResult.Completed] after finite completion or
 * [AnimationRunResult.Cancelled] when inactivity is observed without a thrown cancellation
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
        // Publish from the exact duration to eliminate residual error from a late or rounded frame.
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
 * Returns the normalized total duration of [spec] in nanoseconds.
 *
 * Tween delays are included. Non-positive finite durations normalize to one millisecond, except a
 * zero-iteration [RepeatableSpec], which reports zero. Repeat multiplication saturates instead of
 * overflowing. [InfiniteRepeatableSpec] returns [Long.MAX_VALUE] as an infinity sentinel.
 *
 * @param spec specification whose full duration is required
 * @return normalized duration in nanoseconds or [Long.MAX_VALUE] for an infinite repeat
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
 * Returns the value of [animationSpec] at [playTimeNanos] without owning a clock or mutable state.
 *
 * Negative play time is treated as zero by finite and repeated samplers. Progress is clamped to the
 * specification interval. Interpolation uses the dimension count returned for [startValue]; missing
 * end dimensions retain their corresponding start dimension and extra end dimensions are ignored.
 * The supplied values and vectors are not retained by the engine.
 *
 * This function is deterministic for deterministic converters and is suitable for tests, seeking,
 * transition channels, and preview tooling. It allocates endpoint and result vectors per call.
 *
 * @sample com.viewcompose.animation.core.samples.sampleAnimationValueSample
 *
 * @param T domain value interpolated by the animation
 * @param startValue interpolation origin and pre-delay result
 * @param endValue terminal value for a non-reversed finite animation
 * @param animationSpec timing, easing, and repetition policy
 * @param converter converter that defines independently interpolated dimensions
 * @param playTimeNanos elapsed play time relative to the animation start, in nanoseconds
 * @return the reconstructed domain value at the normalized play time
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
 * Returns whether [playTimeNanos] has reached the normalized finite duration of [spec].
 *
 * Infinite repeats always return `false`. Negative play time is compared as supplied; it does not
 * finish a positive-duration animation. A zero-iteration repeat is finished at time zero.
 *
 * @param spec specification whose completion state is queried
 * @param playTimeNanos elapsed play time in nanoseconds
 * @return `true` when a finite specification has reached its terminal time
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
    // Alternating direction returns to the origin after every even-numbered cycle.
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
