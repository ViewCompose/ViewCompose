package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.LocalAnimationCoroutineContext
import com.viewcompose.ui.foundation.LocalMonotonicFrameClock
import com.viewcompose.ui.foundation.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * Returns composition-owned [State] that animates toward [targetValue].
 *
 * The first composition exposes [targetValue] immediately. A later change to the target,
 * [animationSpec], [converter], frame clock, or animation coroutine context cancels the previous
 * launched effect and starts from the last published value. Leaving the composition cancels the
 * animation and retains no independent scope.
 *
 * The current [LocalAnimationCoroutineContext] must not contain a [Job]; the composition-owned job
 * remains the cancellation parent while the supplied context may select a dispatcher or other
 * context elements. Samples are published on that context and invalidate readers of the returned
 * state.
 *
 * @sample com.viewcompose.animation.samples.animateValueAsStateSample
 *
 * @param T domain value represented by [converter]
 * @param targetValue value exposed immediately on first composition and animated toward thereafter
 * @param converter stable per-dimension converter used for every sample
 * @param animationSpec timing policy for the current target change
 * @return stable observable state owned by this composition call position
 * @throws IllegalArgumentException if [LocalAnimationCoroutineContext] contains a [Job]
 */
fun <T, V> animateValueAsState(
    targetValue: T,
    converter: AnimationConverter<T, V>,
    animationSpec: FiniteAnimationSpec = tween(),
): State<T> {
    val frameClock = LocalMonotonicFrameClock.current
    val animatable = remember(converter) {
        Animatable(
            initialValue = targetValue,
            converter = converter,
            defaultFrameClock = frameClock,
        )
    }
    animatable.bindFrameClock(frameClock)
    val animationCoroutineContext = LocalAnimationCoroutineContext.current
    require(animationCoroutineContext[Job] == null) {
        "Animation coroutine context must not contain a Job."
    }
    LaunchedEffect(targetValue, animationSpec, converter, frameClock, animationCoroutineContext) {
        withContext(animationCoroutineContext) {
            animatable.animateTo(
                targetValue = targetValue,
                animationSpec = animationSpec,
            )
        }
    }
    return animatable.asState
}

/**
 * Returns composition-owned state that animates a [Float] target.
 *
 * @sample com.viewcompose.animation.samples.animateAsStateSample
 *
 * @param targetValue float value requested by the current composition
 * @param animationSpec timing policy used after the first target changes
 * @return stable state containing the latest interpolated value
 */
fun animateFloatAsState(
    targetValue: Float,
    animationSpec: FiniteAnimationSpec = tween(),
): State<Float> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationConverters.Float,
        animationSpec = animationSpec,
    )
}

/**
 * Returns composition-owned state that animates an [Int] target with truncating interpolation.
 *
 * @param targetValue integer requested by the current composition
 * @param animationSpec timing policy used after the first target changes
 * @return stable state containing the latest integer sample
 */
fun animateIntAsState(
    targetValue: Int,
    animationSpec: FiniteAnimationSpec = tween(),
): State<Int> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationConverters.Int,
        animationSpec = animationSpec,
    )
}

/**
 * Returns composition-owned state that animates a packed ARGB color by encoded channel.
 *
 * Interpolation is not gamma-correct or color-space aware.
 *
 * @param targetValue packed ARGB color requested by the current composition
 * @param animationSpec timing policy used after the first target changes
 * @return stable state containing the latest packed ARGB sample
 */
fun animateColorAsState(
    targetValue: Int,
    animationSpec: FiniteAnimationSpec = tween(),
): State<Int> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationConverters.ColorInt,
        animationSpec = animationSpec,
    )
}

/**
 * Returns composition-owned state that animates a density-independent scalar.
 *
 * This interpolates the [UiDp.value] number and does not resolve pixels; density changes therefore
 * do not restart the animation by themselves.
 *
 * @param targetValue density-independent value requested by the current composition
 * @param animationSpec timing policy used after the first target changes
 * @return stable state containing the latest [UiDp] sample
 */
fun animateDpAsState(
    targetValue: UiDp,
    animationSpec: FiniteAnimationSpec = tween(),
): State<UiDp> {
    return animateValueAsState(
        targetValue = targetValue,
        converter = AnimationUnitConverters.Dp,
        animationSpec = animationSpec,
    )
}
