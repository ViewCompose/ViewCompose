package com.viewcompose.animation.core

/**
 * Defines how the animation engine maps elapsed time to progress between two values.
 *
 * Specifications are immutable descriptions. They do not own a clock or mutable animation state;
 * [runAnimation] and [TargetAnimation] interpret them. Consumers normally construct a known
 * subtype through the factory functions in this package rather than implementing the sealed
 * hierarchy.
 *
 * Durations and delays are normalized by the engine when sampled. See each subtype for its exact
 * behavior.
 */
sealed interface AnimationSpec

/**
 * Identifies a target animation with a deterministic terminal sample or safety guard.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 */
sealed interface FiniteAnimationSpec : AnimationSpec

/**
 * Identifies a finite specification whose duration is known without values or velocity.
 *
 * Duration-based specifications can be repeated. Physical springs deliberately do not implement
 * this interface because their equilibrium time depends on endpoints, velocity, and thresholds.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 */
sealed interface DurationBasedAnimationSpec : FiniteAnimationSpec

/**
 * Maps a fixed-duration interval through an [easing] curve after an optional delay.
 *
 * The engine treats a negative [delayMillis] as zero and a non-positive [durationMillis] as one
 * millisecond. The stored properties retain the values supplied by the caller.
 *
 * @property durationMillis requested interpolation duration in milliseconds
 * @property delayMillis requested time in milliseconds to hold the start value before interpolation
 * @property easing curve that maps normalized elapsed time to normalized visual progress
 */
data class TweenSpec(
    val durationMillis: Int = 300,
    val delayMillis: Int = 0,
    val easing: Easing = EasingDefaults.FastOutSlowIn,
) : DurationBasedAnimationSpec

/**
 * Solves normalized-mass physical spring motion until every vector component reaches equilibrium.
 *
 * The engine evaluates `x'' + 2ζω₀x' + ω₀²(x - target) = 0` analytically with
 * `ω₀ = sqrt(stiffness)`. The solve supports overshoot and initial velocity and has no nominal
 * duration. [maxDurationMillis] is only a safety guard; reaching it returns
 * [AnimationEndReason.DurationLimitReached] without snapping to the target.
 *
 * @property dampingRatio dimensionless damping ratio, finite and at least zero
 * @property stiffness normalized-mass spring stiffness in `s⁻²`, finite and greater than zero
 * @property maxDurationMillis safety guard in milliseconds, from `1` through `60_000`
 * @throws IllegalArgumentException if any parameter is outside its documented range
 */
data class SpringSpec(
    val dampingRatio: Float = 0.8f,
    val stiffness: Float = 250f,
    val maxDurationMillis: Int = 10_000,
) : FiniteAnimationSpec {
    init {
        require(dampingRatio.isFinite() && dampingRatio >= 0f) {
            "Spring dampingRatio must be finite and at least zero."
        }
        require(stiffness.isFinite() && stiffness > 0f) {
            "Spring stiffness must be finite and greater than zero."
        }
        require(maxDurationMillis in 1..60_000) {
            "Spring maxDurationMillis must be between 1 and 60000."
        }
    }
}

/**
 * Defines one progress checkpoint in a [KeyframesSpec].
 *
 * Sampling orders checkpoints by [timeMillis], interpolates linearly between adjacent entries, and
 * clamps the resulting [valueFraction] to `0f..1f`. Times outside the animation interval are not
 * rejected; only checkpoints surrounding the sampled time participate.
 *
 * @property timeMillis checkpoint time relative to the start of the animation, in milliseconds
 * @property valueFraction target progress between the animation's start and end values
 */
data class Keyframe(
    val timeMillis: Int,
    val valueFraction: Float,
)

/**
 * Interpolates progress linearly between timestamped [keyframes].
 *
 * An empty list falls back to linear progress. Missing endpoints are synthesized as `0f` at zero
 * milliseconds and `1f` at [durationMillis]. A non-positive duration is sampled as one
 * millisecond. The [keyframes] factory returns a time-sorted list; direct construction is also
 * supported and the engine sorts the list once when constructing an evaluator.
 *
 * @property durationMillis requested animation duration in milliseconds
 * @property keyframes progress checkpoints used to shape the interval
 */
data class KeyframesSpec(
    val durationMillis: Int,
    val keyframes: List<Keyframe>,
) : DurationBasedAnimationSpec

/**
 * Selects the target value immediately without interpolation.
 *
 * The sampler always returns the end value. Duration queries report a minimal one-nanosecond finite
 * interval so the spec can participate safely in repeat calculations.
 */
data object SnapSpec : DurationBasedAnimationSpec

/** Defines how a repeated animation chooses the direction of each cycle. */
enum class RepeatMode {
    /** Starts every cycle at the original start value and ends at the original target value. */
    Restart,

    /** Alternates start-to-target and target-to-start cycles. */
    Reverse,
}

