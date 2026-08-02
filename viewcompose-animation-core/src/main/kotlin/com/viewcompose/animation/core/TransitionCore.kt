package com.viewcompose.animation.core

/**
 * Coordinates segment state and one shared timeline for a multi-channel transition.
 *
 * The coordinator does not sample values, own a clock, launch coroutines, or synchronize access.
 * A higher layer calls [updateTarget], lets every channel contribute its duration through
 * [registerDuration], then advances the common time with [updatePlayTime]. All calls must be
 * serialized by the owning transition runtime.
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
     * Returns a monotonically increasing identity for target-changing segments.
     *
     * Repeating the existing segment target does not increment this value.
     */
    var segmentVersion: Long = 0L
        private set

    /**
     * Returns the normalized play time of the active or most recently completed segment.
     *
     * A new segment resets it to zero. Completion pins it to [segmentDurationNanos].
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
