package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val SPRING_DURATION_STEP_NANOS = NANOS_PER_MILLISECOND
private const val VELOCITY_THRESHOLD_SECONDS = 0.016
private const val CRITICAL_DAMPING_EPSILON = 1e-4
private const val BASE_DECAY_RATE = 4.2

internal suspend fun MonotonicFrameClock.awaitFrameNanos(): Long {
    return withFrameNanos { it }
}

/**
 * Evaluates one finite target animation at explicit monotonic play times.
 *
 * The evaluator converts endpoints, velocity, thresholds, and scratch vectors once. [stateAt]
 * reuses that storage and creates only the immutable domain state returned to the caller. The
 * instance is not thread-safe; one animation owner must serialize sampling.
 *
 * Physical spring duration is the first one-millisecond sample that satisfies both position and
 * velocity thresholds in every component, capped by [SpringSpec.maxDurationMillis]. A capped solve
 * retains its sampled state and reports [AnimationEndReason.DurationLimitReached].
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param initialValue value at play time zero
 * @param targetValue requested target value
 * @param animationSpec finite timing or physical specification
 * @param converter stable value and velocity converter
 * @param initialVelocity typed velocity at play time zero
 * @throws IllegalArgumentException when converter output, thresholds, or a derived physical sample
 * cannot satisfy the finite vector contract
 */
class TargetAnimation<T, V>(
    initialValue: T,
    val targetValue: T,
    animationSpec: FiniteAnimationSpec,
    private val converter: AnimationConverter<T, V>,
    initialVelocity: AnimationVelocity<V> = AnimationVelocity(converter.zeroVelocity),
) {
    private val vectorSize = converter.validatedVectorSize()
    private val initialVector = converter.valueVector(initialValue, vectorSize, "initialValue")
    private val targetVector = converter.valueVector(targetValue, vectorSize, "targetValue")
    private val initialVelocityVector =
        converter.velocityVector(initialVelocity.valuePerSecond, vectorSize, "initialVelocity")
    private val thresholdVector =
        converter.velocityVector(converter.visibilityThreshold, vectorSize, "visibilityThreshold")
    private val valueVector = FloatArray(vectorSize)
    private val velocityVector = FloatArray(vectorSize)
    private val normalizedSpec = animationSpec.normalized()
    private val durationResolution = resolveDuration()

    init {
        converter.validateZeroVelocity(vectorSize)
    }

    /** Returns the deterministic finite duration or physical safety guard in nanoseconds. */
    val durationNanos: Long
        get() = durationResolution.durationNanos

    /** Returns the normal reason produced when [durationNanos] is reached. */
    val terminalEndReason: AnimationEndReason
        get() = durationResolution.endReason

    internal var lastSampleReachedBound: Boolean = false
        private set

    /**
     * Returns the animation state at [playTimeNanos].
     *
     * Negative time is treated as zero and time beyond [durationNanos] is pinned to the terminal
     * sample. A successfully settled spring returns the exact target and zero velocity; a spring
     * that reaches its safety guard retains the physical sample at the guard.
     *
     * @param playTimeNanos elapsed play time relative to this animation in nanoseconds
     * @return immutable value, typed velocity, and normalized play time
     * @throws IllegalArgumentException when a custom easing or derived sample produces a non-finite
     * vector component
     */
    fun stateAt(playTimeNanos: Long): AnimationState<T, V> {
        return stateAt(playTimeNanos, bounds = null)
    }

    /** Returns whether [playTimeNanos] has reached this animation's terminal sample. */
    fun isFinished(playTimeNanos: Long): Boolean {
        return playTimeNanos >= durationNanos
    }

    internal fun stateAt(
        playTimeNanos: Long,
        bounds: AnimationVectorBounds?,
    ): AnimationState<T, V> {
        val normalizedPlayTime = playTimeNanos.coerceIn(0L, durationNanos)
        sampleVectors(normalizedPlayTime)
        valueVector.requireFinite("sampled value")
        velocityVector.requireFinite("sampled velocity")
        lastSampleReachedBound = bounds?.clamp(valueVector, velocityVector) == true
        return AnimationState(
            value = converter.convertFromVector(valueVector),
            velocity = AnimationVelocity(converter.convertVelocityFromVector(velocityVector)),
            playTimeNanos = normalizedPlayTime,
        )
    }

    private fun sampleVectors(playTimeNanos: Long) {
        when (val spec = normalizedSpec) {
            is SpringSpec -> sampleSpring(
                spec = spec,
                initial = initialVector,
                target = targetVector,
                initialVelocity = initialVelocityVector,
                playTimeNanos = playTimeNanos,
                values = valueVector,
                velocities = velocityVector,
            )

            is DurationBasedAnimationSpec -> sampleDurationBased(
                spec = spec,
                initial = initialVector,
                target = targetVector,
                playTimeNanos = playTimeNanos,
                values = valueVector,
                velocities = velocityVector,
            )
        }
        if (
            normalizedSpec is SpringSpec &&
            playTimeNanos >= durationNanos &&
            durationResolution.endReason == AnimationEndReason.Finished
        ) {
            targetVector.copyInto(valueVector)
            velocityVector.fill(0f)
        }
    }

    private fun resolveDuration(): DurationResolution {
        val spec = normalizedSpec
        if (spec is DurationBasedAnimationSpec) {
            if (vectorsEqual(initialVector, targetVector)) {
                return DurationResolution(0L, AnimationEndReason.Finished)
            }
            return DurationResolution(
                durationNanos = durationBasedDurationNanos(spec),
                endReason = AnimationEndReason.Finished,
            )
        }
        spec as SpringSpec
        if (isAtEquilibrium(initialVector, targetVector, initialVelocityVector, thresholdVector)) {
            return DurationResolution(0L, AnimationEndReason.Finished)
        }
        val maxDurationNanos = spec.maxDurationMillis.toLong() * NANOS_PER_MILLISECOND
        var playTimeNanos = SPRING_DURATION_STEP_NANOS
        val candidateValue = FloatArray(vectorSize)
        val candidateVelocity = FloatArray(vectorSize)
        while (playTimeNanos <= maxDurationNanos) {
            sampleSpring(
                spec = spec,
                initial = initialVector,
                target = targetVector,
                initialVelocity = initialVelocityVector,
                playTimeNanos = playTimeNanos,
                values = candidateValue,
                velocities = candidateVelocity,
            )
            candidateValue.requireFinite("sampled value")
            candidateVelocity.requireFinite("sampled velocity")
            if (isAtEquilibrium(candidateValue, targetVector, candidateVelocity, thresholdVector)) {
                return DurationResolution(playTimeNanos, AnimationEndReason.Finished)
            }
            playTimeNanos += SPRING_DURATION_STEP_NANOS
        }
        return DurationResolution(maxDurationNanos, AnimationEndReason.DurationLimitReached)
    }
}

