package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationEndReason
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.ArgbChannels
import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.KeyframesSpec
import com.viewcompose.animation.core.RepeatableSpec
import com.viewcompose.animation.core.SnapSpec
import com.viewcompose.animation.core.SpringSpec
import com.viewcompose.animation.core.TargetAnimation
import com.viewcompose.animation.core.TransitionCore
import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.animation.core.tween
import com.viewcompose.animation.tooling.AnimationTimelineChannelSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineRegistration
import com.viewcompose.animation.tooling.AnimationTimelineRunState
import com.viewcompose.animation.tooling.AnimationTimelineSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineSource
import com.viewcompose.animation.tooling.AnimationTimelineSpecFamily
import com.viewcompose.animation.tooling.AnimationTimelineStateSummary
import com.viewcompose.animation.tooling.AnimationTimelineTerminalCondition
import com.viewcompose.animation.tooling.AnimationTimelineToolingDiscovery
import com.viewcompose.animation.tooling.AnimationTimelineValue
import com.viewcompose.animation.tooling.AnimationTimelineValueKind
import com.viewcompose.animation.tooling.MAX_TIMELINE_CHANNELS
import com.viewcompose.animation.tooling.MAX_TIMELINE_TEXT_LENGTH
import com.viewcompose.runtime.State
import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.composition.RememberObserver
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.LocalAnimationCoroutineContext
import com.viewcompose.ui.foundation.LocalMonotonicFrameClock
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.remember
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * Immutable logical endpoints selected for one [Transition] segment.
 *
 * One instance is retained for the complete segment, including explicit seeking and autonomous
 * continuation. It changes only when the transition accepts a different target or restarts an
 * unfinished seek from its sampled values. Equality checks use the logical state's `equals`
 * contract; channel values and play time are intentionally absent.
 *
 * @sample com.viewcompose.animation.samples.seekableTransitionSample
 *
 * @param S logical transition state domain
 */
interface TransitionSegment<S> {
    /** Logical state from which this segment was accepted. */
    val initialState: S

    /** Logical state the segment commits at its terminal sample. */
    val targetState: S

    /**
     * Returns whether this segment exactly matches [initial] to [target].
     *
     * @param initial expected logical start state
     * @param target expected logical end state
     * @return `true` when both expected endpoints match this segment
     */
    fun isTransitioningTo(initial: S, target: S): Boolean {
        return initialState == initial && targetState == target
    }
}

private data class ImmutableTransitionSegment<S>(
    override val initialState: S,
    override val targetState: S,
) : TransitionSegment<S>

/**
 * Coordinates multiple animated values against one logical state segment and shared timeline.
 *
 * Instances are created by [updateTransition]. Every `animate*` call position owns an observable
 * channel, freezes its current value and new target when a segment starts, and contributes its
 * duration. The longest channel determines when [currentState] commits [targetState]; shorter
 * channels settle at their own terminal values while the segment continues.
 *
 * Retargeting preserves each existing channel's latest sampled value as its new start. The object is
 * composition-owned and is not thread-safe. [updateTransition] owns its autonomous frame loop;
 * [rememberTransition] instead binds it to one [SeekableTransitionState], whose commands are the
 * only writer. Each target or frame update publishes current state, target state, running state,
 * [segment], and time in one snapshot transaction, so observers never receive a mixed segment.
 *
 * @param S logical endpoint state mapped to channel target values
 */
