package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.InfiniteRepeatableSpec
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.runAnimation
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
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
 * Scopes the declaration of composition-owned infinite animation channels.
 *
 * The object is a marker used by the extension API; each `animate*` call position owns its state and
 * launched effect. It does not expose start, stop, or disposal methods. Removing a channel from
 * composition cancels that channel's effect, and removing the remembered transition removes all
 * channels declared beneath that composition path.
 */
class InfiniteTransition internal constructor()

/**
 * Remembers an [InfiniteTransition] at the current composition call position.
 *
 * [label] is reserved for diagnostics and does not currently change identity or runtime behavior.
 * The instance is forgotten when its composition slot leaves the tree.
 *
 * @sample com.viewcompose.animation.samples.infiniteTransitionSample
 *
 * @param label optional diagnostic label reserved for tooling
 * @return a stable marker used to declare infinite animation channels
 */
fun rememberInfiniteTransition(
    label: String = "",
): InfiniteTransition {
    @Suppress("UNUSED_PARAMETER")
    val ignored = label
    return remember { InfiniteTransition() }
}

/**
 * Declares an infinitely repeating [Float] channel.
 *
 * @receiver remembered transition marker that scopes the channel API
 * @param initialValue value used at composition and at each restart cycle boundary
 * @param targetValue value reached at the end of a forward cycle
 * @param animationSpec finite cycle and restart/reverse policy
 * @return stable composition-owned state containing the latest sample
 */
fun InfiniteTransition.animateFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec = infiniteRepeatable(
        animation = tween(),
    ),
): State<Float> {
    return animateValue(
        initialValue = initialValue,
        targetValue = targetValue,
        converter = AnimationConverters.Float,
        animationSpec = animationSpec,
    )
}

/**
 * Declares an infinitely repeating [Int] channel with truncating interpolation.
 *
 * @receiver remembered transition marker that scopes the channel API
 * @param initialValue integer used at composition and restart boundaries
 * @param targetValue integer reached at the end of a forward cycle
 * @param animationSpec finite cycle and restart/reverse policy
 * @return stable state containing the latest integer sample
 */
fun InfiniteTransition.animateInt(
    initialValue: Int,
    targetValue: Int,
    animationSpec: InfiniteRepeatableSpec = infiniteRepeatable(
        animation = tween(),
    ),
): State<Int> {
    return animateValue(
        initialValue = initialValue,
        targetValue = targetValue,
        converter = AnimationConverters.Int,
        animationSpec = animationSpec,
    )
}

/**
 * Declares an infinitely repeating packed ARGB channel.
 *
 * Channels interpolate encoded alpha, red, green, and blue values independently; interpolation is
 * not gamma-correct or color-space aware.
 *
 * @receiver remembered transition marker that scopes the channel API
 * @param initialValue packed ARGB value used at composition and restart boundaries
 * @param targetValue packed ARGB value reached at the end of a forward cycle
 * @param animationSpec finite cycle and restart/reverse policy
 * @return stable state containing the latest packed ARGB sample
 */
fun InfiniteTransition.animateColor(
    initialValue: Int,
    targetValue: Int,
    animationSpec: InfiniteRepeatableSpec = infiniteRepeatable(
        animation = tween(),
    ),
): State<Int> {
    return animateValue(
        initialValue = initialValue,
        targetValue = targetValue,
        converter = AnimationConverters.ColorInt,
        animationSpec = animationSpec,
    )
}

/**
 * Declares an infinitely repeating density-independent scalar channel.
 *
 * @receiver remembered transition marker that scopes the channel API
 * @param initialValue [UiDp] used at composition and restart boundaries
 * @param targetValue [UiDp] reached at the end of a forward cycle
 * @param animationSpec finite cycle and restart/reverse policy
 * @return stable state containing the latest density-independent sample
 */
fun InfiniteTransition.animateDp(
    initialValue: UiDp,
    targetValue: UiDp,
    animationSpec: InfiniteRepeatableSpec = infiniteRepeatable(
        animation = tween(),
    ),
): State<UiDp> {
    return animateValue(
        initialValue = initialValue,
        targetValue = targetValue,
        converter = AnimationUnitConverters.Dp,
        animationSpec = animationSpec,
    )
}

/**
 * Declares a custom infinitely repeating channel at the current composition call position.
 *
 * The first composition exposes [initialValue], then a launched effect repeatedly runs
 * [InfiniteRepeatableSpec.animation]. [RepeatMode.Reverse] swaps endpoints after each completed
 * cycle; restart mode republishes [initialValue] between cycles. Equal endpoints publish the initial
 * value without awaiting frames.
 *
 * Changing endpoints, [animationSpec], the frame clock, or the animation coroutine context cancels
 * and restarts the effect from the newly supplied [initialValue], not from the previous sample.
 * [converter] is expected to remain stable; changing it alone is not an effect restart key. The
 * animation context must not contain a [Job]. Leaving composition cancels the loop.
 *
 * @sample com.viewcompose.animation.samples.infiniteTransitionSample
 *
 * @param T domain value represented by [converter]
 * @receiver remembered transition marker that scopes the channel API
 * @param initialValue value published before the first frame and at restart boundaries
 * @param targetValue forward-cycle target value
 * @param converter stable per-dimension converter used for all samples
 * @param animationSpec cycle specification and restart/reverse policy
 * @return stable composition-owned state containing the latest sample
 * @throws IllegalArgumentException if [LocalAnimationCoroutineContext] contains a [Job]
 */
fun <T> InfiniteTransition.animateValue(
    initialValue: T,
    targetValue: T,
    converter: AnimationConverter<T>,
    animationSpec: InfiniteRepeatableSpec = infiniteRepeatable(
        animation = tween(),
    ),
): State<T> {
    val valueState = remember {
        mutableStateOf(initialValue)
    }
    val frameClock = LocalMonotonicFrameClock.current
    val animationCoroutineContext = LocalAnimationCoroutineContext.current
    require(animationCoroutineContext[Job] == null) {
        "Animation coroutine context must not contain a Job."
    }
    LaunchedEffect(initialValue, targetValue, animationSpec, frameClock, animationCoroutineContext) {
        if (initialValue == targetValue) {
            valueState.value = initialValue
            return@LaunchedEffect
        }
        withContext(animationCoroutineContext) {
            var from = initialValue
            var to = targetValue
            while (isActive) {
                // Finite runs preserve a frame suspension point even when the cycle snaps.
                runAnimation(
                    frameClock = frameClock,
                    startValue = from,
                    endValue = to,
                    animationSpec = animationSpec.animation,
                    converter = converter,
                ) { next ->
                    valueState.value = next
                }
                if (animationSpec.repeatMode == RepeatMode.Reverse) {
                    val swap = from
                    from = to
                    to = swap
                } else {
                    valueState.value = initialValue
                }
            }
        }
    }
    return valueState
}