/**
 * Evaluates one exponential decay at explicit monotonic play times.
 *
 * The evaluator owns reusable vectors and is not thread-safe. [targetValue] is the unbounded
 * asymptotic position; bounds are applied only by [AnimatableCore] or [runDecayAnimation].
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param initialValue value at play time zero
 * @param initialVelocity typed velocity at play time zero
 * @param animationSpec target-free exponential decay specification
 * @param converter stable value and velocity converter
 * @throws IllegalArgumentException when converter output, thresholds, or the derived asymptotic
 * target cannot satisfy the finite vector contract
 */
class DecayAnimation<T, V>(
    initialValue: T,
    initialVelocity: AnimationVelocity<V>,
    animationSpec: DecayAnimationSpec,
    private val converter: AnimationConverter<T, V>,
) {
    private val vectorSize = converter.validatedVectorSize()
    private val initialVector = converter.valueVector(initialValue, vectorSize, "initialValue")
    private val initialVelocityVector =
        converter.velocityVector(initialVelocity.valuePerSecond, vectorSize, "initialVelocity")
    private val thresholdVector =
        converter.velocityVector(converter.visibilityThreshold, vectorSize, "visibilityThreshold")
    private val valueVector = FloatArray(vectorSize)
    private val velocityVector = FloatArray(vectorSize)
    private val spec = animationSpec as ExponentialDecaySpec
    private val decayRate = BASE_DECAY_RATE * spec.frictionMultiplier.toDouble()
    private val durationResolution = resolveDuration()

    init {
        converter.validateZeroVelocity(vectorSize)
    }

    /** Returns the deterministic threshold time or physical safety guard in nanoseconds. */
    val durationNanos: Long
        get() = durationResolution.durationNanos

    /** Returns the normal reason produced when [durationNanos] is reached. */
    val terminalEndReason: AnimationEndReason
        get() = durationResolution.endReason

    /** Returns the unbounded asymptotic value approached by this decay. */
    val targetValue: T = run {
        for (index in 0 until vectorSize) {
            valueVector[index] =
                (initialVector[index].toDouble() + initialVelocityVector[index].toDouble() / decayRate).toFloat()
        }
        valueVector.requireFinite("decay target")
        converter.convertFromVector(valueVector)
    }

    internal var lastSampleReachedBound: Boolean = false
        private set

    /**
     * Returns the decay state at [playTimeNanos].
     *
     * Negative time is treated as zero and later time is pinned to [durationNanos]. Normal
     * threshold completion publishes zero retained velocity; a safety-guard result retains the
     * sampled velocity.
     *
     * @param playTimeNanos elapsed play time relative to this decay in nanoseconds
     * @return immutable value, typed velocity, and normalized play time
     * @throws IllegalArgumentException when a derived sample produces a non-finite vector component
     */
    fun stateAt(playTimeNanos: Long): AnimationState<T, V> {
        return stateAt(playTimeNanos, bounds = null)
    }

    internal fun stateAt(
        playTimeNanos: Long,
        bounds: AnimationVectorBounds?,
    ): AnimationState<T, V> {
        val normalizedPlayTime = playTimeNanos.coerceIn(0L, durationNanos)
        sampleVectors(normalizedPlayTime)
        valueVector.requireFinite("sampled value")
        velocityVector.requireFinite("sampled velocity")
        if (
            normalizedPlayTime >= durationNanos &&
            durationResolution.endReason == AnimationEndReason.Finished
        ) {
            velocityVector.fill(0f)
        }
        lastSampleReachedBound = bounds?.clamp(valueVector, velocityVector) == true
        return AnimationState(
            value = converter.convertFromVector(valueVector),
            velocity = AnimationVelocity(converter.convertVelocityFromVector(velocityVector)),
            playTimeNanos = normalizedPlayTime,
        )
    }

    private fun sampleVectors(playTimeNanos: Long) {
        val seconds = playTimeNanos.toDouble() / NANOS_PER_SECOND
        val velocityScale = exp(-decayRate * seconds)
        val positionScale = (1.0 - velocityScale) / decayRate
        for (index in 0 until vectorSize) {
            val initialVelocity = initialVelocityVector[index].toDouble()
            valueVector[index] =
                (initialVector[index].toDouble() + initialVelocity * positionScale).toFloat()
            velocityVector[index] = (initialVelocity * velocityScale).toFloat()
        }
    }

    private fun resolveDuration(): DurationResolution {
        var durationSeconds = 0.0
        for (index in 0 until vectorSize) {
            val speed = abs(initialVelocityVector[index].toDouble())
            val threshold = thresholdVector[index].toDouble() / VELOCITY_THRESHOLD_SECONDS
            if (speed > threshold) {
                durationSeconds = maxOf(durationSeconds, ln(speed / threshold) / decayRate)
            }
        }
        val requiredNanos = ceil(durationSeconds * NANOS_PER_SECOND)
            .coerceAtMost(Long.MAX_VALUE.toDouble())
            .toLong()
        val maxDurationNanos = spec.maxDurationMillis.toLong() * NANOS_PER_MILLISECOND
        return if (requiredNanos <= maxDurationNanos) {
            DurationResolution(requiredNanos, AnimationEndReason.Finished)
        } else {
            DurationResolution(maxDurationNanos, AnimationEndReason.DurationLimitReached)
        }
    }
}

