package com.viewcompose.animation.tooling

/** Stable execution state projected for one inspected transition timeline. */
enum class AnimationTimelineRunState {
    /** The transition has no active segment writer. */
    Idle,

    /** The transition is advancing or retaining an unfinished controlled segment. */
    Running,

    /** A running segment was retargeted before reaching its previous endpoint. */
    Interrupted,
}

/** Bounded value families that may be exported without calling application `toString`. */
enum class AnimationTimelineValueKind {
    /** One floating-point scalar. */
    Float,

    /** One integer scalar represented exactly while it fits a `Float`. */
    Int,

    /** One density-independent scalar. */
    Dp,

    /** Encoded alpha, red, green, and blue components in that order. */
    Argb,
}

/** Finite specification family used by one inspected transition channel. */
enum class AnimationTimelineSpecFamily {
    /** Fixed-duration easing with an optional delay. */
    Tween,

    /** Analytic physical spring with an equilibrium or safety-guard terminal condition. */
    Spring,

    /** Timestamped finite keyframes. */
    Keyframes,

    /** Immediate endpoint selection. */
    Snap,

    /** A finite repeated duration-based specification. */
    Repeatable,

    /** A sealed finite specification unknown to this tooling projection. */
    Unsupported,
}

/** Terminal condition predicted by the accepted channel evaluator. */
enum class AnimationTimelineTerminalCondition {
    /** Normal finite completion at the requested endpoint. */
    Finished,

    /** A physical evaluator reaches its declared duration safety guard. */
    DurationLimitReached,
}

/**
 * Privacy-safe bounded numeric value used by timeline tooling.
 *
 * [components] contains one value for scalar kinds and four values for [AnimationTimelineValueKind.Argb].
 * Custom animation domains are deliberately reported as unsupported instead of invoking their
 * application-defined formatting or retaining their values in a tooling report.
 *
 * @property kind semantic family of the bounded numeric components
 * @property components one scalar component, or four alpha/red/green/blue components for ARGB
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
data class AnimationTimelineValue(
    val kind: AnimationTimelineValueKind,
    val components: List<Float>,
) {
    init {
        val expectedSize = if (kind == AnimationTimelineValueKind.Argb) 4 else 1
        require(components.size == expectedSize) {
            "Animation timeline $kind values require $expectedSize components."
        }
        require(components.all(Float::isFinite)) {
            "Animation timeline value components must be finite."
        }
    }
}

/**
 * Bounded logical-state description that never calls application `toString`.
 *
 * [displayValue] is present only for primitive numbers, booleans, characters, and enum constants.
 * Other application state types expose their bounded JVM type name and a `null` display value.
 *
 * @property typeName bounded JVM type name that identifies the logical state domain
 * @property displayValue bounded primitive or enum value, or `null` for private/custom state
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
data class AnimationTimelineStateSummary(
    val typeName: String,
    val displayValue: String?,
) {
    init {
        require(typeName.isNotBlank() && typeName.length <= MAX_TIMELINE_TEXT_LENGTH) {
            "Animation timeline state typeName must contain 1..$MAX_TIMELINE_TEXT_LENGTH characters."
        }
        require(displayValue == null || displayValue.length <= MAX_TIMELINE_TEXT_LENGTH) {
            "Animation timeline state displayValue must be null or at most " +
                "$MAX_TIMELINE_TEXT_LENGTH characters."
        }
    }
}

/**
 * Immutable projection of one committed transition channel.
 *
 * Nullable numeric fields identify custom or otherwise unsupported value domains. [name] is a
 * deterministic runtime name scoped to the owning transition, not composition identity.
 *
 * @property identity stable process-lifetime identity scoped to the owning transition
 * @property name deterministic runtime channel name
 * @property specFamily finite evaluator family used by the active segment
 * @property startValue privacy-safe segment start value, or `null` when unsupported
 * @property currentValue privacy-safe value at the snapshot play time, or `null` when unsupported
 * @property targetValue privacy-safe segment target value, or `null` when unsupported
 * @property velocity privacy-safe velocity at the snapshot play time, or `null` when unsupported
 * @property durationNanos duration owned by this channel in the active segment
 * @property finished whether this channel has reached its terminal sample
 * @property terminalCondition predicted normal or physical safety-guard terminal condition
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
data class AnimationTimelineChannelSnapshot(
    val identity: String,
    val name: String,
    val specFamily: AnimationTimelineSpecFamily,
    val startValue: AnimationTimelineValue?,
    val currentValue: AnimationTimelineValue?,
    val targetValue: AnimationTimelineValue?,
    val velocity: AnimationTimelineValue?,
    val durationNanos: Long,
    val finished: Boolean,
    val terminalCondition: AnimationTimelineTerminalCondition,
) {
    init {
        require(identity.isNotBlank() && identity.length <= MAX_TIMELINE_TEXT_LENGTH) {
            "Animation timeline channel identity must be bounded and non-blank."
        }
        require(name.isNotBlank() && name.length <= MAX_TIMELINE_TEXT_LENGTH) {
            "Animation timeline channel name must be bounded and non-blank."
        }
        require(durationNanos >= 0L) {
            "Animation timeline channel durationNanos must be non-negative."
        }
    }
}

/**
 * Immutable request-time projection of one composition-owned transition.
 *
 * The model contains no application object, callback, View, or source text. Implementations must
 * cap [channels] before constructing the snapshot and may omit unsupported values.
 *
 * @property identity stable process-lifetime identity of the transition instance
 * @property label bounded diagnostic label captured when the transition was created
 * @property currentState latest committed logical state
 * @property targetState latest requested logical target
 * @property segmentInitialState logical state from which the active segment started
 * @property segmentTargetState logical state toward which the active segment is moving
 * @property segmentVersion monotonically increasing process-local segment revision
 * @property playTimeNanos non-negative play time within the active segment
 * @property durationNanos longest committed channel duration for the active segment
 * @property runState idle, running, or interrupted execution state
 * @property channels at most [MAX_TIMELINE_CHANNELS] committed channel projections
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
data class AnimationTimelineSnapshot(
    val identity: String,
    val label: String,
    val currentState: AnimationTimelineStateSummary,
    val targetState: AnimationTimelineStateSummary,
    val segmentInitialState: AnimationTimelineStateSummary,
    val segmentTargetState: AnimationTimelineStateSummary,
    val segmentVersion: Long,
    val playTimeNanos: Long,
    val durationNanos: Long,
    val runState: AnimationTimelineRunState,
    val channels: List<AnimationTimelineChannelSnapshot>,
) {
    init {
        require(identity.isNotBlank() && identity.length <= MAX_TIMELINE_TEXT_LENGTH) {
            "Animation timeline identity must be bounded and non-blank."
        }
        require(label.length <= MAX_TIMELINE_TEXT_LENGTH) {
            "Animation timeline label must be at most $MAX_TIMELINE_TEXT_LENGTH characters."
        }
        require(segmentVersion >= 0L) {
            "Animation timeline segmentVersion must be non-negative."
        }
        require(playTimeNanos >= 0L && durationNanos >= 0L) {
            "Animation timeline play time and duration must be non-negative."
        }
        require(channels.size <= MAX_TIMELINE_CHANNELS) {
            "Animation timeline snapshots support at most $MAX_TIMELINE_CHANNELS channels."
        }
        require(channels.map(AnimationTimelineChannelSnapshot::identity).distinct().size == channels.size) {
            "Animation timeline channel identities must be unique within a transition."
        }
    }
}

/**
 * Q3 neutral read-only source retained weakly by optional downstream tooling.
 *
 * [snapshot] is called only on the owning animation thread after an explicit request. It must not
 * mutate the transition or install a clock, listener, or callback.
 *
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
interface AnimationTimelineSource {
    /** Stable process-lifetime identity for this transition instance. */
    val identity: String

    /** Bounded diagnostic label captured when the transition is created. */
    val label: String

    /** Returns one bounded immutable projection of the latest committed transition state. */
    fun snapshot(): AnimationTimelineSnapshot
}

