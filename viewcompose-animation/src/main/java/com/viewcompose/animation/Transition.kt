package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.TargetAnimation
import com.viewcompose.animation.core.TransitionCore
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.LocalAnimationCoroutineContext
import com.viewcompose.ui.foundation.LocalMonotonicFrameClock
import com.viewcompose.ui.foundation.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Coordinates multiple animated values against one logical state segment and shared timeline.
 *
 * Instances are created by [updateTransition]. Every `animate*` call position owns an observable
 * channel, freezes its current value and new target when a segment starts, and contributes its
 * duration. The longest channel determines when [currentState] commits [targetState]; shorter
 * channels settle at their own terminal values while the segment continues.
 *
 * Retargeting preserves each existing channel's latest sampled value as its new start. The object is
 * composition-owned, is not thread-safe, and does not expose imperative time control. Each target
 * or frame update publishes current state, target state, running state, segment identity, time, and
 * endpoints in one snapshot transaction, so observers never receive a committed mixed segment.
 *
 * @param S logical endpoint state mapped to channel target values
 */
class Transition<S> internal constructor(
    initialState: S,
    label: String,
) {
    @Suppress("unused")
    private val transitionLabel: String = label
    private val core = TransitionCore(initialState)
    private val currentStateHolder = mutableStateOf(initialState)
    private val targetStateHolder = mutableStateOf(initialState)
    private val runningHolder = mutableStateOf(false)
    private val segmentVersionHolder = mutableStateOf(0L)
    private val playTimeNanosHolder = mutableStateOf(0L)
    private val segmentInitialStateHolder = mutableStateOf(initialState)
    private val segmentTargetStateHolder = mutableStateOf(initialState)

    /**
     * Returns the last logical state committed after every registered channel finished.
     *
     * It remains at the previous endpoint while [isRunning] is `true`.
     */
    val currentState: S
        get() = currentStateHolder.value

    /** Returns the latest logical state supplied to [updateTransition]. */
    val targetState: S
        get() = targetStateHolder.value

    /** Returns `true` while the shared segment has not reached its longest channel duration. */
    val isRunning: Boolean
        get() = runningHolder.value

    internal val segmentInitialState: S
        get() = segmentInitialStateHolder.value

    internal val segmentTargetState: S
        get() = segmentTargetStateHolder.value

    internal val segmentVersion: Long
        get() = segmentVersionHolder.value

    internal val playTimeNanos: Long
        get() = playTimeNanosHolder.value

    internal val segmentDurationNanos: Long
        get() = core.segmentDurationNanos

    internal fun updateTarget(target: S) {
        core.updateTarget(target)
        syncFromCore()
    }

    internal fun registerChannelDuration(durationNanos: Long) {
        core.registerDuration(durationNanos)
    }

    internal fun advanceFrame(version: Long, playTimeNanos: Long) {
        if (core.segmentVersion != version || !core.isRunning) return
        core.updatePlayTime(playTimeNanos)
        syncFromCore()
    }

    internal fun isRunningOn(version: Long): Boolean {
        return core.segmentVersion == version && core.isRunning
    }

    // Composition reads a snapshot that may not see mirror writes from this pass. Scheduling uses
    // live core values so a newly started segment cannot miss its frame loop or channel duration.
    internal fun runtimeIsRunning(): Boolean = core.isRunning

    internal fun runtimeSegmentVersion(): Long = core.segmentVersion

    private fun syncFromCore() {
        Snapshot.withMutableSnapshot {
            currentStateHolder.value = core.currentState
            targetStateHolder.value = core.targetState
            runningHolder.value = core.isRunning
            segmentVersionHolder.value = core.segmentVersion
            playTimeNanosHolder.value = core.playTimeNanos
            segmentInitialStateHolder.value = core.segmentInitialState
            segmentTargetStateHolder.value = core.segmentTargetState
        }
    }

    private class ChannelState<T, V>(
        var segmentVersion: Long,
        var velocity: AnimationVelocity<V>,
        var animation: TargetAnimation<T, V>?,
        var liveValue: T,
    )

    private data class ChannelSample<T>(
        val state: State<T>,
        val liveValue: T,
    )

    private fun <T, V> animateValueInternal(
        converter: AnimationConverter<T, V>,
        transitionSpec: (initialState: S, targetState: S) -> FiniteAnimationSpec,
        segmentEndpoints: (initialState: S, targetState: S, currentValue: T) -> Pair<T, T>,
        valueForSettledState: (S) -> T,
    ): ChannelSample<T> {
        val outputState = remember(this, converter) {
            mutableStateOf(valueForSettledState(currentState))
        }
        val channelState = remember(this, converter) {
            ChannelState<T, V>(
                segmentVersion = -1L,
                velocity = AnimationVelocity(converter.zeroVelocity),
                animation = null,
                liveValue = outputState.value,
            )
        }
        val running = core.isRunning
        val version = core.segmentVersion
        if (channelState.segmentVersion != version) {
            // Freeze channel endpoints once per segment; subsequent samples share the common time.
            val segmentInitialState = core.segmentInitialState
            val segmentTargetState = core.segmentTargetState
            val spec = transitionSpec(segmentInitialState, segmentTargetState)
            val (start, end) = segmentEndpoints(
                segmentInitialState,
                segmentTargetState,
                outputState.value,
            )
            channelState.segmentVersion = version
            channelState.animation = TargetAnimation(
                initialValue = start,
                targetValue = end,
                animationSpec = spec,
                converter = converter,
                initialVelocity = channelState.velocity,
            )
        }
        if (running) {
            val animation = checkNotNull(channelState.animation)
            registerChannelDuration(
                durationNanos = animation.durationNanos,
            )
            // Keep observing the mirror so frame updates invalidate composition, but sample the
            // live coordinator. A target can reset its time inside a composition whose pinned
            // snapshot still exposes the previous segment's terminal play time.
            playTimeNanosHolder.value
            val state = animation.stateAt(core.playTimeNanos)
            outputState.value = state.value
            channelState.liveValue = state.value
            channelState.velocity = state.velocity
        } else {
            val settledValue = valueForSettledState(core.targetState)
            outputState.value = settledValue
            channelState.liveValue = settledValue
            channelState.velocity = AnimationVelocity(converter.zeroVelocity)
        }
        return ChannelSample(
            state = outputState,
            liveValue = channelState.liveValue,
        )
    }

    /**
     * Declares a [Float] channel derived from each logical state.
     *
     * [animationSpec] is evaluated once for each new segment. On retarget, interpolation starts from
     * the channel's latest sample and ends at `targetValueByState(targetState)`.
     *
     * @sample com.viewcompose.animation.samples.transitionSample
     *
     * @param animationSpec factory for the specification used by the next segment
     * @param targetValueByState maps a logical endpoint to this channel's settled value
     * @return stable composition-owned state containing the latest channel sample
     */
    fun animateFloat(
        animationSpec: () -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> Float,
    ): State<Float> {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        ).state
    }

    internal fun animateFloatBySegment(
        transitionSpec: (initialState: S, targetState: S) -> FiniteAnimationSpec,
        segmentEndpoints: (initialState: S, targetState: S, currentValue: Float) -> Pair<Float, Float>,
        valueForSettledState: (S) -> Float,
    ): State<Float> {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = transitionSpec,
            segmentEndpoints = segmentEndpoints,
            valueForSettledState = valueForSettledState,
        ).state
    }

    internal fun sampleFloatBySegment(
        transitionSpec: (initialState: S, targetState: S) -> FiniteAnimationSpec,
        segmentEndpoints: (initialState: S, targetState: S, currentValue: Float) -> Pair<Float, Float>,
        valueForSettledState: (S) -> Float,
    ): Float {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = transitionSpec,
            segmentEndpoints = segmentEndpoints,
            valueForSettledState = valueForSettledState,
        ).liveValue
    }

    /**
     * Declares an [Int] channel with truncating interpolation.
     *
     * @param animationSpec factory evaluated once for each new segment
     * @param targetValueByState maps a logical endpoint to the channel's settled integer
     * @return stable state containing the latest integer sample
     */
    fun animateInt(
        animationSpec: () -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateValueInternal(
            converter = AnimationConverters.Int,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        ).state
    }

    /**
     * Declares a packed ARGB channel interpolated by encoded color component.
     *
     * Interpolation is not gamma-correct or color-space aware.
     *
     * @param animationSpec factory evaluated once for each new segment
     * @param targetValueByState maps a logical endpoint to the channel's packed ARGB value
     * @return stable state containing the latest packed ARGB sample
     */
    fun animateColor(
        animationSpec: () -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateValueInternal(
            converter = AnimationConverters.ColorInt,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        ).state
    }

    /**
     * Declares a density-independent scalar channel.
     *
     * The numeric [UiDp.value] is interpolated without resolving pixels.
     *
     * @param animationSpec factory evaluated once for each new segment
     * @param targetValueByState maps a logical endpoint to the channel's settled [UiDp]
     * @return stable state containing the latest density-independent sample
     */
    fun animateDp(
        animationSpec: () -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> UiDp,
    ): State<UiDp> {
        return animateValueInternal(
            converter = AnimationUnitConverters.Dp,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        ).state
    }
}