/**
 * Runs one finite target animation and publishes accepted states on [frameClock].
 *
 * The function rejects non-monotonic frame timestamps before publishing their candidate sample.
 * Cancellation always propagates. Bounds are converted once; a crossing sample is clamped before
 * publication and returns [AnimationEndReason.BoundReached] with zero velocity.
 *
 * @sample com.viewcompose.animation.core.samples.runAnimationSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param frameClock monotonic frame source that paces the run
 * @param startValue value captured at mutation start
 * @param endValue requested target value
 * @param animationSpec finite timing or physical specification
 * @param converter stable value and velocity converter
 * @param initialVelocity velocity supplied to physical spring motion; duration specifications
 * ignore it
 * @param lowerBound optional inclusive component-wise lower value
 * @param upperBound optional inclusive component-wise upper value
 * @param onFrame callback invoked on the animation coroutine after each accepted sample
 * @return final retained state and normal terminal reason
 * @throws java.util.concurrent.CancellationException when the caller is cancelled
 * @throws IllegalArgumentException for invalid converter output or inverted bounds
 * @throws IllegalStateException when [frameClock] produces a non-monotonic timestamp
 */
suspend fun <T, V> runAnimation(
    frameClock: MonotonicFrameClock,
    startValue: T,
    endValue: T,
    animationSpec: FiniteAnimationSpec,
    converter: AnimationConverter<T, V>,
    initialVelocity: AnimationVelocity<V> = AnimationVelocity(converter.zeroVelocity),
    lowerBound: T? = null,
    upperBound: T? = null,
    onFrame: (AnimationState<T, V>) -> Unit,
): AnimationResult<T, V> {
    val animation = TargetAnimation(
        initialValue = startValue,
        targetValue = endValue,
        animationSpec = animationSpec,
        converter = converter,
        initialVelocity = initialVelocity,
    )
    val bounds = createAnimationVectorBounds(converter, lowerBound, upperBound)
    return runTargetAnimation(
        frameClock = frameClock,
        animation = animation,
        boundsProvider = { bounds },
        onFrame = onFrame,
    )
}

