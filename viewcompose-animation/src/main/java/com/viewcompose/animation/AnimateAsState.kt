package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.runAnimation
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.LaunchedEffect
import com.viewcompose.widget.core.LocalAnimationCoroutineContext
import com.viewcompose.widget.core.LocalMonotonicFrameClock
import com.viewcompose.widget.core.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * 将目标值动画为可观察 [State]，目标变化时从当前值启动新动画。
 * Animates a target value into observable [State], starting a new animation from the current value
 * when the target changes.
 */
fun <T> animateValueAsState(
    targetValue: T,
    converter: AnimationConverter<T>,
    animationSpec: AnimationSpec = tween(),
): State<T> {
    val state = remember {
        mutableStateOf(targetValue)
    }
    val frameClock = LocalMonotonicFrameClock.current
    val animationCoroutineContext = LocalAnimationCoroutineContext.current
    require(animationCoroutineContext[Job] == null) {
        "Animation coroutine context must not contain a Job."
    }
    LaunchedEffect(targetValue, animationSpec, converter, frameClock, animationCoroutineContext) {
        withContext(animationCoroutineContext) {
            runAnimation(
                frameClock = frameClock,
                startValue = state.value,
                endValue = targetValue,
                animationSpec = animationSpec,
                converter = converter,
            ) { next ->
                state.value = next
            }
        }
    }
    return state
}

fun animateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec = tween(),
): State<Float> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationConverters.Float,
        animationSpec = animationSpec,
    )
}

fun animateIntAsState(
    targetValue: Int,
    animationSpec: AnimationSpec = tween(),
): State<Int> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationConverters.Int,
        animationSpec = animationSpec,
    )
}

fun animateColorAsState(
    targetValue: Int,
    animationSpec: AnimationSpec = tween(),
): State<Int> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationConverters.ColorInt,
        animationSpec = animationSpec,
    )
}

fun animateDpAsState(
    targetValue: Int,
    animationSpec: AnimationSpec = tween(),
): State<Int> {
    return animateIntAsState(
        targetValue = targetValue,
        animationSpec = animationSpec,
    )
}
