package com.viewcompose.animation.core

/**
 * 多通道 transition 的纯状态机。
 * Pure state machine for a multi-channel transition.
 */
class TransitionCore<S>(
    initialState: S,
) {
    var currentState: S = initialState
        private set

    var targetState: S = initialState
        private set

    var segmentInitialState: S = initialState
        private set

    var segmentTargetState: S = initialState
        private set

    var segmentVersion: Long = 0L
        private set

    var playTimeNanos: Long = 0L
        private set

    var segmentDurationNanos: Long = 1L
        private set

    var isRunning: Boolean = false
        private set

    fun updateTarget(target: S) {
        if (target == segmentTargetState) {
            this.targetState = target
            return
        }
        segmentInitialState = if (isRunning) {
            // 运行中切换目标时，从上一段目标继续，保证所有通道共享同一段边界。
            // When retargeting while running, continue from the previous target so channels share boundaries.
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

    fun registerDuration(durationNanos: Long) {
        if (!isRunning) return
        // transition 时长取所有动画通道中的最大值，短通道会在各自采样中提前到终点。
        // Transition duration is the max of all channels; shorter channels settle in their own sampler.
        val normalized = durationNanos.coerceAtLeast(1L)
        if (normalized > segmentDurationNanos) {
            segmentDurationNanos = normalized
        }
    }

    fun updatePlayTime(playTimeNanos: Long) {
        if (!isRunning) return
        this.playTimeNanos = playTimeNanos.coerceAtLeast(0L)
        if (this.playTimeNanos >= segmentDurationNanos) {
            finishRunningSegment()
        }
    }

    fun finishRunningSegment() {
        if (!isRunning) return
        currentState = segmentTargetState
        targetState = segmentTargetState
        playTimeNanos = segmentDurationNanos
        isRunning = false
    }
}