/**
 * Runs one exponential decay and publishes accepted states on [frameClock].
 *
 * Cancellation, timestamp validation, bounds, and callback behavior match [runAnimation].
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param frameClock monotonic frame source that paces the run
 * @param initialValue value captured at mutation start
 * @param initialVelocity typed velocity that drives the decay
 * @param animationSpec target-free decay specification
 * @param converter stable value and velocity converter
 * @param lowerBound optional inclusive component-wise lower value
 * @param upperBound optional inclusive component-wise upper value
 * @param onFrame callback invoked on the animation coroutine after each accepted sample
 * @return final retained state and normal terminal reason
 * @throws java.util.concurrent.CancellationException when the caller is cancelled
 * @throws IllegalArgumentException for invalid converter output, inverted bounds, or non-finite
 * derived decay state
 * @throws IllegalStateException when [frameClock] produces a non-monotonic timestamp
 */
suspend fun <T, V> runDecayAnimation(
    frameClock: MonotonicFrameClock,
    initialValue: T,
    initialVelocity: AnimationVelocity<V>,
    animationSpec: DecayAnimationSpec,
    converter: AnimationConverter<T, V>,
    lowerBound: T? = null,
    upperBound: T? = null,
    onFrame: (AnimationState<T, V>) -> Unit,
): AnimationResult<T, V> {
    val animation = DecayAnimation(
        initialValue = initialValue,
        initialVelocity = initialVelocity,
        animationSpec = animationSpec,
        converter = converter,
    )
    val bounds = createAnimationVectorBounds(converter, lowerBound, upperBound)
    return runDecay(
        frameClock = frameClock,
        animation = animation,
        boundsProvider = { bounds },
        onFrame = onFrame,
    )
}

internal suspend fun <T, V> runTargetAnimation(
    frameClock: MonotonicFrameClock,
    animation: TargetAnimation<T, V>,
    boundsProvider: () -> AnimationVectorBounds?,
    onFrame: (AnimationState<T, V>) -> Unit,
): AnimationResult<T, V> {
    coroutineContext.ensureActive()
    if (animation.durationNanos == 0L) {
        val state = animation.stateAt(0L, boundsProvider())
        onFrame(state)
        return AnimationResult(
            endState = state,
            endReason = if (animation.lastSampleReachedBound) {
                AnimationEndReason.BoundReached
            } else {
                animation.terminalEndReason
            },
        )
    }
    val startNanos = frameClock.awaitFrameNanos()
    var previousFrameNanos = startNanos
    while (true) {
        coroutineContext.ensureActive()
        val frameNanos = frameClock.awaitFrameNanos()
        check(frameNanos > previousFrameNanos) {
            "Animation frame timestamps must increase monotonically."
        }
        previousFrameNanos = frameNanos
        val playTimeNanos = elapsedNanos(startNanos, frameNanos)
        val state = animation.stateAt(playTimeNanos, boundsProvider())
        onFrame(state)
        if (animation.lastSampleReachedBound) {
            return AnimationResult(state, AnimationEndReason.BoundReached)
        }
        if (playTimeNanos >= animation.durationNanos) {
            return AnimationResult(state, animation.terminalEndReason)
        }
    }
}

