package com.viewcompose.animation.core

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.frame.MonotonicFrameClock
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

/**
 * Owns one observable animated value with last-mutation-wins coroutine semantics.
 *
 * [animateTo], [animateDecay], [snapTo], and [stop] are mutually exclusive mutations. A newer
 * mutation cancels the older coroutine job, and a cancelled call returns no normal result. Value
 * and velocity are published atomically, stale frames are rejected by mutation identity, and
 * cancellation retains the last accepted state.
 *
 * Bounds are inclusive converter-domain values. [updateBounds] converts and validates them once.
 * While idle it clamps immediately; while running the active mutation observes the replacement on
 * its next frame. No crossing sample is published outside the bounds.
 *
 * The instance owns no coroutine scope or frame clock. Callers supply structured ownership and a
 * [MonotonicFrameClock] to each animation.
 *
 * @sample com.viewcompose.animation.core.samples.animatableCoreSample
 *
 * @param T immutable animated-value domain
 * @param V immutable velocity domain
 * @param initialValue value exposed before the first mutation
 * @param converter stable converter used for the lifetime of this instance
 * @throws IllegalArgumentException when [initialValue] or [converter] violates the finite vector
 * contract
 */
class AnimatableCore<T, V>(
    initialValue: T,
    private val converter: AnimationConverter<T, V>,
) {
    private val internalState: MutableState<T> = mutableStateOf(validateInitialValue(initialValue))
    private val velocityState: MutableState<AnimationVelocity<V>> =
        mutableStateOf(AnimationVelocity(converter.zeroVelocity))
    private val targetState: MutableState<T> = mutableStateOf(internalState.value)
    private val runningState: MutableState<Boolean> = mutableStateOf(false)
    private val mutationLock = Any()

    private var bounds: AnimationVectorBounds? = null
    private var nextMutationId: Long = 0L
    private var activeMutation: Mutation<T>? = null

    private fun validateInitialValue(value: T): T {
        val converterVectorSize = converter.validatedVectorSize()
        converter.valueVector(value, converterVectorSize, "initialValue")
        converter.validateZeroVelocity(converterVectorSize)
        converter.velocityVector(
            converter.visibilityThreshold,
            converterVectorSize,
            "visibilityThreshold",
        )
        return value
    }

    /** Returns the live value from the latest accepted sample. */
    val value: T
        get() = internalState.value

    /** Returns the live typed velocity from the same atomic sample as [value]. */
    val velocity: AnimationVelocity<V>
        get() = velocityState.value

    /** Returns the active target, or [value] while idle. */
    val targetValue: T
        get() = targetState.value

    /** Returns whether the newest mutation is still active. */
    val isRunning: Boolean
        get() = runningState.value

    /**
     * Publishes [targetValue] immediately and resets retained velocity to zero.
     *
     * A concurrent older mutation is cancelled. The call requires no frame clock.
     *
     * @param targetValue value to retain after the mutation
     * @throws IllegalArgumentException when converter output is invalid; the currently active
     * mutation remains authoritative
     */
    suspend fun snapTo(targetValue: T) {
        commitInstantMutation {
            clampAnimationState(
                converter = converter,
                value = targetValue,
                velocity = AnimationVelocity(converter.zeroVelocity),
                bounds = bounds,
                playTimeNanos = 0L,
            )
        }
    }

    /**
     * Cancels an older mutation, preserves its latest value, and resets velocity to zero.
     *
     * The call is idempotent while idle and publishes no intermediate running state.
     */
    suspend fun stop() {
        commitInstantMutation {
            AnimationState(
                value = internalState.value,
                velocity = AnimationVelocity(converter.zeroVelocity),
                playTimeNanos = 0L,
            )
        }
    }

    /**
     * Animates from the latest accepted value to [targetValue].
     *
     * Physical springs consume [initialVelocity]. Duration-based specifications ignore it. The
     * default `null` captures this owner's retained velocity atomically with the start value,
     * including after an interrupted physical mutation. Successful target completion publishes the
     * exact target and zero velocity.
     *
     * @param targetValue requested target value
     * @param animationSpec finite timing or physical specification
     * @param initialVelocity optional typed velocity supplied at mutation start; `null` captures
     * the retained velocity in the same mutation snapshot as the start value
     * @param frameClock monotonic frame source that paces samples
     * @return final retained state and normal terminal reason
     * @throws IllegalArgumentException when the specification, converter output, or target is
     * invalid; the currently active mutation remains authoritative
     * @throws java.util.concurrent.CancellationException when replaced or externally cancelled
     */
    suspend fun animateTo(
        targetValue: T,
        animationSpec: FiniteAnimationSpec = spring(),
        initialVelocity: AnimationVelocity<V>? = null,
        frameClock: MonotonicFrameClock,
    ): AnimationResult<T, V> {
        lateinit var animation: TargetAnimation<T, V>
        val mutation = beginMutation { startValue, retainedVelocity ->
            animation = TargetAnimation(
                initialValue = startValue,
                targetValue = targetValue,
                animationSpec = animationSpec,
                converter = converter,
                initialVelocity = initialVelocity ?: retainedVelocity,
            )
            targetValue
        }
        try {
            currentCoroutineContext().ensureActive()
            val result = runTargetAnimation(
                frameClock = frameClock,
                animation = animation,
                boundsProvider = ::currentBounds,
            ) { state ->
                publishState(mutation.id, state)
            }
            currentCoroutineContext().ensureActive()
            return result
        } finally {
            endMutation(mutation.id)
        }
    }

    /**
     * Continues from [value] with [initialVelocity] under [animationSpec].
     *
     * The unbounded asymptotic value becomes [targetValue] while running. Threshold completion,
     * bounds, safety-guard termination, replacement, and external cancellation follow [animateTo].
     *
     * @param initialVelocity typed velocity supplied by a gesture or preceding animation
     * @param animationSpec target-free decay policy
     * @param frameClock monotonic frame source that paces samples
     * @return final retained state and normal terminal reason
     * @throws IllegalArgumentException when the specification, converter output, or initial
     * velocity is invalid; the currently active mutation remains authoritative
     * @throws java.util.concurrent.CancellationException when replaced or externally cancelled
     */
    suspend fun animateDecay(
        initialVelocity: AnimationVelocity<V>,
        animationSpec: DecayAnimationSpec = exponentialDecay(),
        frameClock: MonotonicFrameClock,
    ): AnimationResult<T, V> {
        lateinit var animation: DecayAnimation<T, V>
        val mutation = beginMutation { startValue, _ ->
            animation = DecayAnimation(
                initialValue = startValue,
                initialVelocity = initialVelocity,
                animationSpec = animationSpec,
                converter = converter,
            )
            animation.targetValue
        }
        try {
            currentCoroutineContext().ensureActive()
            val result = runDecay(
                frameClock = frameClock,
                animation = animation,
                boundsProvider = ::currentBounds,
            ) { state ->
                publishState(mutation.id, state)
            }
            currentCoroutineContext().ensureActive()
            return result
        } finally {
            endMutation(mutation.id)
        }
    }

    /**
     * Replaces the inclusive component-wise bounds for subsequent accepted samples.
     *
     * Either side may be unbounded with `null`. Validation is atomic: an inverted component throws
     * without changing existing bounds or state. While idle, an out-of-bounds value is clamped
     * immediately with zero velocity. While running, the active mutation observes the new bounds
     * on its next sample.
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
        val replacement = createAnimationVectorBounds(converter, lowerBound, upperBound)
        synchronized(mutationLock) {
            bounds = replacement
            if (activeMutation == null) {
                val clamped = clampAnimationState(
                    converter = converter,
                    value = internalState.value,
                    velocity = velocityState.value,
                    bounds = replacement,
                    playTimeNanos = 0L,
                )
                Snapshot.withMutableSnapshot {
                    internalState.value = clamped.value
                    velocityState.value = clamped.velocity
                    targetState.value = clamped.value
                }
            }
        }
    }

    private suspend fun beginMutation(
        targetValue: (T, AnimationVelocity<V>) -> T,
    ): Mutation<T> {
        val mutationContext = currentCoroutineContext()
        mutationContext.ensureActive()
        val mutationJob = mutationContext.job
        val previous: Mutation<T>?
        val mutation: Mutation<T>
        synchronized(mutationLock) {
            val startValue = internalState.value
            val startVelocity = velocityState.value
            val resolvedTarget = targetValue(startValue, startVelocity)
            mutation = Mutation(
                id = ++nextMutationId,
                job = mutationJob,
                startValue = startValue,
            )
            previous = activeMutation
            activeMutation = mutation
            Snapshot.withMutableSnapshot {
                targetState.value = resolvedTarget
                runningState.value = true
            }
        }
        if (previous != null && previous.job !== mutationJob) {
            previous.job.cancel(
                CancellationException("AnimatableCore mutation was interrupted by a newer mutation."),
            )
        }
        return mutation
    }

    private suspend fun commitInstantMutation(
        state: () -> AnimationState<T, V>,
    ) {
        val mutationContext = currentCoroutineContext()
        mutationContext.ensureActive()
        val mutationJob = mutationContext.job
        val previous: Mutation<T>?
        synchronized(mutationLock) {
            val committedState = state()
            previous = activeMutation
            activeMutation = null
            Snapshot.withMutableSnapshot {
                internalState.value = committedState.value
                velocityState.value = committedState.velocity
                targetState.value = committedState.value
                runningState.value = false
            }
        }
        if (previous != null && previous.job !== mutationJob) {
            previous.job.cancel(
                CancellationException("AnimatableCore mutation was interrupted by a newer mutation."),
            )
        }
    }

    private fun publishState(
        mutationId: Long,
        state: AnimationState<T, V>,
    ) {
        synchronized(mutationLock) {
            if (activeMutation?.id != mutationId) return
            Snapshot.withMutableSnapshot {
                internalState.value = state.value
                velocityState.value = state.velocity
            }
        }
    }

    private fun endMutation(mutationId: Long) {
        synchronized(mutationLock) {
            if (activeMutation?.id != mutationId) return
            activeMutation = null
            Snapshot.withMutableSnapshot {
                targetState.value = internalState.value
                runningState.value = false
            }
        }
    }

    private fun currentBounds(): AnimationVectorBounds? {
        return synchronized(mutationLock) { bounds }
    }

    private data class Mutation<T>(
        val id: Long,
        val job: Job,
        val startValue: T,
    )
}
