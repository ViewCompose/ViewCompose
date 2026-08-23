package com.viewcompose.animation

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LocalAnimationCoroutineContext
import com.viewcompose.ui.foundation.LocalMonotonicFrameClock
import com.viewcompose.ui.foundation.remember
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Owns one externally seekable or autonomously animated [Transition].
 *
 * The state has one mutation writer at a time. A newer [seekTo], [animateTo], or [snapTo] cancels
 * and joins the older caller before it publishes another sample. Seeking maps [fraction] to the
 * longest committed channel duration, clamps shorter channels at their endpoints, and supplies
 * zero physical velocity. [animateTo] leaves seeking and continues from the sampled channel values
 * on one autonomous frame loop.
 *
 * Commands require an active [rememberTransition] binding and follow that composition's frame clock
 * and animation coroutine context. Removing the binding cancels an active command and retains an
 * unfinished sample as seeking state so a later binding can reconstruct it. This object owns no
 * coroutine scope, is not automatically saveable, and should be retained only for the lifecycle
 * that owns the visual transition.
 *
 * @sample com.viewcompose.animation.samples.seekableTransitionSample
 *
 * @param S logical endpoint state mapped by the bound transition's channels
 * @param initialState current and target state exposed before the first binding
 */
class SeekableTransitionState<S>(
    initialState: S,
) {
    private val mutationLock = Any()
    private val currentStateHolder = mutableStateOf(initialState)
    private val targetStateHolder = mutableStateOf(initialState)
    private val fractionHolder = mutableStateOf(0f)
    private val animatingHolder = mutableStateOf(false)
    private val seekingHolder = mutableStateOf(false)
    private var binding: Binding<S>? = null
    private var activeMutation: Mutation? = null
    private var nextBindingId: Long = 0L
    private var nextMutationId: Long = 0L

    /** Returns the last logical endpoint committed by the bound transition. */
    val currentState: S
        get() = currentStateHolder.value

    /** Returns the logical endpoint requested by the newest accepted command. */
    val targetState: S
        get() = targetStateHolder.value

    /**
     * Returns normalized progress on the longest committed channel while seeking or animating.
     *
     * The value is always finite and in `0f..1f`. It resets to zero after normal autonomous
     * completion or [snapTo].
     */
    val fraction: Float
        get() = fractionHolder.value

    /** Returns whether [animateTo] currently owns the bound transition's single frame loop. */
    val isAnimating: Boolean
        get() = animatingHolder.value

    /** Returns whether the retained visual sample is controlled by explicit normalized progress. */
    val isSeeking: Boolean
        get() = seekingHolder.value

    /**
     * Animates from the latest accepted sample to [targetState].
     *
     * Calling this method while seeking freezes every channel at its sampled value, resets seek
     * velocity to zero, and starts a fresh autonomous segment. Calling it during another autonomous
     * segment preserves physical channel velocity. A newer command cancels this caller.
     *
     * @sample com.viewcompose.animation.samples.driveSeekableTransitionSample
     *
     * @param targetState logical endpoint to commit after every channel finishes
     * @throws IllegalStateException when no [rememberTransition] is currently bound
     * @throws CancellationException when replaced, externally cancelled, or unbound
     */
    suspend fun animateTo(targetState: S) {
        val command = beginMutation()
        var transitionStarted = false
        try {
            withContext(command.binding.animationContext) {
                val version = synchronized(mutationLock) {
                    requireActive(command)
                    val wasSeeking = seekingHolder.value
                    val startedVersion = command.binding.transition.beginControlledAnimation(
                        target = targetState,
                        resumeFromSeek = wasSeeking,
                    )
                    transitionStarted = true
                    publishFromTransition(
                        transition = command.binding.transition,
                        fraction = 0f,
                        isAnimating = command.binding.transition.runtimeIsRunning(),
                        isSeeking = false,
                    )
                    startedVersion
                }

                awaitChannelConfiguration(command, version)
                if (synchronized(mutationLock) {
                        requireActive(command)
                        !command.binding.transition.isRunningOn(version)
                    }
                ) {
                    completeAutonomous(command)
                    return@withContext
                }

                val startNanos = command.binding.frameClock.withFrameNanos { frameTimeNanos ->
                    frameTimeNanos
                }
                while (currentCoroutineContext().isActive) {
                    val frameNanos = command.binding.frameClock.withFrameNanos { frameTimeNanos ->
                        frameTimeNanos
                    }
                    val keepRunning = synchronized(mutationLock) {
                        requireActive(command)
                        val transition = command.binding.transition
                        transition.advanceFrame(
                            version = version,
                            playTimeNanos = (frameNanos - startNanos).coerceAtLeast(0L),
                        )
                        if (transition.isRunningOn(version)) {
                            publishFromTransition(
                                transition = transition,
                                fraction = transition.normalizedRuntimeFraction(),
                                isAnimating = true,
                                isSeeking = false,
                            )
                            true
                        } else {
                            publishFromTransition(
                                transition = transition,
                                fraction = 0f,
                                isAnimating = false,
                                isSeeking = false,
                            )
                            activeMutation = null
                            false
                        }
                    }
                    if (!keepRunning) break
                }
            }
        } catch (cancellation: CancellationException) {
            preserveCancelledSample(command, transitionStarted)
            throw cancellation
        }
    }

    /**
     * Publishes the sample at normalized [fraction] toward [targetState].
     *
     * The input is validated before an older writer is cancelled. A changed target freezes current
     * channel samples as the new starts and resets their retained velocities before applying the
     * requested fraction. No autonomous frame loop remains active after this method returns.
     *
     * @sample com.viewcompose.animation.samples.driveSeekableTransitionSample
     *
     * @param fraction finite normalized progress in `0f..1f`
     * @param targetState logical endpoint sampled at progress one
     * @throws IllegalArgumentException when [fraction] is non-finite or outside `0f..1f`; the active
     * mutation and published state remain unchanged
     * @throws IllegalStateException when no [rememberTransition] is currently bound
     * @throws CancellationException when replaced, externally cancelled, or unbound
     */
    suspend fun seekTo(
        fraction: Float,
        targetState: S,
    ) {
        require(fraction.isFinite() && fraction in 0f..1f) {
            "fraction must be finite and in 0f..1f, but was $fraction."
        }
        val command = beginMutation()
        var transitionStarted = false
        try {
            withContext(command.binding.animationContext) {
                val version = synchronized(mutationLock) {
                    requireActive(command)
                    val startedVersion = command.binding.transition.beginControlledSeek(targetState)
                    transitionStarted = true
                    startedVersion
                }
                awaitChannelConfiguration(command, version)
                synchronized(mutationLock) {
                    requireActive(command)
                    val transition = command.binding.transition
                    transition.seekControlledFraction(fraction)
                    publishFromTransition(
                        transition = transition,
                        fraction = fraction,
                        isAnimating = false,
                        isSeeking = true,
                    )
                    activeMutation = null
                }
            }
        } catch (cancellation: CancellationException) {
            preserveCancelledSample(command, transitionStarted)
            throw cancellation
        }
    }

    /**
     * Commits [targetState] immediately with zero retained progress and no frame-loop writer.
     * Current state, target state, and both segment endpoints collapse to the requested target in
     * one snapshot transaction.
     *
     * @sample com.viewcompose.animation.samples.driveSeekableTransitionSample
     *
     * @param targetState logical endpoint published as both current and target state
     * @throws IllegalStateException when no [rememberTransition] is currently bound
     * @throws CancellationException when replaced, externally cancelled, or unbound while waiting
     * for an older writer to terminate
     */
    suspend fun snapTo(targetState: S) {
        val command = beginMutation()
        try {
            withContext(command.binding.animationContext) {
                synchronized(mutationLock) {
                    requireActive(command)
                    val transition = command.binding.transition
                    transition.snapControlled(targetState)
                    publishFromTransition(
                        transition = transition,
                        fraction = 0f,
                        isAnimating = false,
                        isSeeking = false,
                    )
                    activeMutation = null
                }
            }
        } catch (cancellation: CancellationException) {
            clearIfActive(command)
            throw cancellation
        }
    }

    internal fun bind(
        transition: Transition<S>,
        frameClock: MonotonicFrameClock,
        animationContext: CoroutineContext,
    ): Long {
        val binding = synchronized(mutationLock) {
            check(this.binding == null) {
                "SeekableTransitionState is already bound to an active rememberTransition call."
            }
            Binding(
                id = ++nextBindingId,
                transition = transition,
                frameClock = frameClock,
                animationContext = animationContext,
            ).also { accepted ->
                this.binding = accepted
                if (seekingHolder.value) {
                    transition.beginControlledSeek(targetStateHolder.value)
                    transition.seekControlledFraction(fractionHolder.value)
                }
            }
        }
        return binding.id
    }

    internal fun unbind(bindingId: Long) {
        val mutationToCancel = synchronized(mutationLock) {
            val activeBinding = binding
            if (activeBinding?.id != bindingId) return
            val transition = activeBinding.transition
            val unfinished = transition.runtimeCurrentState() != transition.runtimeTargetState()
            if (unfinished) {
                publishFromTransition(
                    transition = transition,
                    fraction = transition.cancelControlledAnimationToSeek(),
                    isAnimating = false,
                    isSeeking = true,
                )
            } else {
                publishFromTransition(
                    transition = transition,
                    fraction = 0f,
                    isAnimating = false,
                    isSeeking = false,
                )
            }
            binding = null
            activeMutation.also {
                activeMutation = null
            }
        }
        mutationToCancel?.job?.cancel(
            CancellationException("SeekableTransitionState was removed from composition."),
        )
    }

    private suspend fun beginMutation(): ActiveCommand<S> {
        val context = currentCoroutineContext()
        context.ensureActive()
        val mutationJob = context.job
        val previous: Mutation?
        val command: ActiveCommand<S>
        synchronized(mutationLock) {
            val activeBinding = checkNotNull(binding) {
                "SeekableTransitionState has no active binding. Call rememberTransition(state)."
            }
            val mutation = Mutation(
                id = ++nextMutationId,
                job = mutationJob,
                bindingId = activeBinding.id,
            )
            previous = activeMutation
            activeMutation = mutation
            command = ActiveCommand(
                mutation = mutation,
                binding = activeBinding,
            )
        }
        if (previous != null && previous.job !== mutationJob) {
            previous.job.cancel(
                CancellationException(
                    "SeekableTransitionState mutation was interrupted by a newer mutation.",
                ),
            )
            previous.job.join()
        }
        context.ensureActive()
        synchronized(mutationLock) {
            requireActive(command)
        }
        return command
    }

    private suspend fun awaitChannelConfiguration(
        command: ActiveCommand<S>,
        segmentVersion: Long,
    ) {
        repeat(CONFIGURATION_FRAME_LIMIT) {
            val configured = synchronized(mutationLock) {
                requireActive(command)
                command.binding.transition.runtimeConfiguredSegmentVersion() == segmentVersion ||
                    !command.binding.transition.isRunningOn(segmentVersion)
            }
            if (configured) return
            command.binding.frameClock.withFrameNanos { Unit }
        }
    }

    private fun completeAutonomous(command: ActiveCommand<S>) {
        synchronized(mutationLock) {
            requireActive(command)
            publishFromTransition(
                transition = command.binding.transition,
                fraction = 0f,
                isAnimating = false,
                isSeeking = false,
            )
            activeMutation = null
        }
    }

    private fun preserveCancelledSample(
        command: ActiveCommand<S>,
        transitionStarted: Boolean,
    ) {
        synchronized(mutationLock) {
            if (!isActive(command)) return
            if (transitionStarted) {
                val transition = command.binding.transition
                publishFromTransition(
                    transition = transition,
                    fraction = transition.cancelControlledAnimationToSeek(),
                    isAnimating = false,
                    isSeeking = transition.runtimeCurrentState() != transition.runtimeTargetState(),
                )
            }
            activeMutation = null
        }
    }

    private fun clearIfActive(command: ActiveCommand<S>) {
        synchronized(mutationLock) {
            if (isActive(command)) {
                activeMutation = null
            }
        }
    }

    private fun requireActive(command: ActiveCommand<S>) {
        if (!isActive(command)) {
            throw CancellationException(
                "SeekableTransitionState mutation is no longer the active writer.",
            )
        }
    }

    private fun isActive(command: ActiveCommand<S>): Boolean {
        return activeMutation?.id == command.mutation.id &&
            binding?.id == command.binding.id &&
            command.mutation.bindingId == command.binding.id
    }

    private fun publishFromTransition(
        transition: Transition<S>,
        fraction: Float,
        isAnimating: Boolean,
        isSeeking: Boolean,
    ) {
        Snapshot.withMutableSnapshot {
            currentStateHolder.value = transition.runtimeCurrentState()
            targetStateHolder.value = transition.runtimeTargetState()
            fractionHolder.value = fraction
            animatingHolder.value = isAnimating
            seekingHolder.value = isSeeking
        }
    }

    private data class Binding<S>(
        val id: Long,
        val transition: Transition<S>,
        val frameClock: MonotonicFrameClock,
        val animationContext: CoroutineContext,
    )

    private data class Mutation(
        val id: Long,
        val job: Job,
        val bindingId: Long,
    )

    private data class ActiveCommand<S>(
        val mutation: Mutation,
        val binding: Binding<S>,
    )

    private companion object {
        const val CONFIGURATION_FRAME_LIMIT: Int = 2
    }
}