internal suspend fun <T, V> runDecay(
    frameClock: MonotonicFrameClock,
    animation: DecayAnimation<T, V>,
    boundsProvider: () -> AnimationVectorBounds?,
    onFrame: (AnimationState<T, V>) -> Unit,
): AnimationResult<T, V> {
    coroutineContext.ensureActive()
    if (animation.durationNanos == 0L) {
        val state = animation.stateAt(0L, boundsProvider())
        onFrame(state)
        return AnimationResult(
            endState = state,
            endReason = if (animation.lastSampleReachedBound) {
                AnimationEndReason.BoundReached
            } else {
                animation.terminalEndReason
            },
        )
    }
    val startNanos = frameClock.awaitFrameNanos()
    var previousFrameNanos = startNanos
    while (true) {
        coroutineContext.ensureActive()
        val frameNanos = frameClock.awaitFrameNanos()
        check(frameNanos > previousFrameNanos) {
            "Animation frame timestamps must increase monotonically."
        }
        previousFrameNanos = frameNanos
        val playTimeNanos = elapsedNanos(startNanos, frameNanos)
        val state = animation.stateAt(playTimeNanos, boundsProvider())
        onFrame(state)
        if (animation.lastSampleReachedBound) {
            return AnimationResult(state, AnimationEndReason.BoundReached)
        }
        if (playTimeNanos >= animation.durationNanos) {
            return AnimationResult(state, animation.terminalEndReason)
        }
    }
}

internal class AnimationVectorBounds(
    val lower: FloatArray?,
    val upper: FloatArray?,
) {
    fun clamp(
        values: FloatArray,
        velocities: FloatArray,
    ): Boolean {
        var reached = false
        for (index in values.indices) {
            val lowerValue = lower?.get(index)
            val upperValue = upper?.get(index)
            when {
                lowerValue != null && values[index] < lowerValue -> {
                    values[index] = lowerValue
                    reached = true
                }

                upperValue != null && values[index] > upperValue -> {
                    values[index] = upperValue
                    reached = true
                }
            }
        }
        if (reached) {
            velocities.fill(0f)
        }
        return reached
    }
}

internal fun <T, V> createAnimationVectorBounds(
    converter: AnimationConverter<T, V>,
    lowerBound: T?,
    upperBound: T?,
): AnimationVectorBounds? {
    if (lowerBound == null && upperBound == null) return null
    val vectorSize = converter.validatedVectorSize()
    val lower = lowerBound?.let { converter.valueVector(it, vectorSize, "lowerBound") }
    val upper = upperBound?.let { converter.valueVector(it, vectorSize, "upperBound") }
    for (index in 0 until vectorSize) {
        val lowerValue = lower?.get(index)
        val upperValue = upper?.get(index)
        require(lowerValue == null || upperValue == null || lowerValue <= upperValue) {
            "Animation lowerBound must not exceed upperBound at component $index."
        }
    }
    return AnimationVectorBounds(lower = lower, upper = upper)
}

internal fun <T, V> clampAnimationState(
    converter: AnimationConverter<T, V>,
    value: T,
    velocity: AnimationVelocity<V>,
    bounds: AnimationVectorBounds?,
    playTimeNanos: Long,
): AnimationState<T, V> {
    val vectorSize = converter.validatedVectorSize()
    val values = converter.valueVector(value, vectorSize, "value")
    val velocities = converter.velocityVector(velocity.valuePerSecond, vectorSize, "velocity")
    if (bounds == null) {
        return AnimationState(value, velocity, playTimeNanos)
    }
    bounds.clamp(values, velocities)
    return AnimationState(
        value = converter.convertFromVector(values),
        velocity = AnimationVelocity(converter.convertVelocityFromVector(velocities)),
        playTimeNanos = playTimeNanos,
    )
}

