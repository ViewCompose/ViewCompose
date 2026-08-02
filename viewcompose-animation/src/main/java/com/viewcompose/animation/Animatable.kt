package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.runAnimation
import com.viewcompose.animation.core.spring
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.widget.core.LocalMonotonicFrameClock
import com.viewcompose.widget.core.remember
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/**
 * Owns an observable animated value with last-mutation-wins coroutine semantics.
 *
 * [animateTo], [snapTo], and [stop] are mutations. Starting a mutation from a different coroutine
 * job cancels the previous job, and only the newest mutation identifier may publish values. The
 * interrupted caller observes cancellation; stale frames cannot overwrite the new mutation.
 * External cancellation leaves [value] at its latest sample and resets [targetValue] to that value.
 *
 * The instance does not own a coroutine scope. [animateTo] requires the constructor's
 * [defaultFrameClock] or a clock installed later by [rememberAnimatable]. State reads are observable
 * by ViewCompose, while mutation arbitration is synchronized for cross-coroutine safety. Callers
 * should still perform UI-facing mutations from their structured UI scope.
 *
 * @sample com.viewcompose.animation.samples.animatableSample
 *
 * @param T domain value represented by [converter]
 * @param initialValue value exposed before the first mutation
 * @param converter stable converter used for all interpolated samples
 * @param defaultFrameClock optional clock for instances constructed outside composition
 */
class Animatable<T>(
    initialValue: T,
    private val converter: AnimationConverter<T>,
    defaultFrameClock: MonotonicFrameClock? = null,
) {
    private val internalState: MutableState<T> = mutableStateOf(initialValue)
    private val targetState: MutableState<T> = mutableStateOf(initialValue)
    private val runningState: MutableState<Boolean> = mutableStateOf(false)
    private val mutationLock = Any()

    private var boundFrameClock: MonotonicFrameClock? = defaultFrameClock
    private var nextMutationId: Long = 0L
    private var activeMutation: Mutation? = null

    /** Returns the live value most recently published by the active mutation. */
    val value: T
        get() = internalState.value

    /**
     * Returns the active mutation target, or [value] while idle.
     *
     * Cancellation, stop, and successful completion reset this property to the final retained value.
     */
    val targetValue: T
        get() = targetState.value

    /** Returns `true` while the newest mutation has not completed its cleanup. */
    val isRunning: Boolean
        get() = runningState.value

    /** Returns the stable observable state object backing [value]. */
    val asState: State<T>
        get() = internalState

    /**
     * Interrupts an older mutation and publishes [targetValue] without interpolation.
     *
     * The call does not require a frame clock. On return, [value] and this instance's
     * [Animatable.targetValue] equal [targetValue], and [isRunning] is false unless a newer mutation
     * has already replaced this one.
     *
     * @param targetValue value to publish immediately
     */
    suspend fun snapTo(targetValue: T) {
        val mutation = beginMutation(targetValue)
        try {
            publishValue(mutation.id, targetValue)
        } finally {
            endMutation(mutation.id)
        }
    }

    /**
     * Interrupts an older mutation and preserves its latest published value.
     *
     * The preserved [value] becomes [targetValue]. The call does not require a frame clock and is
     * idempotent while idle apart from briefly claiming mutation ownership.
     */
    suspend fun stop() {
        val mutation = beginMutation(internalState.value)
        endMutation(mutation.id)
    }

    /**
     * Interrupts an older mutation and animates from the latest [value] to [targetValue].
     *
     * The current coroutine owns the mutation. A newer mutation from another job cancels this
     * caller. Successful finite completion publishes the exact target. Cancellation or failure
     * propagates and retains the last accepted sample; infinite specifications run until replaced
     * or cancelled.
     *
     * @param targetValue requested terminal value
     * @param animationSpec timing policy; defaults to a duration-based spring approximation
     * @throws IllegalArgumentException if no frame clock was supplied or bound
     */
    suspend fun animateTo(
        targetValue: T,
        animationSpec: AnimationSpec = spring(),
    ) {
        val frameClock = requireNotNull(boundFrameClock) {
            "Animatable has no frame clock. Use rememberAnimatable(...) or pass a clock in constructor."
        }
        val mutation = beginMutation(targetValue)
        try {
            runAnimation(
                frameClock = frameClock,
                startValue = internalState.value,
                endValue = targetValue,
                animationSpec = animationSpec,
                converter = converter,
            ) { next ->
                publishValue(mutation.id, next)
            }
        } finally {
            endMutation(mutation.id)
        }
    }

    internal fun bindFrameClock(frameClock: MonotonicFrameClock) {
        boundFrameClock = frameClock
    }

    private suspend fun beginMutation(targetValue: T): Mutation {
        val mutationJob = currentCoroutineContext().job
        val mutation: Mutation
        val previous: Mutation?
        synchronized(mutationLock) {
            mutation = Mutation(
                id = ++nextMutationId,
                job = mutationJob,
            )
            previous = activeMutation
            activeMutation = mutation
            targetState.value = targetValue
            runningState.value = true
        }
        if (previous != null && previous.job !== mutationJob) {
            // Do not self-cancel when a mutation is replaced reentrantly from the same Job.
            previous.job.cancel(
                CancellationException("Animatable mutation was interrupted by a newer mutation."),
            )
        }
        return mutation
    }

    private fun publishValue(
        mutationId: Long,
        value: T,
    ) {
        synchronized(mutationLock) {
            if (activeMutation?.id == mutationId) {
                internalState.value = value
            }
        }
    }

    private fun endMutation(mutationId: Long) {
        synchronized(mutationLock) {
            if (activeMutation?.id == mutationId) {
                activeMutation = null
                targetState.value = internalState.value
                runningState.value = false
            }
        }
    }

    private data class Mutation(
        val id: Long,
        val job: Job,
    )
}

/**
 * Remembers an [Animatable] and binds it to the current composition frame clock.
 *
 * [initialValue] is used only when this call position creates an instance. Changing [converter]
 * creates a new instance and uses the then-current initial value; changing only [initialValue] does
 * not reset existing state. The frame clock is rebound on every composition so host replacement is
 * observed without recreating the value holder.
 *
 * @sample com.viewcompose.animation.samples.rememberAnimatableSample
 *
 * @param T domain value represented by [converter]
 * @param initialValue value for the first instance created at this call position
 * @param converter converter that also participates in remembered-instance identity
 * @return the composition-owned animated value holder
 */
fun <T> rememberAnimatable(
    initialValue: T,
    converter: AnimationConverter<T>,
): Animatable<T> {
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
