package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.TransitionCore
import com.viewcompose.animation.core.animationDurationNanos
import com.viewcompose.animation.core.sampleAnimationValue
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.LaunchedEffect
import com.viewcompose.widget.core.LocalAnimationCoroutineContext
import com.viewcompose.widget.core.LocalMonotonicFrameClock
import com.viewcompose.widget.core.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    private val segmentDurationNanosHolder = mutableStateOf(1L)
    private val segmentInitialStateHolder = mutableStateOf(initialState)
    private val segmentTargetStateHolder = mutableStateOf(initialState)

    val currentState: S
        get() = currentStateHolder.value

    val targetState: S
        get() = targetStateHolder.value

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
        get() = segmentDurationNanosHolder.value

    internal fun updateTarget(target: S) {
        core.updateTarget(target)
        syncFromCore()
    }

    internal fun registerChannelDuration(durationNanos: Long) {
        core.registerDuration(durationNanos)
        if (segmentDurationNanosHolder.value != core.segmentDurationNanos) {
            segmentDurationNanosHolder.value = core.segmentDurationNanos
        }
    }

    internal fun advanceFrame(version: Long, playTimeNanos: Long) {
        if (core.segmentVersion != version || !core.isRunning) return
        core.updatePlayTime(playTimeNanos)
        syncFromCore()
    }

    internal fun isRunningOn(version: Long): Boolean {
        return core.segmentVersion == version && core.isRunning
    }

    // 中文：组合阶段运行在只读快照里，syncFromCore() 写入的 mirror state 在同一轮读取可能仍是旧值。
    // 因此“是否启动协程/是否注册时长”等控制流必须读取 core 实时值，避免同帧漏启动动画。
    // English: Composition runs in a read snapshot; mirror-state writes from syncFromCore() may still
    // read stale in the same pass. Control-flow decisions (launch/register) must use live core values.
    internal fun runtimeIsRunning(): Boolean = core.isRunning

    internal fun runtimeSegmentVersion(): Long = core.segmentVersion

    private fun syncFromCore() {
        currentStateHolder.value = core.currentState
        targetStateHolder.value = core.targetState
        runningHolder.value = core.isRunning
        segmentVersionHolder.value = core.segmentVersion
        playTimeNanosHolder.value = core.playTimeNanos
        segmentDurationNanosHolder.value = core.segmentDurationNanos
        segmentInitialStateHolder.value = core.segmentInitialState
        segmentTargetStateHolder.value = core.segmentTargetState
    }

    private class ChannelState<T>(
        var segmentVersion: Long,
        var startValue: T,
        var endValue: T,
        var animationSpec: AnimationSpec,
    )

    private fun <T> animateValueInternal(
        converter: AnimationConverter<T>,
        transitionSpec: (initialState: S, targetState: S) -> AnimationSpec,
        segmentEndpoints: (initialState: S, targetState: S, currentValue: T) -> Pair<T, T>,
        valueForSettledState: (S) -> T,
    ): State<T> {
        val outputState = remember(this, converter) {
            mutableStateOf(valueForSettledState(currentState))
        }
        val channelState = remember(this, converter) {
            ChannelState(
                segmentVersion = -1L,
                startValue = outputState.value,
                endValue = outputState.value,
                animationSpec = tween(),
            )
        }
        val running = core.isRunning
        val version = core.segmentVersion
        if (channelState.segmentVersion != version) {
            val segmentInitialState = core.segmentInitialState
            val segmentTargetState = core.segmentTargetState
            val spec = transitionSpec(segmentInitialState, segmentTargetState)
            val (start, end) = segmentEndpoints(
                segmentInitialState,
                segmentTargetState,
                outputState.value,
            )
            channelState.segmentVersion = version
            channelState.animationSpec = spec
            channelState.startValue = start
            channelState.endValue = end
        }
        if (running) {
            registerChannelDuration(
                durationNanos = animationDurationNanos(channelState.animationSpec),
            )
            outputState.value = sampleAnimationValue(
                startValue = channelState.startValue,
                endValue = channelState.endValue,
                animationSpec = channelState.animationSpec,
                converter = converter,
                // 中文：playTime 继续走 mirror state 读取，保证每帧写入能触发观察失效与重组。
                // English: Keep playTime observed via mirror state so per-frame writes trigger invalidation.
                playTimeNanos = playTimeNanosHolder.value,
            )
        } else {
            outputState.value = valueForSettledState(core.targetState)
        }
        return outputState
    }

    fun animateFloat(
        animationSpec: () -> AnimationSpec = { tween() },
        targetValueByState: (S) -> Float,
    ): State<Float> {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        )
    }

    internal fun animateFloatBySegment(
        transitionSpec: (initialState: S, targetState: S) -> AnimationSpec,
        segmentEndpoints: (initialState: S, targetState: S, currentValue: Float) -> Pair<Float, Float>,
        valueForSettledState: (S) -> Float,
    ): State<Float> {
        return animateValueInternal(
            converter = AnimationConverters.Float,
            transitionSpec = transitionSpec,
            segmentEndpoints = segmentEndpoints,
            valueForSettledState = valueForSettledState,
        )
    }

    fun animateInt(
        animationSpec: () -> AnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateValueInternal(
            converter = AnimationConverters.Int,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        )
    }

    fun animateColor(
        animationSpec: () -> AnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateValueInternal(
            converter = AnimationConverters.ColorInt,
            transitionSpec = { _, _ -> animationSpec() },
            segmentEndpoints = { _, target, current ->
                current to targetValueByState(target)
            },
            valueForSettledState = targetValueByState,
        )
    }

    fun animateDp(
        animationSpec: () -> AnimationSpec = { tween() },
        targetValueByState: (S) -> Int,
    ): State<Int> {
        return animateInt(
            animationSpec = animationSpec,
            targetValueByState = targetValueByState,
        )
    }
}

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