class Transition<S> internal constructor(
    initialState: S,
    label: String,
) {
    @Suppress("unused")
    private val transitionLabel: String = label
    private val timelineIdentity = nextTimelineIdentity.incrementAndGet()
    private val timelineLabel = label.take(MAX_TIMELINE_TEXT_LENGTH)
    private val core = TransitionCore(initialState)
    private val currentStateHolder = mutableStateOf(initialState)
    private val targetStateHolder = mutableStateOf(initialState)
    private val runningHolder = mutableStateOf(false)
    private val segmentVersionHolder = mutableStateOf(0L)
    private val playTimeNanosHolder = mutableStateOf(0L)
    private val segmentInitialStateHolder = mutableStateOf(initialState)
    private val segmentTargetStateHolder = mutableStateOf(initialState)
    private var liveSegment: TransitionSegment<S> = ImmutableTransitionSegment(initialState, initialState)
    private var liveSegmentVersion: Long = 0L
    private val segmentHolder = mutableStateOf(liveSegment)
    private val committedChannels = LinkedHashSet<ChannelState<*, *>>()
    private var nextChannelIdentity: Long = 0L
    private var timelineRegistration: AnimationTimelineRegistration? = null
    private var controlledSeekFraction: Float? = null
    private var zeroVelocitySegmentVersion: Long = -1L
    private var interruptedSegmentVersion: Long = -1L
    private var timelineSource: AnimationTimelineSource? = null

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

    /**
     * Returns the stable logical endpoints for the current or most recently completed segment.
     *
     * Segment-aware channel specifications receive this same object. Retargeting or resuming an
     * unfinished seek creates a new object; ordinary frame sampling and duration recomputation do
     * not.
     */
    val segment: TransitionSegment<S>
        get() = segmentHolder.value

    internal val segmentInitialState: S
        get() = segmentInitialStateHolder.value

    internal val segmentTargetState: S
        get() = segmentTargetStateHolder.value

    internal val segmentVersion: Long
        get() = segmentVersionHolder.value

    internal val playTimeNanos: Long
        get() = playTimeNanosHolder.value

    internal val segmentDurationNanos: Long
        get() = effectiveSegmentDurationNanos()

    internal fun updateTarget(target: S) {
        val interrupted = core.isRunning && target != core.targetState
        val previousVersion = core.segmentVersion
        core.updateTarget(target)
        if (interrupted && core.segmentVersion != previousVersion) {
            interruptedSegmentVersion = core.segmentVersion
        }
        syncFromCore()
    }

    internal fun advanceFrame(version: Long, playTimeNanos: Long) {
        if (core.segmentVersion != version || !core.isRunning) return
        if (playTimeNanos > 0L && interruptedSegmentVersion == version) {
            interruptedSegmentVersion = -1L
        }
        val duration = effectiveSegmentDurationNanos()
        core.replaceDuration(duration)
        core.updatePlayTime(playTimeNanos.coerceAtMost(duration))
        syncFromCore()
    }

    internal fun isRunningOn(version: Long): Boolean {
        return core.segmentVersion == version && core.isRunning
    }

    // Composition reads a snapshot that may not see mirror writes from this pass. Scheduling uses
    // live core values so a newly started segment cannot miss its frame loop or channel duration.
    internal fun runtimeIsRunning(): Boolean = core.isRunning

    internal fun runtimeSegmentVersion(): Long = core.segmentVersion

    internal fun runtimeCurrentState(): S = core.currentState

    internal fun runtimeTargetState(): S = core.targetState

    internal fun runtimeConfiguredSegmentVersion(): Long {
        val version = core.segmentVersion
        return if (committedChannels.any { channel -> channel.durationVersion == version }) {
            version
        } else {
            -1L
        }
    }

    internal fun beginControlledAnimation(
        target: S,
        resumeFromSeek: Boolean,
    ): Long {
        controlledSeekFraction = null
        val wasRunning = core.isRunning
        val previousTarget = core.targetState
        val previousVersion = core.segmentVersion
        if (target == core.targetState && core.currentState != core.targetState) {
            core.restartRunningSegment()
            if (resumeFromSeek) {
                zeroVelocitySegmentVersion = core.segmentVersion
            }
        } else {
            core.updateTarget(target)
            if (resumeFromSeek && core.segmentVersion != previousVersion) {
                zeroVelocitySegmentVersion = core.segmentVersion
            }
        }
        if (wasRunning && target != previousTarget && core.segmentVersion != previousVersion) {
            interruptedSegmentVersion = core.segmentVersion
        }
        syncFromCore()
        return core.segmentVersion
    }

    internal fun beginControlledSeek(target: S): Long {
        val interrupted = core.isRunning && target != core.targetState
        val previousVersion = core.segmentVersion
        core.updateTarget(target)
        controlledSeekFraction = 0f
        if (core.segmentVersion != previousVersion) {
            zeroVelocitySegmentVersion = core.segmentVersion
            if (interrupted) {
                interruptedSegmentVersion = core.segmentVersion
            }
        }
        syncFromCore()
        return core.segmentVersion
    }

    internal fun seekControlledFraction(fraction: Float) {
        controlledSeekFraction = fraction
        val duration = effectiveSegmentDurationNanos()
        core.replaceDuration(duration)
        core.seekToPlayTime(playTimeForFraction(duration, fraction))
        if (fraction > 0f && interruptedSegmentVersion == core.segmentVersion) {
            interruptedSegmentVersion = -1L
        }
        syncFromCore()
    }

    internal fun snapControlled(target: S) {
        controlledSeekFraction = null
        interruptedSegmentVersion = -1L
        core.snapTo(target)
        syncFromCore()
    }

    internal fun cancelControlledAnimationToSeek(): Float {
        val duration = effectiveSegmentDurationNanos()
        val fraction = if (duration <= 0L) {
            0f
        } else {
            (core.playTimeNanos.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        controlledSeekFraction = fraction
        syncFromCore()
        return fraction
    }

    private fun syncFromCore() {
        if (
            liveSegmentVersion != core.segmentVersion ||
            liveSegment.initialState != core.segmentInitialState ||
            liveSegment.targetState != core.segmentTargetState
        ) {
            liveSegment = ImmutableTransitionSegment(
                initialState = core.segmentInitialState,
                targetState = core.segmentTargetState,
            )
            liveSegmentVersion = core.segmentVersion
        }
        Snapshot.withMutableSnapshot {
            currentStateHolder.value = core.currentState
            targetStateHolder.value = core.targetState
            runningHolder.value = core.isRunning
            segmentVersionHolder.value = core.segmentVersion
            playTimeNanosHolder.value = core.playTimeNanos
            segmentInitialStateHolder.value = core.segmentInitialState
            segmentTargetStateHolder.value = core.segmentTargetState
            segmentHolder.value = liveSegment
        }
        recordTimelineIfRequested()
    }

    private fun onChannelRemembered(channel: ChannelState<*, *>) {
        committedChannels += channel
    }

    private fun onChannelForgotten(channel: ChannelState<*, *>) {
        committedChannels -= channel
        recomputeCommittedDuration()
    }

    private fun onChannelCommitted(channel: ChannelState<*, *>) {
        if (channel in committedChannels) {
            recomputeCommittedDuration()
        }
    }

    private fun recomputeCommittedDuration() {
        val duration = effectiveSegmentDurationNanos()
        core.replaceDuration(duration)
        controlledSeekFraction?.let { fraction ->
            core.seekToPlayTime(playTimeForFraction(duration, fraction))
        }
        syncFromCore()
    }

    private fun effectiveSegmentDurationNanos(): Long {
        val version = core.segmentVersion
        return committedChannels
            .asSequence()
            .filter { channel -> channel.durationVersion == version }
            .maxOfOrNull { channel -> channel.durationNanos }
            ?.coerceAtLeast(1L)
            ?: 1L
    }

    private fun playTimeForFraction(durationNanos: Long, fraction: Float): Long {
        if (fraction <= 0f) return 0L
        if (fraction >= 1f) return durationNanos
        return (durationNanos.toDouble() * fraction.toDouble())
            .roundToLong()
            .coerceIn(0L, durationNanos)
    }

    internal fun attachTimelineTooling(
        tooling: com.viewcompose.animation.tooling.AnimationTimelineTooling? =
            AnimationTimelineToolingDiscovery.tooling,
    ) {
        if (timelineRegistration != null) return
        tooling ?: return
        timelineRegistration = try {
            tooling.register(timelineSource())
        } catch (_: Exception) {
            null
        }
    }

    internal fun detachTimelineTooling() {
        val registration = timelineRegistration ?: return
        timelineRegistration = null
        try {
            registration.dispose()
        } catch (_: Exception) {
            // Optional tooling cannot become a transition lifecycle dependency.
        }
    }

    private fun recordTimelineIfRequested() {
        val registration = timelineRegistration ?: return
        try {
            if (registration.captureRequested()) {
                registration.record(timelineSnapshot())
            }
        } catch (_: Exception) {
            // Request-time diagnostics are isolated from animation publication.
        }
    }

    private fun timelineSource(): AnimationTimelineSource {
        timelineSource?.let { source -> return source }
        return object : AnimationTimelineSource {
            override val identity: String = "transition-$timelineIdentity"

            override val label: String
                get() = timelineLabel

            override fun snapshot(): AnimationTimelineSnapshot = timelineSnapshot()
        }.also { source -> timelineSource = source }
    }

    private fun timelineSnapshot(): AnimationTimelineSnapshot {
        val playTime = core.playTimeNanos.coerceAtLeast(0L)
        return AnimationTimelineSnapshot(
            identity = "transition-$timelineIdentity",
            label = timelineLabel,
            currentState = core.currentState.toTimelineStateSummary(),
            targetState = core.targetState.toTimelineStateSummary(),
            segmentInitialState = core.segmentInitialState.toTimelineStateSummary(),
            segmentTargetState = core.segmentTargetState.toTimelineStateSummary(),
            segmentVersion = core.segmentVersion.coerceAtLeast(0L),
            playTimeNanos = playTime,
            durationNanos = effectiveSegmentDurationNanos().coerceAtLeast(0L),
            runState = when {
                !core.isRunning -> AnimationTimelineRunState.Idle
                interruptedSegmentVersion == core.segmentVersion ->
                    AnimationTimelineRunState.Interrupted
                else -> AnimationTimelineRunState.Running
            },
            channels = committedChannels
                .asSequence()
                .take(MAX_TIMELINE_CHANNELS)
                .map { channel -> channel.timelineSnapshot(playTime) }
                .toList(),
        )
    }

    private class ChannelState<T, V>(
        private val owner: Transition<*>,
        val timelineIdentity: Long,
        val timelineKind: ChannelTimelineKind,
        var segmentVersion: Long,
        var velocity: AnimationVelocity<V>,
        var animation: TargetAnimation<T, V>?,
        var liveValue: T,
    ) : RememberObserver {
        var durationVersion: Long = -1L
            private set
        var durationNanos: Long = 1L
            private set
        var timelineSpecFamily: AnimationTimelineSpecFamily = AnimationTimelineSpecFamily.Unsupported
            private set
        private var remembered: Boolean = false

        fun commit(
            segmentVersion: Long,
            velocity: AnimationVelocity<V>,
            animation: TargetAnimation<T, V>?,
            liveValue: T,
            durationNanos: Long?,
            timelineSpecFamily: AnimationTimelineSpecFamily,
        ) {
            this.segmentVersion = segmentVersion
            this.velocity = velocity
            this.animation = animation
            this.liveValue = liveValue
            if (durationNanos != null) {
                durationVersion = segmentVersion
                this.durationNanos = durationNanos.coerceAtLeast(1L)
            }
            this.timelineSpecFamily = timelineSpecFamily
            owner.onChannelCommitted(this)
        }

        fun timelineSnapshot(playTimeNanos: Long): AnimationTimelineChannelSnapshot {
            val evaluator = animation
            val startValue = evaluator
                ?.let { active -> runCatching { active.stateAt(0L).value }.getOrNull() }
                ?: liveValue
            val targetValue = evaluator?.targetValue ?: liveValue
            return AnimationTimelineChannelSnapshot(
                identity = "channel-$timelineIdentity",
                name = "${timelineKind.displayName} $timelineIdentity",
                specFamily = timelineSpecFamily,
                startValue = timelineKind.projectValue(startValue),
                currentValue = timelineKind.projectValue(liveValue),
                targetValue = timelineKind.projectValue(targetValue),
                velocity = timelineKind.projectVelocity(velocity.valuePerSecond),
                durationNanos = evaluator?.durationNanos?.coerceAtLeast(0L) ?: 0L,
                finished = evaluator?.isFinished(playTimeNanos) ?: true,
                terminalCondition = evaluator?.terminalEndReason?.timelineCondition()
                    ?: AnimationTimelineTerminalCondition.Finished,
            )
        }

        override fun onRemembered() {
            remembered = true
            owner.onChannelRemembered(this)
        }

        override fun onForgotten() {
            if (remembered) {
                remembered = false
                owner.onChannelForgotten(this)
            }
        }

        override fun onAbandoned() = Unit
    }

    private enum class ChannelTimelineKind(
        val displayName: String,
    ) {
        FloatValue("Float"),
        IntValue("Int"),
        DpValue("Dp"),
        ArgbValue("ARGB"),
        Unsupported("Value"),
        ;

        fun projectValue(value: Any?): AnimationTimelineValue? {
            return when (this) {
                FloatValue -> (value as? Float)?.timelineScalar(AnimationTimelineValueKind.Float)
                IntValue -> (value as? Int)
                    ?.takeIf { integer -> integer in -MAX_EXACT_FLOAT_INT..MAX_EXACT_FLOAT_INT }
                    ?.toFloat()
                    ?.timelineScalar(AnimationTimelineValueKind.Int)
                DpValue -> (value as? UiDp)?.value
                    ?.timelineScalar(AnimationTimelineValueKind.Dp)
                ArgbValue -> (value as? Int)?.timelineArgb()
                Unsupported -> null
            }
        }

        fun projectVelocity(value: Any?): AnimationTimelineValue? {
            return when (this) {
                FloatValue,
                IntValue,
                -> (value as? Float)?.timelineScalar(AnimationTimelineValueKind.Float)
                DpValue -> (value as? UiDp)?.value
                    ?.timelineScalar(AnimationTimelineValueKind.Dp)
                ArgbValue -> (value as? ArgbChannels)?.let { channels ->
                    AnimationTimelineValue(
                        kind = AnimationTimelineValueKind.Argb,
                        components = listOf(
                            channels.alpha,
                            channels.red,
                            channels.green,
                            channels.blue,
                        ),
                    )
                }
                Unsupported -> null
            }
        }
    }

    private data class ChannelSample<T>(
        val state: State<T>,
        val liveValue: T,
    )

    private fun <T, V> animateValueInternal(
        converter: AnimationConverter<T, V>,
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec,
        segmentEndpoints: (segment: TransitionSegment<S>, currentValue: T) -> Pair<T, T>,
        valueForSettledState: (S) -> T,
        timelineKind: ChannelTimelineKind = ChannelTimelineKind.Unsupported,
    ): ChannelSample<T> {
        val outputState = remember(this, converter) {
            mutableStateOf(valueForSettledState(currentState))
        }
        val channelState = remember(this, converter) {
            ChannelState<T, V>(
                owner = this,
                timelineIdentity = ++nextChannelIdentity,
                timelineKind = timelineKind,
                segmentVersion = -1L,
                velocity = AnimationVelocity(converter.zeroVelocity),
                animation = null,
                liveValue = outputState.value,
            )
        }
        val version = core.segmentVersion
        val unequalSegment = core.segmentInitialState != core.segmentTargetState
        var candidateSpecFamily = channelState.timelineSpecFamily
        val candidateAnimation = if (channelState.segmentVersion != version && unequalSegment) {
            // Freeze channel endpoints once per segment; subsequent samples share the common time.
            val segment = liveSegment
            val spec = segment.transitionSpec()
            val (start, end) = segmentEndpoints(segment, outputState.value)
            candidateSpecFamily = spec.timelineFamily()
            TargetAnimation(
                initialValue = start,
                targetValue = end,
                animationSpec = spec,
                converter = converter,
                initialVelocity = if (zeroVelocitySegmentVersion == version) {
                    AnimationVelocity(converter.zeroVelocity)
                } else {
                    channelState.velocity
                },
            )
        } else if (channelState.segmentVersion == version) {
            channelState.animation
        } else {
            null
        }
        val sampledState = if (candidateAnimation != null) {
            // Keep observing the mirror so frame updates invalidate composition, but sample the
            // live coordinator. A target can reset its time inside a composition whose pinned
            // snapshot still exposes the previous segment's terminal play time.
            playTimeNanosHolder.value
            candidateAnimation.stateAt(core.playTimeNanos)
        } else {
            null
        }
        val candidateValue = sampledState?.value ?: valueForSettledState(core.targetState)
        val candidateVelocity = if (controlledSeekFraction != null || !core.isRunning) {
            AnimationVelocity(converter.zeroVelocity)
        } else {
            sampledState?.velocity ?: AnimationVelocity(converter.zeroVelocity)
        }
        if (candidateAnimation == null) {
            candidateSpecFamily = AnimationTimelineSpecFamily.Unsupported
        }
        outputState.value = candidateValue
        SideEffect {
            channelState.commit(
                segmentVersion = version,
                velocity = candidateVelocity,
                animation = candidateAnimation,
                liveValue = candidateValue,
                durationNanos = candidateAnimation?.durationNanos,
                timelineSpecFamily = candidateSpecFamily,
            )
        }
        return ChannelSample(
            state = outputState,
            liveValue = candidateValue,
        )
    }

    /**
     * Declares a custom typed channel on this transition's shared segment timeline.
     *
     * [transitionSpec] is evaluated once for each accepted [segment]. The channel freezes its latest
     * sampled value as the next start, maps the segment target through [targetValueByState], and
     * preserves autonomous physical velocity across ordinary retargeting. Explicit seeking samples
     * position only and resets retained channel velocity to [AnimationConverter.zeroVelocity].
     *
     * Every committed call position contributes its duration to the shared maximum. Adding or
     * removing a channel recomputes that maximum; shorter channels clamp at their own terminal
     * samples. The converter instance is part of call-position identity and must keep a stable
     * dimension contract. Infinite specifications are excluded by [FiniteAnimationSpec].
     *
     * @sample com.viewcompose.animation.samples.seekableTransitionSample
     *
     * @param T immutable animated value domain
     * @param V immutable typed velocity domain defined by [converter]
     * @param converter stable converter used to validate, sample, and reconstruct the channel
     * @param transitionSpec segment-aware finite timing policy evaluated once per segment
     * @param targetValueByState maps each logical endpoint to the channel's settled value
     * @return stable composition-owned state containing the latest accepted sample
     * @throws IllegalArgumentException if converter output or the selected specification is invalid
     */
    fun <T, V> animateValue(
        converter: AnimationConverter<T, V>,
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> T,
    ): State<T> {
        return animateValueInternal(
            converter = converter,
            transitionSpec = transitionSpec,
            segmentEndpoints = { segment, current ->
                current to targetValueByState(segment.targetState)
            },
            valueForSettledState = targetValueByState,
        ).state
    }

    /**
     * Declares a [Float] channel derived from each logical state.
     *
     * [transitionSpec] is evaluated once for each new segment. On retarget, interpolation starts from
     * the channel's latest sample and ends at `targetValueByState(targetState)`.
     *
     * @sample com.viewcompose.animation.samples.transitionSample
     *
     * @param transitionSpec segment-aware specification used by the next segment
     * @param targetValueByState maps a logical endpoint to this channel's settled value
     * @return stable composition-owned state containing the latest channel sample
     */
    fun animateFloat(
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> Float,
    ): State<Float> {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = transitionSpec,
            segmentEndpoints = { segment, current ->
                current to targetValueByState(segment.targetState)
            },
            valueForSettledState = targetValueByState,
            timelineKind = ChannelTimelineKind.FloatValue,
        ).state
    }

    internal fun sampleFloat(
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec,
        segmentEndpoints: (segment: TransitionSegment<S>, currentValue: Float) -> Pair<Float, Float>,
        valueForSettledState: (S) -> Float,
    ): Float {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = transitionSpec,
            segmentEndpoints = segmentEndpoints,
            valueForSettledState = valueForSettledState,
            timelineKind = ChannelTimelineKind.FloatValue,
        ).liveValue
    }

    /**
     * Declares an [Int] channel with truncating interpolation.
     *
     * @param transitionSpec segment-aware factory evaluated once for each new segment
     * @param targetValueByState maps a logical endpoint to the channel's settled integer
     * @return stable state containing the latest integer sample
     */
    fun animateInt(
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateValueInternal(
            converter = AnimationConverters.Int,
            transitionSpec = transitionSpec,
            segmentEndpoints = { segment, current ->
                current to targetValueByState(segment.targetState)
            },
            valueForSettledState = targetValueByState,
            timelineKind = ChannelTimelineKind.IntValue,
        ).state
    }

    /**
     * Declares a packed ARGB channel interpolated by encoded color component.
     *
     * Interpolation is not gamma-correct or color-space aware.
     *
     * @param transitionSpec segment-aware factory evaluated once for each new segment
     * @param targetValueByState maps a logical endpoint to the channel's packed ARGB value
     * @return stable state containing the latest packed ARGB sample
     */
    fun animateColor(
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateValueInternal(
            converter = AnimationConverters.ColorInt,
            transitionSpec = transitionSpec,
            segmentEndpoints = { segment, current ->
                current to targetValueByState(segment.targetState)
            },
            valueForSettledState = targetValueByState,
            timelineKind = ChannelTimelineKind.ArgbValue,
        ).state
    }

    /**
     * Declares a density-independent scalar channel.
     *
     * The numeric [UiDp.value] is interpolated without resolving pixels.
     *
     * @param transitionSpec segment-aware factory evaluated once for each new segment
     * @param targetValueByState maps a logical endpoint to the channel's settled [UiDp]
     * @return stable state containing the latest density-independent sample
     */
    fun animateDp(
        transitionSpec: TransitionSegment<S>.() -> FiniteAnimationSpec = { tween() },
        targetValueByState: (S) -> UiDp,
    ): State<UiDp> {
        return animateValueInternal(
            converter = AnimationUnitConverters.Dp,
            transitionSpec = transitionSpec,
            segmentEndpoints = { segment, current ->
                current to targetValueByState(segment.targetState)
            },
            valueForSettledState = targetValueByState,
            timelineKind = ChannelTimelineKind.DpValue,
        ).state
    }

    private companion object {
        val nextTimelineIdentity = AtomicLong(0L)
    }
}

