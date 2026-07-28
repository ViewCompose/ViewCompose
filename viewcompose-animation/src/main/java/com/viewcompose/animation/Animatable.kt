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
 * 可由协程驱动的动画状态容器。
 * Coroutine-driven animated state holder.
 *
 * 同一时刻只有最新 mutation 可以发布值；新动画会取消旧动画，避免过期协程回写 UI 状态。
 * Only the newest mutation may publish values; new animations cancel old ones to prevent stale
 * coroutines from writing UI state.
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

    val value: T
        get() = internalState.value

    val targetValue: T
        get() = targetState.value

    val isRunning: Boolean
        get() = runningState.value

    val asState: State<T>
        get() = internalState

    suspend fun snapTo(targetValue: T) {
        val mutation = beginMutation(targetValue)
        try {
            publishValue(mutation.id, targetValue)
        } finally {
            endMutation(mutation.id)
        }
    }

    suspend fun stop() {
        val mutation = beginMutation(internalState.value)
        endMutation(mutation.id)
    }

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
            // 新 mutation 取代旧 mutation；同一个 Job 内的 snap/stop 不需要自我取消。
            // A new mutation replaces the old one; snap/stop from the same Job should not cancel itself.
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
 * 创建并记忆绑定当前 frame clock 的 [Animatable]。
 * Creates and remembers an [Animatable] bound to the current frame clock.
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