private data class DurationResolution(
    val durationNanos: Long,
    val endReason: AnimationEndReason,
)

private fun FiniteAnimationSpec.normalized(): FiniteAnimationSpec {
    return when (this) {
        is KeyframesSpec -> copy(keyframes = keyframes.sortedBy { it.timeMillis })
        is RepeatableSpec -> copy(animation = animation.normalizedDurationBased())
        else -> this
    }
}

private fun DurationBasedAnimationSpec.normalizedDurationBased(): DurationBasedAnimationSpec {
    return when (this) {
        is KeyframesSpec -> copy(keyframes = keyframes.sortedBy { it.timeMillis })
        is RepeatableSpec -> copy(animation = animation.normalizedDurationBased())
        else -> this
    }
}

private fun durationBasedDurationNanos(spec: DurationBasedAnimationSpec): Long {
    return when (spec) {
        is TweenSpec -> millisToNanos(spec.delayMillis.coerceAtLeast(0))
            .saturatingAdd(millisToNanos(spec.durationMillis.coerceAtLeast(1)))

        is KeyframesSpec -> millisToNanos(spec.durationMillis.coerceAtLeast(1))
        SnapSpec -> 1L
        is RepeatableSpec -> durationBasedDurationNanos(spec.animation)
            .saturatingMultiply(spec.iterations.coerceAtLeast(0).toLong())
    }
}

private fun sampleDurationBased(
    spec: DurationBasedAnimationSpec,
    initial: FloatArray,
    target: FloatArray,
    playTimeNanos: Long,
    values: FloatArray,
    velocities: FloatArray,
) {
    when (spec) {
        is TweenSpec -> sampleTween(spec, initial, target, playTimeNanos, values, velocities)
        is KeyframesSpec -> sampleKeyframes(spec, initial, target, playTimeNanos, values, velocities)
        SnapSpec -> {
            target.copyInto(values)
            velocities.fill(0f)
        }

        is RepeatableSpec -> sampleRepeatable(
            spec,
            initial,
            target,
            playTimeNanos,
            values,
            velocities,
        )
    }
}

private fun sampleTween(
    spec: TweenSpec,
    initial: FloatArray,
    target: FloatArray,
    playTimeNanos: Long,
    values: FloatArray,
    velocities: FloatArray,
) {
    val delayNanos = millisToNanos(spec.delayMillis.coerceAtLeast(0))
    val durationNanos = millisToNanos(spec.durationMillis.coerceAtLeast(1))
    val delayed = playTimeNanos - delayNanos
    if (delayed <= 0L) {
        initial.copyInto(values)
        velocities.fill(0f)
        return
    }
    if (delayed >= durationNanos) {
        target.copyInto(values)
        velocities.fill(0f)
        return
    }
    val fraction = delayed.toDouble() / durationNanos.toDouble()
    val eased = spec.easing.transform(fraction.toFloat()).coerceIn(0f, 1f)
    val derivative = easingDerivative(spec.easing, fraction.toFloat())
    val durationSeconds = durationNanos.toDouble() / NANOS_PER_SECOND
    for (index in values.indices) {
        val delta = target[index] - initial[index]
        values[index] = initial[index] + delta * eased
        velocities[index] = (delta.toDouble() * derivative / durationSeconds).toFloat()
    }
}