private fun Float.timelineScalar(kind: AnimationTimelineValueKind): AnimationTimelineValue? {
    return takeIf(Float::isFinite)?.let { value ->
        AnimationTimelineValue(kind = kind, components = listOf(value))
    }
}

private const val MAX_EXACT_FLOAT_INT = 16_777_216

private fun Int.timelineArgb(): AnimationTimelineValue {
    return AnimationTimelineValue(
        kind = AnimationTimelineValueKind.Argb,
        components = listOf(
            ((this ushr 24) and 0xff).toFloat(),
            ((this ushr 16) and 0xff).toFloat(),
            ((this ushr 8) and 0xff).toFloat(),
            (this and 0xff).toFloat(),
        ),
    )
}

private fun FiniteAnimationSpec.timelineFamily(): AnimationTimelineSpecFamily {
    return when (this) {
        is TweenSpec -> AnimationTimelineSpecFamily.Tween
        is SpringSpec -> AnimationTimelineSpecFamily.Spring
        is KeyframesSpec -> AnimationTimelineSpecFamily.Keyframes
        is SnapSpec -> AnimationTimelineSpecFamily.Snap
        is RepeatableSpec -> AnimationTimelineSpecFamily.Repeatable
    }
}

private fun AnimationEndReason.timelineCondition(): AnimationTimelineTerminalCondition {
    return when (this) {
        AnimationEndReason.Finished,
        AnimationEndReason.BoundReached,
        -> AnimationTimelineTerminalCondition.Finished
        AnimationEndReason.DurationLimitReached ->
            AnimationTimelineTerminalCondition.DurationLimitReached
    }
}

private fun Any?.toTimelineStateSummary(): AnimationTimelineStateSummary {
    if (this == null) {
        return AnimationTimelineStateSummary(typeName = "null", displayValue = null)
    }
    val safeValue = when (this) {
        is Boolean -> if (this) "true" else "false"
        is Byte -> toString()
        is Short -> toString()
        is Int -> toString()
        is Long -> toString()
        is Float -> toString()
        is Double -> toString()
        is Char -> toString()
        is Enum<*> -> name
        else -> null
    }
    return AnimationTimelineStateSummary(
        typeName = javaClass.name.take(MAX_TIMELINE_TEXT_LENGTH),
        displayValue = safeValue?.take(MAX_TIMELINE_TEXT_LENGTH),
    )
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
    DisposableEffect(transition) {
        transition.attachTimelineTooling()
        onDispose(transition::detachTimelineTooling)
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