/**
 * Remembers a [Transition], updates its target, and owns the shared segment frame loop.
 *
 * The first composition starts settled at [targetState]. Later unequal targets begin a segment from
 * the last committed logical state. Channels declared from the returned object register duration
 * during composition; a launched effect then advances the shared play time with
 * [LocalMonotonicFrameClock]. Target changes cancel the old effect and start a new segment.
 *
 * [label] is captured when the transition is first created and is currently reserved for
 * diagnostics. [LocalAnimationCoroutineContext] may select a dispatcher or other context elements
 * but must not contain a [Job], because the composition effect retains cancellation ownership.
 * Removing this call from composition cancels the frame loop and forgets its channel state.
 *
 * @sample com.viewcompose.animation.samples.transitionSample
 *
 * @param S logical endpoint state mapped by animation channels
 * @param targetState state requested by the current composition
 * @param label optional diagnostic label captured on first creation
 * @return the stable transition coordinator owned by this composition call position
 * @throws IllegalArgumentException if [LocalAnimationCoroutineContext] contains a [Job]
 */
fun <S> updateTransition(
    targetState: S,
    label: String = "",
): Transition<S> {
    val transition = remember {
        Transition(
            initialState = targetState,
            label = label,
        )
    }
    transition.updateTarget(targetState)
    val frameClock = LocalMonotonicFrameClock.current
    val animationCoroutineContext = LocalAnimationCoroutineContext.current
    val running = transition.runtimeIsRunning()
    val segmentVersion = transition.runtimeSegmentVersion()
    require(animationCoroutineContext[Job] == null) {
        "Animation coroutine context must not contain a Job."
    }
    LaunchedEffect(transition, running, segmentVersion, frameClock, animationCoroutineContext) {
        if (!running) {
            return@LaunchedEffect
        }
        val launchedVersion = segmentVersion
        withContext(animationCoroutineContext) {
            val startNanos = frameClock.withFrameNanos { it }
            while (isActive && transition.isRunningOn(launchedVersion)) {
                val frameNanos = frameClock.withFrameNanos { it }
                val playTime = (frameNanos - startNanos).coerceAtLeast(0L)
                transition.advanceFrame(
                    version = launchedVersion,
                    playTimeNanos = playTime,
                )
            }
        }
    }
    return transition
}