private fun sampleKeyframes(
    spec: KeyframesSpec,
    initial: FloatArray,
    target: FloatArray,
    playTimeNanos: Long,
    values: FloatArray,
    velocities: FloatArray,
) {
    val durationMillis = spec.durationMillis.coerceAtLeast(1)
    val durationNanos = millisToNanos(durationMillis)
    if (playTimeNanos >= durationNanos) {
        target.copyInto(values)
        velocities.fill(0f)
        return
    }
    val playMillis = (playTimeNanos.coerceAtLeast(0L).toDouble() / NANOS_PER_MILLISECOND).toFloat()
    val before = spec.keyframes.lastOrNull { it.timeMillis.toFloat() <= playMillis }
    val after = spec.keyframes.firstOrNull { it.timeMillis.toFloat() >= playMillis }
    val beforeTime = before?.timeMillis ?: 0
    val beforeFraction = before?.valueFraction ?: 0f
    val afterTime = after?.timeMillis ?: durationMillis
    val afterFraction = after?.valueFraction ?: 1f
    val fraction: Float
    val derivativePerSecond: Float
    if (beforeTime == afterTime) {
        fraction = beforeFraction.coerceIn(0f, 1f)
        derivativePerSecond = 0f
    } else {
        val local = ((playMillis - beforeTime.toFloat()) / (afterTime - beforeTime).toFloat())
            .coerceIn(0f, 1f)
        fraction = lerp(beforeFraction, afterFraction, local).coerceIn(0f, 1f)
        derivativePerSecond =
            (afterFraction - beforeFraction) / ((afterTime - beforeTime).toFloat() / 1_000f)
    }
    for (index in values.indices) {
        val delta = target[index] - initial[index]
        values[index] = initial[index] + delta * fraction
        velocities[index] = delta * derivativePerSecond
    }
}

private fun sampleRepeatable(
    spec: RepeatableSpec,
    initial: FloatArray,
    target: FloatArray,
    playTimeNanos: Long,
    values: FloatArray,
    velocities: FloatArray,
) {
    val iterations = spec.iterations.coerceAtLeast(0)
    if (iterations == 0) {
        initial.copyInto(values)
        velocities.fill(0f)
        return
    }
    val cycleDuration = durationBasedDurationNanos(spec.animation).coerceAtLeast(1L)
    val totalDuration = cycleDuration.saturatingMultiply(iterations.toLong())
    val clamped = playTimeNanos.coerceAtLeast(0L)
    if (clamped >= totalDuration) {
        val returnsToStart = spec.repeatMode == RepeatMode.Reverse && iterations % 2 == 0
        (if (returnsToStart) initial else target).copyInto(values)
        velocities.fill(0f)
        return
    }
    val cycleIndex = clamped / cycleDuration
    val cyclePlayTime = clamped % cycleDuration
    val reverse = spec.repeatMode == RepeatMode.Reverse && cycleIndex % 2L == 1L
    sampleDurationBased(
        spec = spec.animation,
        initial = if (reverse) target else initial,
        target = if (reverse) initial else target,
        playTimeNanos = cyclePlayTime,
        values = values,
        velocities = velocities,
    )
}

private fun sampleSpring(
    spec: SpringSpec,
    initial: FloatArray,
    target: FloatArray,
    initialVelocity: FloatArray,
    playTimeNanos: Long,
    values: FloatArray,
    velocities: FloatArray,
) {
    val seconds = playTimeNanos.coerceAtLeast(0L).toDouble() / NANOS_PER_SECOND
    val dampingRatio = spec.dampingRatio.toDouble()
    val naturalFrequency = sqrt(spec.stiffness.toDouble())
    for (index in values.indices) {
        val displacement = initial[index].toDouble() - target[index].toDouble()
        val initialComponentVelocity = initialVelocity[index].toDouble()
        val position: Double
        val velocity: Double
        when {
            dampingRatio < 1.0 - CRITICAL_DAMPING_EPSILON -> {
                val dampedFrequency = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
                val coefficient =
                    (initialComponentVelocity + dampingRatio * naturalFrequency * displacement) /
                        dampedFrequency
                val envelope = exp(-dampingRatio * naturalFrequency * seconds)
                val cosine = cos(dampedFrequency * seconds)
                val sine = sin(dampedFrequency * seconds)
                val base = displacement * cosine + coefficient * sine
                position = envelope * base
                velocity = envelope * (
                    -dampingRatio * naturalFrequency * base +
                        (-displacement * dampedFrequency * sine +
                            coefficient * dampedFrequency * cosine)
                    )
            }

            dampingRatio > 1.0 + CRITICAL_DAMPING_EPSILON -> {
                val root = sqrt(dampingRatio * dampingRatio - 1.0)
                val firstRate = -naturalFrequency * (dampingRatio - root)
                val secondRate = -naturalFrequency * (dampingRatio + root)
                val firstCoefficient =
                    (initialComponentVelocity - secondRate * displacement) /
                        (firstRate - secondRate)
                val secondCoefficient = displacement - firstCoefficient
                val firstTerm = firstCoefficient * exp(firstRate * seconds)
                val secondTerm = secondCoefficient * exp(secondRate * seconds)
                position = firstTerm + secondTerm
                velocity = firstRate * firstTerm + secondRate * secondTerm
            }

            else -> {
                val coefficient = initialComponentVelocity + naturalFrequency * displacement
                val envelope = exp(-naturalFrequency * seconds)
                position = envelope * (displacement + coefficient * seconds)
                velocity = envelope * (
                    initialComponentVelocity - naturalFrequency * coefficient * seconds
                    )
            }
        }
        values[index] = (target[index].toDouble() + position).toFloat()
        velocities[index] = velocity.toFloat()
    }
}

