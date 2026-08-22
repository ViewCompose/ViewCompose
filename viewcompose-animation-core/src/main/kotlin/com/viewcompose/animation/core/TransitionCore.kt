package com.viewcompose.animation.core

/**
 * Coordinates segment state and one shared timeline for a multi-channel transition.
 *
 * The coordinator does not sample values, own a clock, launch coroutines, or synchronize access.
 * A higher layer calls [updateTarget], lets every channel contribute its duration through
 * [registerDuration] or replaces the complete dynamic-channel maximum through [replaceDuration],
 * then advances the common time with [updatePlayTime]. Explicit control may instead use
 * [seekToPlayTime], [restartRunningSegment], and [snapTo]. All calls must be serialized by the
 * owning transition runtime.
 *
 * During retargeting, a new conceptual segment starts from the previous segment's target rather
 * than from each channel's sampled value. Channel samplers are responsible for preserving visual
 * continuity when that distinction matters.
 *
 * @sample com.viewcompose.animation.core.samples.transitionCoreSample
 *
 * @param S state type that identifies transition endpoints
 * @param initialState committed, target, and segment state exposed before the first update
 */
class TransitionCore<S>(
    initialState: S,
) {
    /**
     * Returns the last fully committed state.
     *
     * It remains unchanged while a segment is running and advances to [segmentTargetState] only
     * when that segment finishes.
     */
    var currentState: S = initialState
        private set

    /**
     * Returns the latest target requested by [updateTarget].
     *
     * During a running segment this differs from [currentState].
     */
    var targetState: S = initialState
        private set

    /** Returns the logical initial state captured for the current segment. */
    var segmentInitialState: S = initialState
        private set

    /** Returns the logical target state captured for the current segment. */
    var segmentTargetState: S = initialState
        private set

    /**
     * Returns a monotonically increasing identity for accepted segment snapshots.
     *
     * A changed target, unfinished restart, or changed snap increments this value. Repeating the
     * existing target or an identical settled snap does not.
     */
    var segmentVersion: Long = 0L
        private set

    /**
     * Returns the normalized play time of the active or most recently completed segment.
     *
     * A new segment resets it to zero. Completion pins it to [segmentDurationNanos], while [snapTo]
     * publishes an idle zero-time snapshot.
     */
    var playTimeNanos: Long = 0L
        private set

    /**
     * Returns the maximum duration registered by channels for the current segment, in nanoseconds.
     *
     * A new segment starts at one nanosecond so it can finish even when every channel reports a
     * non-positive duration.
     */
    var segmentDurationNanos: Long = 1L
        private set

    /** Returns `true` while the current segment has not committed its target state. */
    var isRunning: Boolean = false
        private set

    /**
     * Starts a new segment when [target] differs from the current segment target.
     *
     * An equal target is idempotent and preserves timing and [segmentVersion]. When retargeting a
     * running segment, [segmentInitialState] becomes the previous segment target. Otherwise it uses
     * [currentState]. A segment whose endpoints compare equal commits immediately.
     *
     * @param target state requested for the next committed endpoint
     */
    fun updateTarget(target: S) {
        if (target == segmentTargetState) {
            this.targetState = target
            return
        }
        segmentInitialState = if (isRunning) {
            // One shared logical boundary keeps independently registered channels on one segment.
            segmentTargetState
        } else {
            currentState
        }
        segmentTargetState = target
        targetState = target
        playTimeNanos = 0L
        segmentDurationNanos = 1L
        segmentVersion += 1L
        isRunning = segmentInitialState != segmentTargetState
        if (!isRunning) {
            currentState = target
        }
    }

    /**
     * Contributes one channel duration to the active segment.
     *
     * The coordinator retains the maximum registered value so shorter channels can settle while
     * longer channels continue. Non-positive values normalize to one nanosecond. Calls made while
     * no segment is running are ignored. Register all channel durations before advancing time when
     * a deterministic first frame is required.
     *
     * @param durationNanos channel duration in nanoseconds
     */
    fun registerDuration(durationNanos: Long) {
        if (!isRunning) return
        val normalized = durationNanos.coerceAtLeast(1L)
        if (normalized > segmentDurationNanos) {
            segmentDurationNanos = normalized
        }
    }

    /**
     * Replaces the duration of the active or most recently completed segment.
     *
     * This is the dynamic-channel counterpart to [registerDuration]. A composition owner calls it
     * after recomputing the longest duration from its complete committed channel set, including
     * after a channel is removed. Non-positive input normalizes to one nanosecond. Shrinking an
     * active duration to or below [playTimeNanos] commits the target immediately; changing the
     * duration of an idle segment does not restart it.
     *
     * @sample com.viewcompose.animation.core.samples.transitionCoreSample
     *
     * @param durationNanos replacement segment duration in nanoseconds
     */
    fun replaceDuration(durationNanos: Long) {
        segmentDurationNanos = durationNanos.coerceAtLeast(1L)
        if (isRunning && playTimeNanos >= segmentDurationNanos) {
            finishRunningSegment()
        }
    }

    /**
     * Replaces the active segment's shared play time.
     *
     * Negative values normalize to zero. Reaching or exceeding [segmentDurationNanos] commits the
     * segment through [finishRunningSegment]. Calls made while idle are ignored; time is not
     * required to increase monotonically, so callers may seek backward before completion.
     *
     * @param playTimeNanos new segment-relative play time in nanoseconds
     */
    fun updatePlayTime(playTimeNanos: Long) {
        if (!isRunning) return
        this.playTimeNanos = playTimeNanos.coerceAtLeast(0L)
        if (this.playTimeNanos >= segmentDurationNanos) {
            finishRunningSegment()
        }
    }

    /**
     * Samples the current segment at an explicit time without owning an autonomous clock.
     *
     * Time clamps to `0..segmentDurationNanos`. Sampling the terminal time commits the target;
     * seeking backward from that endpoint reactivates the same segment and restores its logical
     * initial state until the endpoint is reached again. Calls are ignored before any unequal
     * segment exists. The higher layer remains responsible for excluding a simultaneous frame-loop
     * writer.
     *
     * @sample com.viewcompose.animation.core.samples.transitionCoreSample
     *
     * @param playTimeNanos requested segment-relative time in nanoseconds
     */
    fun seekToPlayTime(playTimeNanos: Long) {
        if (segmentInitialState == segmentTargetState) return
        this.playTimeNanos = playTimeNanos.coerceIn(0L, segmentDurationNanos)
        if (this.playTimeNanos >= segmentDurationNanos) {
            finishRunningSegment()
        } else {
            currentState = segmentInitialState
            targetState = segmentTargetState
            isRunning = true
        }
    }

    /**
     * Starts a fresh timing segment toward the current target when an unfinished sample resumes.
     *
     * The current logical state becomes the new segment initial state, play time resets to zero,
     * duration resets to one nanosecond for channel registration, and [segmentVersion] advances.
     * A settled transition is unchanged. Channel owners freeze their latest sampled values when
     * they observe the new version; this coordinator does not own channel values or velocities.
     *
     * @sample com.viewcompose.animation.core.samples.transitionCoreSample
     */
    fun restartRunningSegment() {
        if (currentState == targetState) return
        segmentInitialState = currentState
        segmentTargetState = targetState
        playTimeNanos = 0L
        segmentDurationNanos = 1L
        segmentVersion += 1L
        isRunning = true
    }

    /**
     * Atomically collapses the coordinator onto [target] without creating a running segment.
     *
     * Current, target, and both segment endpoints become [target], play time resets to zero, and
     * duration resets to one nanosecond. A changed snapshot advances [segmentVersion] so channel
     * owners discard the previous segment; an already identical idle snapshot is unchanged.
     *
     * @sample com.viewcompose.animation.core.samples.transitionCoreSample
     *
     * @param target logical endpoint to publish as the complete idle snapshot
     */
    fun snapTo(target: S) {
        if (
            !isRunning &&
            currentState == target &&
            targetState == target &&
            segmentInitialState == target &&
            segmentTargetState == target &&
            playTimeNanos == 0L &&
            segmentDurationNanos == 1L
        ) {
            return
        }
        currentState = target
        targetState = target
        segmentInitialState = target
        segmentTargetState = target
        playTimeNanos = 0L
        segmentDurationNanos = 1L
        segmentVersion += 1L
        isRunning = false
    }

    /**
     * Commits the active segment immediately at its registered terminal time.
     *
     * The operation is idempotent while idle. It updates [currentState] and [targetState], pins
     * [playTimeNanos] to [segmentDurationNanos], and clears [isRunning].
     */
    fun finishRunningSegment() {
        if (!isRunning) return
        currentState = segmentTargetState
        targetState = segmentTargetState
        playTimeNanos = segmentDurationNanos
        isRunning = false
    }
}