/**
 * Lifecycle handle returned by optional animation timeline tooling.
 *
 * The animation owner performs one [captureRequested] check after accepted state changes. A `false`
 * result must do no additional work. [record] is called only while a finite explicit request is
 * active, and [dispose] is terminal and idempotent.
 *
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
interface AnimationTimelineRegistration {
    /** Returns whether the owning transition is selected by a current bounded capture request. */
    fun captureRequested(): Boolean

    /** Records one already bounded snapshot for the active request. */
    fun record(snapshot: AnimationTimelineSnapshot)

    /** Releases this transition from the downstream registry. */
    fun dispose()
}

/**
 * Q3 optional downstream port for request-driven animation timeline inspection.
 *
 * Runtime artifacts discover at most one implementation. Optional-artifact presence may retain a
 * registered source only weakly so transitions composed before the first request remain
 * discoverable. Registration alone cannot take a snapshot, start a thread or frame loop, perform
 * serialization or file I/O, traverse Views, or publish a report. Concrete receivers must reject
 * non-debuggable processes and require an explicit bounded request before snapshot work. Android
 * and IDE protocols stay outside the animation runtime.
 *
 * @sample com.viewcompose.animation.samples.animationTimelineToolingSample
 */
interface AnimationTimelineTooling {
    /** Registers [source] weakly or returns `null` when the optional provider remains inactive. */
    fun register(source: AnimationTimelineSource): AnimationTimelineRegistration?
}

/**
 * Q3 installs one process-wide optional animation-timeline implementation from a downstream
 * tooling artifact.
 *
 * Tooling artifacts must call this during application-component initialization, before the first
 * transition reads the port. The call is synchronized, performs no file or service discovery, and
 * retains the implementation for the process lifetime. Reinstalling the same instance is
 * idempotent. Distinct instances installed before first use disable the port, while installations
 * after first use are ignored. Both cases leave animation behavior unchanged.
 *
 * Applications do not call this integration hook. Concrete tooling must independently enforce its
 * artifact-presence, debuggable-process, and explicit-request gates.
 *
 * @sample com.viewcompose.animation.samples.installAnimationTimelineToolingSample
 */
fun installAnimationTimelineTooling(tooling: AnimationTimelineTooling) {
    AnimationTimelineToolingDiscovery.install(tooling)
}

/** Maximum channels retained in one neutral transition snapshot. */
const val MAX_TIMELINE_CHANNELS: Int = 32

/** Maximum characters retained in one neutral diagnostic identity, label, or state summary. */
const val MAX_TIMELINE_TEXT_LENGTH: Int = 256