private fun isAtEquilibrium(
    value: FloatArray,
    target: FloatArray,
    velocity: FloatArray,
    threshold: FloatArray,
): Boolean {
    for (index in value.indices) {
        if (abs(value[index] - target[index]) > threshold[index]) return false
        if (abs(velocity[index]) > threshold[index] / VELOCITY_THRESHOLD_SECONDS) return false
    }
    return true
}

private fun vectorsEqual(first: FloatArray, second: FloatArray): Boolean {
    for (index in first.indices) {
        if (first[index] != second[index]) return false
    }
    return true
}

private fun easingDerivative(easing: Easing, fraction: Float): Double {
    val epsilon = 0.0001f
    val start = (fraction - epsilon).coerceIn(0f, 1f)
    val end = (fraction + epsilon).coerceIn(0f, 1f)
    if (start == end) return 0.0
    val startValue = easing.transform(start).coerceIn(0f, 1f)
    val endValue = easing.transform(end).coerceIn(0f, 1f)
    return (endValue - startValue).toDouble() / (end - start).toDouble()
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

private fun elapsedNanos(startNanos: Long, frameNanos: Long): Long {
    val difference = frameNanos - startNanos
    return if (difference < 0L && frameNanos > startNanos) Long.MAX_VALUE else difference
}

private fun millisToNanos(millis: Int): Long {
    return millis.toLong().saturatingMultiply(NANOS_PER_MILLISECOND)
}

private fun Long.saturatingAdd(other: Long): Long {
    if (this > Long.MAX_VALUE - other) return Long.MAX_VALUE
    return this + other
}

private fun Long.saturatingMultiply(multiplier: Long): Long {
    if (this <= 0L || multiplier <= 0L) return 0L
    if (this > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE
    return this * multiplier
}

internal fun <T, V> AnimationConverter<T, V>.validatedVectorSize(): Int {
    return vectorSize.also { size ->
        require(size > 0) {
            "Animation converter vectorSize must be greater than zero."
        }
    }
}

internal fun <T, V> AnimationConverter<T, V>.valueVector(
    value: T,
    size: Int,
    label: String,
): FloatArray {
    val vector = FloatArray(size) { Float.NaN }
    convertToVector(value, vector)
    vector.requireFinite(label)
    return vector
}

internal fun <T, V> AnimationConverter<T, V>.velocityVector(
    velocity: V,
    size: Int,
    label: String,
): FloatArray {
    val vector = FloatArray(size) { Float.NaN }
    convertVelocityToVector(velocity, vector)
    vector.requireFinite(label)
    if (label == "visibilityThreshold") {
        for (index in vector.indices) {
            require(vector[index] > 0f) {
                "Animation visibilityThreshold component $index must be greater than zero."
            }
        }
    }
    return vector
}

internal fun <T, V> AnimationConverter<T, V>.validateZeroVelocity(size: Int) {
    val vector = FloatArray(size) { Float.NaN }
    convertVelocityToVector(zeroVelocity, vector)
    vector.requireFinite("zeroVelocity")
    for (index in vector.indices) {
        require(vector[index] == 0f) {
            "Animation converter zeroVelocity must convert to zero at component $index."
        }
    }
}

private fun FloatArray.requireFinite(label: String) {
    for (index in indices) {
        require(this[index].isFinite()) {
            "Animation $label component $index must be finite."
        }
    }
}
