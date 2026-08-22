package com.viewcompose.animation

import com.viewcompose.animation.core.AnimatableCore
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationResult
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.DecayAnimationSpec
import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.exponentialDecay
import com.viewcompose.animation.core.spring
import com.viewcompose.runtime.State
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.foundation.LocalMonotonicFrameClock
import com.viewcompose.ui.foundation.remember

/**
 * Owns a composition-observable animated value with last-mutation-wins coroutine semantics.
 *
 * The platform-neutral [AnimatableCore] is the single mutation, velocity, bounds, and cancellation
 * owner. This facade binds that state to a composition frame clock without introducing another
 * solver or mutation loop. A newer mutation cancels the previous caller, and cancellation retains
 * the last atomically published value and velocity.
 *
 * The instance owns no coroutine scope. UI-facing calls should run in a structured composition or
 * lifecycle scope. [animateTo] and [animateDecay] require the constructor's [defaultFrameClock] or
 * a clock installed by [rememberAnimatable].
 *
 * @sample com.viewcompose.animation.samples.animatableSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param initialValue value exposed before the first mutation
 * @param converter stable converter used for the lifetime of this instance
 * @param defaultFrameClock optional clock for instances constructed outside composition
 * @throws IllegalArgumentException when [initialValue] or [converter] violates the finite vector
 * contract
 */
class Animatable<T, V>(
    initialValue: T,
    converter: AnimationConverter<T, V>,
    defaultFrameClock: MonotonicFrameClock? = null,
) {
    private val core = AnimatableCore(
        initialValue = initialValue,
        converter = converter,
    )
    private val observableState = object : State<T> {
        override val value: T
            get() = core.value
    }
    private var boundFrameClock: MonotonicFrameClock? = defaultFrameClock

    /** Returns the live value from the latest accepted state. */
    val value: T
        get() = core.value

    /** Returns the live typed velocity from the same atomic state as [value]. */
    val velocity: AnimationVelocity<V>
        get() = core.velocity

    /** Returns the active target, or [value] while idle. */
    val targetValue: T
        get() = core.targetValue

    /** Returns whether the newest mutation is still active. */
    val isRunning: Boolean
        get() = core.isRunning

    /** Returns the stable observable state object backing [value]. */
    val asState: State<T>
        get() = observableState

    /**
     * Publishes [targetValue] immediately and resets retained velocity to zero.
     *
     * @param targetValue value to retain after the mutation
     * @throws IllegalArgumentException when converter output is invalid; the currently active
     * mutation remains authoritative
     */
    suspend fun snapTo(targetValue: T) {
        core.snapTo(targetValue)
    }

    /** Cancels an older mutation, preserves its latest value, and resets velocity to zero. */
    suspend fun stop() {
        core.stop()
    }

    /**
     * Animates from the latest accepted value to [targetValue].
     *
     * Physical springs continue [initialVelocity]; `null` captures the retained velocity atomically
     * with the start value. Duration-based specifications ignore it.
     *
     * @param targetValue requested target value
     * @param animationSpec finite timing or physical specification
     * @param initialVelocity optional typed velocity supplied at mutation start; `null` continues
     * the retained velocity from the same mutation snapshot as the start value
     * @return final retained state and normal terminal reason
     * @throws IllegalArgumentException if no frame clock is bound or an input contract is invalid;
     * an invalid request does not cancel the currently active mutation
     * @throws java.util.concurrent.CancellationException when replaced or externally cancelled
     */
    suspend fun animateTo(
        targetValue: T,
        animationSpec: FiniteAnimationSpec = spring(),
        initialVelocity: AnimationVelocity<V>? = null,
    ): AnimationResult<T, V> {
        return core.animateTo(
            targetValue = targetValue,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity,
            frameClock = requireFrameClock(),
        )
    }

    /**
     * Continues motion from [value] using [initialVelocity] and a target-free decay.
     *
     * @param initialVelocity typed velocity supplied by a gesture or preceding animation
     * @param animationSpec target-free decay policy
     * @return final retained state and normal terminal reason
     * @throws IllegalArgumentException if no frame clock is bound or an input contract is invalid;
     * an invalid request does not cancel the currently active mutation
     * @throws java.util.concurrent.CancellationException when replaced or externally cancelled
     */
    suspend fun animateDecay(
        initialVelocity: AnimationVelocity<V>,
        animationSpec: DecayAnimationSpec = exponentialDecay(),
    ): AnimationResult<T, V> {
        return core.animateDecay(
            initialVelocity = initialVelocity,
            animationSpec = animationSpec,
            frameClock = requireFrameClock(),
        )
    }

    /**
     * Replaces the inclusive component-wise bounds for subsequent accepted samples.
     *
     * @param lowerBound optional inclusive lower value
     * @param upperBound optional inclusive upper value
     * @throws IllegalArgumentException if converter output is invalid or a lower component exceeds
     * its upper component
     */
    fun updateBounds(
        lowerBound: T? = null,
        upperBound: T? = null,
    ) {
        core.updateBounds(lowerBound = lowerBound, upperBound = upperBound)
    }

    internal fun bindFrameClock(frameClock: MonotonicFrameClock) {
        boundFrameClock = frameClock
    }

    private fun requireFrameClock(): MonotonicFrameClock {
        return requireNotNull(boundFrameClock) {
            "Animatable has no frame clock. Use rememberAnimatable(...) or pass a clock in constructor."
        }
    }
}

/**
 * Remembers an [Animatable] and binds it to the current composition frame clock.
 *
 * [initialValue] is used only when this call position creates an instance. Changing [converter]
 * creates a new instance; changing only [initialValue] does not reset retained state.
 *
 * @sample com.viewcompose.animation.samples.rememberAnimatableSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param initialValue value for the first instance created at this call position
 * @param converter converter that participates in remembered-instance identity
 * @return composition-owned animated value holder
 * @throws IllegalArgumentException when [initialValue] or [converter] violates the finite vector
 * contract while creating an instance
 */
fun <T, V> rememberAnimatable(
    initialValue: T,
    converter: AnimationConverter<T, V>,
): Animatable<T, V> {
    val frameClock = LocalMonotonicFrameClock.current
    val animatable = remember(converter) {
        Animatable(
            initialValue = initialValue,
            converter = converter,
            defaultFrameClock = frameClock,
        )
    }
    animatable.bindFrameClock(frameClock)
    return animatable
}