/**
 * Repeats a finite [animation] for a fixed number of [iterations].
 *
 * Zero or negative iterations produce a zero-duration animation that remains at its start value.
 * In [RepeatMode.Reverse], an even iteration count ends at the start value and an odd count ends at
 * the target value. Only duration-based specifications are accepted, so a physical spring or an
 * [InfiniteRepeatableSpec] cannot be wrapped.
 *
 * @property iterations number of cycles requested; values below zero are treated as zero
 * @property animation specification sampled during each cycle
 * @property repeatMode direction policy applied between cycles
 */
data class RepeatableSpec(
    val iterations: Int,
    val animation: DurationBasedAnimationSpec,
    val repeatMode: RepeatMode = RepeatMode.Restart,
) : DurationBasedAnimationSpec

/**
 * Repeats [animation] until the driving coroutine is cancelled.
 *
 * Duration queries return [Long.MAX_VALUE] as an infinity sentinel. Even when [animation] is
 * [SnapSpec], [runAnimation] awaits the frame clock once per sample and does not busy-loop.
 *
 * @property animation specification sampled during each cycle
 * @property repeatMode direction policy applied between cycles
 */
data class InfiniteRepeatableSpec(
    val animation: DurationBasedAnimationSpec,
    val repeatMode: RepeatMode = RepeatMode.Restart,
) : AnimationSpec

/**
 * Creates a fixed-duration easing specification.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 *
 * @param durationMillis requested interpolation duration in milliseconds; non-positive values are
 * normalized to one millisecond by the engine
 * @param delayMillis requested start delay in milliseconds; negative values are treated as zero
 * @param easing curve used to transform normalized time progress
 * @return an immutable tween specification
 */
fun tween(
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    easing: Easing = EasingDefaults.FastOutSlowIn,
): TweenSpec = TweenSpec(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = easing,
)

/**
 * Creates a normalized-mass physical spring specification.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 *
 * @param dampingRatio dimensionless damping ratio, finite and at least zero
 * @param stiffness normalized-mass stiffness in `s⁻²`, finite and greater than zero
 * @param maxDurationMillis safety guard in milliseconds, from `1` through `60_000`; this is not a
 * nominal duration
 * @return an immutable physical spring specification
 * @throws IllegalArgumentException if any parameter is outside its documented range
 */
fun spring(
    dampingRatio: Float = 0.8f,
    stiffness: Float = 250f,
    maxDurationMillis: Int = 10_000,
): SpringSpec = SpringSpec(
    dampingRatio = dampingRatio,
    stiffness = stiffness,
    maxDurationMillis = maxDurationMillis,
)

/**
 * Creates a keyframe specification with checkpoints sorted by timestamp.
 *
 * Duplicate timestamps are retained in their stable input order. The sampler clamps checkpoint
 * progress, not the objects returned by this function.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 *
 * @param durationMillis requested animation duration in milliseconds
 * @param keyframes checkpoints to sort and store in the specification
 * @return an immutable keyframe specification whose checkpoint list is ordered by [Keyframe.timeMillis]
 */
fun keyframes(
    durationMillis: Int,
    vararg keyframes: Keyframe,
): KeyframesSpec {
    return KeyframesSpec(
        durationMillis = durationMillis,
        keyframes = keyframes.sortedBy { it.timeMillis },
    )
}

/**
 * Creates one timestamped progress checkpoint for [keyframes].
 *
 * @param timeMillis checkpoint time relative to the animation start, in milliseconds
 * @param valueFraction target progress between the start and end values
 * @return an immutable checkpoint; clamping occurs only when the engine samples it
 */
fun keyframe(
    timeMillis: Int,
    valueFraction: Float,
): Keyframe = Keyframe(
    timeMillis = timeMillis,
    valueFraction = valueFraction,
)

/**
 * Returns the shared specification that selects the target value immediately.
 *
 * @return the singleton [SnapSpec]
 */
fun snap(): SnapSpec = SnapSpec

/**
 * Creates a finite repeated animation.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 *
 * @param iterations number of cycles; zero and negative values keep the start value
 * @param animation finite specification to sample during each cycle
 * @param repeatMode direction policy between cycles
 * @return an immutable finite repeat specification
 */
fun repeatable(
    iterations: Int,
    animation: DurationBasedAnimationSpec,
    repeatMode: RepeatMode = RepeatMode.Restart,
): RepeatableSpec = RepeatableSpec(
    iterations = iterations,
    animation = animation,
    repeatMode = repeatMode,
)

/**
 * Creates an animation that repeats until its driving coroutine is cancelled.
 *
 * @sample com.viewcompose.animation.core.samples.animationSpecificationsSample
 *
 * @param animation specification to sample during every cycle
 * @param repeatMode direction policy between cycles
 * @return an immutable infinite repeat specification
 */
fun infiniteRepeatable(
    animation: DurationBasedAnimationSpec,
    repeatMode: RepeatMode = RepeatMode.Restart,
): InfiniteRepeatableSpec = InfiniteRepeatableSpec(
    animation = animation,
    repeatMode = repeatMode,
)
