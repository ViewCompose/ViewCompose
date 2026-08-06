package com.viewcompose.animation.samples

import com.viewcompose.animation.Animatable
import com.viewcompose.animation.AnimatedContent
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.Crossfade
import com.viewcompose.animation.EnterTransition
import com.viewcompose.animation.ExitTransition
import com.viewcompose.animation.MutableTransitionState
import com.viewcompose.animation.animateContentSize
import com.viewcompose.animation.animateFloat
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.animateValueAsState
import com.viewcompose.animation.expandVertically
import com.viewcompose.animation.fadeIn
import com.viewcompose.animation.fadeOut
import com.viewcompose.animation.rememberAnimatable
import com.viewcompose.animation.rememberInfiniteTransition
import com.viewcompose.animation.shrinkVertically
import com.viewcompose.animation.updateTransition
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

/** Animates a scalar target owned by the current composition call position. */
fun UiTreeBuilder.animateAsStateSample(target: Float): State<Float> {
    return animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 220),
    )
}

/** Supplies a custom converter for a composition-owned application value. */
fun UiTreeBuilder.animateValueAsStateSample(target: Point): State<Point> {
    return animateValueAsState(
        targetValue = target,
        converter = PointConverter,
        animationSpec = spring(durationMillis = 360),
    )
}

/** Runs an imperative animation in a caller-owned coroutine with an explicit clock. */
suspend fun animatableSample(frameClock: MonotonicFrameClock): Float {
    val value = Animatable(
        initialValue = 0f,
        converter = AnimationConverters.Float,
        defaultFrameClock = frameClock,
    )
    value.animateTo(1f, tween(durationMillis = 180))
    return value.value
}

/** Remembers an imperative animated value bound to the current composition frame clock. */
fun UiTreeBuilder.rememberAnimatableSample(): Animatable<Float> {
    return rememberAnimatable(
        initialValue = 0f,
        converter = AnimationConverters.Float,
    )
}

/** Declares a continuously reversing pulse channel. */
fun UiTreeBuilder.infiniteTransitionSample(): State<Float> {
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
    )
}

/** Derives multiple values from one shared logical transition. */
fun UiTreeBuilder.transitionSample(expanded: Boolean): TransitionValues {
    val transition = updateTransition(
        targetState = if (expanded) PanelState.Expanded else PanelState.Collapsed,
        label = "panel",
    )
    val alpha = transition.animateFloat(
        animationSpec = { tween(durationMillis = 180) },
        targetValueByState = { state -> if (state == PanelState.Expanded) 1f else 0.6f },
    )
    val height = transition.animateDp(
        animationSpec = { spring(durationMillis = 320) },
        targetValueByState = { state -> if (state == PanelState.Expanded) 240.dp else 80.dp },
    )
    return TransitionValues(alpha = alpha, height = height)
}

/** Combines alpha and vertical size primitives into enter and exit policies. */
fun visibilityTransitionsSample(): Pair<EnterTransition, ExitTransition> {
    val enter = fadeIn(tween(durationMillis = 180)) +
        expandVertically(spring(durationMillis = 260))
    val exit = shrinkVertically(tween(durationMillis = 160)) +
        fadeOut(tween(durationMillis = 120))
    return enter to exit
}

/** Keeps content mounted until its visibility exit transition completes. */
fun UiTreeBuilder.animatedVisibilitySample(visible: Boolean) {
    val (enter, exit) = visibilityTransitionsSample()
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
    ) {
        Text("Details")
    }
}

/** Retains observable current and idle state outside the visibility call. */
fun UiTreeBuilder.mutableTransitionStateSample(showDetails: Boolean): MutableTransitionState<Boolean> {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = showDetails
    AnimatedVisibility(visibleState = visibility) {
        Text("Details")
    }
    return visibility
}

/** Cross-fades two state-derived content subtrees. */
fun UiTreeBuilder.animatedContentSample(selection: String) {
    AnimatedContent(targetState = selection) { state ->
        Text("Selected: $state")
    }
    Crossfade(targetState = selection, animationSpec = tween(durationMillis = 160)) { state ->
        Text("Summary: $state")
    }
}

/** Adds measured-size animation to a node modifier. */
fun animateContentSizeSample(): Modifier {
    return Modifier.animateContentSize(
        animationSpec = spring(durationMillis = 320),
    )
}

data class Point(
    val x: Float,
    val y: Float,
)

data class TransitionValues(
    val alpha: State<Float>,
    val height: State<UiDp>,
)

enum class PanelState {
    Collapsed,
    Expanded,
}

private object PointConverter : AnimationConverter<Point> {
    override fun toVector(value: Point): FloatArray = floatArrayOf(value.x, value.y)

    override fun fromVector(vector: FloatArray): Point {
        return Point(
            x = vector.getOrElse(0) { 0f },
            y = vector.getOrElse(1) { 0f },
        )
    }
}