/**
 * Remembers one [Transition] whose only writer is [transitionState].
 *
 * The binding is installed only after a successful composition commit and is removed synchronously
 * when this call leaves composition. Unlike [updateTransition], this overload launches no automatic
 * effect; [SeekableTransitionState.animateTo] owns the sole frame loop. Reusing the same state in two
 * simultaneously active call positions is rejected.
 *
 * @sample com.viewcompose.animation.samples.seekableTransitionSample
 *
 * @param S logical endpoint state mapped by transition channels
 * @param transitionState lifecycle-retained external seek and animation owner
 * @param label optional diagnostic label captured by this remembered transition
 * @return stable transition coordinator bound to [transitionState]
 * @throws IllegalArgumentException if [LocalAnimationCoroutineContext] contains a [Job]
 * @throws IllegalStateException if [transitionState] is already bound elsewhere
 */
fun <S> rememberTransition(
    transitionState: SeekableTransitionState<S>,
    label: String = "",
): Transition<S> {
    val initialState = transitionState.currentState
    val transition = remember(transitionState) {
        Transition(
            initialState = initialState,
            label = label,
        )
    }
    val frameClock = LocalMonotonicFrameClock.current
    val animationContext = LocalAnimationCoroutineContext.current
    require(animationContext[Job] == null) {
        "Animation coroutine context must not contain a Job."
    }

    // Commands mutate live coordinator state outside composition. These reads subscribe the call
    // position so committed command samples rebuild every declared channel exactly once.
    transition.segmentVersion
    transition.playTimeNanos
    DisposableEffect(transition) {
        transition.attachTimelineTooling()
        onDispose(transition::detachTimelineTooling)
    }
    DisposableEffect(transitionState, transition, frameClock, animationContext) {
        val bindingId = transitionState.bind(
            transition = transition,
            frameClock = frameClock,
            animationContext = animationContext,
        )
        onDispose {
            transitionState.unbind(bindingId)
        }
    }
    return transition
}

private fun Transition<*>.normalizedRuntimeFraction(): Float {
    val duration = segmentDurationNanos
    if (duration <= 0L) return 0f
    return (playTimeNanos.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}
