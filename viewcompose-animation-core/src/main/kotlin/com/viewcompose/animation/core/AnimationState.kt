package com.viewcompose.animation.core

/**
 * Stores a typed velocity in domain units per second.
 *
 * [V] is the converter's velocity domain and may differ from the animated value type. Instances
 * are immutable and own no clock or mutable vector storage.
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @property valuePerSecond velocity value in the converter-defined domain
 * @param V immutable velocity domain
 */
data class AnimationVelocity<V>(
    val valuePerSecond: V,
)

/**
 * Captures one accepted animation sample.
 *
 * The state is immutable. [playTimeNanos] is relative to the current animation mutation and never
 * negative. Values and velocities are reconstructed by the active [AnimationConverter].
 *
 * @sample com.viewcompose.animation.core.samples.distinctIntegerVelocityDomainSample
 *
 * @property value accepted animated value
 * @property velocity typed velocity at the same sample
 * @property playTimeNanos elapsed monotonic play time in nanoseconds
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 */
data class AnimationState<T, V>(
    val value: T,
    val velocity: AnimationVelocity<V>,
    val playTimeNanos: Long,
)

/**
 * Identifies why an animation mutation returned normally.
 *
 * Coroutine interruption is deliberately absent: replacement and external cancellation throw
 * [java.util.concurrent.CancellationException] instead of returning a normal result.
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 */
enum class AnimationEndReason {
    /** The target reached equilibrium or a decay's velocity reached its visibility threshold. */
    Finished,

    /** At least one value component reached its configured lower or upper bound. */
    BoundReached,

    /** A physical solve reached its safety guard before satisfying its terminal threshold. */
    DurationLimitReached,
}

/**
 * Returns the final accepted state and normal terminal reason of an animation mutation.
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @property endState immutable state retained by the animation owner
 * @property endReason reason the mutation returned without cancellation or failure
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 */
data class AnimationResult<T, V>(
    val endState: AnimationState<T, V>,
    val endReason: AnimationEndReason,
)
