package com.viewcompose.animation.samples

import com.viewcompose.animation.Animatable
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.AnimatedContent
import com.viewcompose.animation.ContentSlideDirection
import com.viewcompose.animation.ContentTransform
import com.viewcompose.animation.Crossfade
import com.viewcompose.animation.EnterTransition
import com.viewcompose.animation.ExitTransition
import com.viewcompose.animation.MutableTransitionState
import com.viewcompose.animation.SeekableTransitionState
import com.viewcompose.animation.SizeTransform
import com.viewcompose.animation.SlideDirection
import com.viewcompose.animation.animateContentSize
import com.viewcompose.animation.animateFloat
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.animateValueAsState
import com.viewcompose.animation.expandVertically
import com.viewcompose.animation.expandIn
import com.viewcompose.animation.fadeIn
import com.viewcompose.animation.fadeOut
import com.viewcompose.animation.rememberAnimatable
import com.viewcompose.animation.rememberInfiniteTransition
import com.viewcompose.animation.rememberTransition
import com.viewcompose.animation.shrinkVertically
import com.viewcompose.animation.shrinkOut
import com.viewcompose.animation.slideInHorizontally
import com.viewcompose.animation.slideInVertically
import com.viewcompose.animation.slideOutHorizontally
import com.viewcompose.animation.slideOutVertically
import com.viewcompose.animation.scaleIn
import com.viewcompose.animation.scaleOut
import com.viewcompose.animation.togetherWith
import com.viewcompose.animation.updateTransition
import com.viewcompose.animation.using
import com.viewcompose.animation.interpolateUiShape
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.exponentialDecay
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.State
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.shape.UiShape
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
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 240f),
    )
}

/** Runs an imperative animation in a caller-owned coroutine with an explicit clock. */
suspend fun animatableSample(frameClock: MonotonicFrameClock): Float {
    val value = Animatable(
        initialValue = 0f,
        converter = AnimationConverters.Float,
        defaultFrameClock = frameClock,
    )
    value.updateBounds(lowerBound = -0.5f, upperBound = 1.5f)
    value.animateTo(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 260f),
        initialVelocity = AnimationVelocity(2f),
    )
    value.animateDecay(
        initialVelocity = AnimationVelocity(-0.5f),
        animationSpec = exponentialDecay(),
    )
    return value.value
}

/** Remembers an imperative animated value bound to the current composition frame clock. */
fun UiTreeBuilder.rememberAnimatableSample(): Animatable<Float, Float> {
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
        transitionSpec = { tween(durationMillis = 180) },
        targetValueByState = { state -> if (state == PanelState.Expanded) 1f else 0.6f },
    )
    val height = transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.82f, stiffness = 230f) },
        targetValueByState = { state -> if (state == PanelState.Expanded) 240.dp else 80.dp },
    )
    return TransitionValues(alpha = alpha, height = height)
}

/** Declares generic and segment-aware channels owned by an externally seekable transition. */
fun UiTreeBuilder.seekableTransitionSample(
    state: SeekableTransitionState<PanelState>,
): SeekableTransitionValues {
    val transition = rememberTransition(
        transitionState = state,
        label = "seekable panel",
    )
    val point = transition.animateValue(
        converter = PointConverter,
        transitionSpec = {
            if (isTransitioningTo(PanelState.Collapsed, PanelState.Expanded)) {
                tween(durationMillis = 420)
            } else {
                tween(durationMillis = 280)
            }
        },
        targetValueByState = { panelState ->
            if (panelState == PanelState.Expanded) Point(96f, 48f) else Point(0f, 0f)
        },
    )
    val alpha = transition.animateFloat(
        transitionSpec = { tween(durationMillis = 180) },
        targetValueByState = { panelState ->
            if (panelState == PanelState.Expanded) 1f else 0.5f
        },
    )
    return SeekableTransitionValues(
        point = point,
        alpha = alpha,
    )
}

/** Transfers one seekable transition between explicit progress, autonomous, and snapped modes. */
suspend fun driveSeekableTransitionSample(state: SeekableTransitionState<PanelState>) {
    state.seekTo(
        fraction = 0.5f,
        targetState = PanelState.Expanded,
    )
    state.animateTo(PanelState.Expanded)
    state.snapTo(PanelState.Collapsed)
}

/** Combines alpha and vertical size primitives into enter and exit policies. */
fun visibilityTransitionsSample(): Pair<EnterTransition, ExitTransition> {
    val enter = fadeIn(tween(durationMillis = 180)) +
        expandVertically(spring(dampingRatio = 0.86f, stiffness = 280f))
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

/** Shares one visibility timeline across parent slide/scale/reveal and descendant choreography. */
fun UiTreeBuilder.richVisibilityTransitionsSample(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 160)) +
            slideInHorizontally(
                from = SlideDirection.Start,
                animationSpec = tween(durationMillis = 240),
                distanceFraction = 0.5f,
            ) +
            scaleIn(
                animationSpec = tween(durationMillis = 220),
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(0f, 1f),
            ) +
            expandIn(
                animationSpec = tween(durationMillis = 240),
                alignment = BoxAlignment.BottomStart,
            ),
        exit = shrinkOut(
            animationSpec = tween(durationMillis = 220),
            alignment = BoxAlignment.BottomEnd,
        ) + scaleOut(
            animationSpec = tween(durationMillis = 180),
            targetScale = 0.94f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + slideOutHorizontally(
            towards = SlideDirection.End,
            animationSpec = tween(durationMillis = 220),
            distanceFraction = 0.35f,
        ) + fadeOut(tween(durationMillis = 140)),
    ) {
        Text("Shared transition running: ${transition.isRunning}")
        AnimatedEnterExit(
            enter = slideInVertically(
                from = SlideDirection.Down,
                animationSpec = tween(durationMillis = 320),
            ),
            exit = slideOutVertically(
                towards = SlideDirection.Up,
                animationSpec = tween(durationMillis = 320),
            ),
        ) {
            Text("Descendant joins the parent timeline")
        }
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
fun UiTreeBuilder.crossfadeSample(selection: String) {
    Crossfade(targetState = selection) { state ->
        Text("Selected: $state")
    }
    Crossfade(targetState = selection, animationSpec = tween(durationMillis = 160)) { state ->
        Text("Summary: $state")
    }
}

/** Replaces keyed content with pair-specific slide, scale, fade, and size policies. */
fun UiTreeBuilder.animatedContentSample(page: Int) {
    AnimatedContent(
        targetState = page,
        contentKey = { it },
        transitionSpec = {
            val forward = when {
                isTransitioningTo(0, 1) -> true
                isTransitioningTo(1, 0) -> false
                else -> targetState > initialState
            }
            val incomingEdge = if (forward) ContentSlideDirection.End else ContentSlideDirection.Start
            val outgoingEdge = if (forward) ContentSlideDirection.Start else ContentSlideDirection.End
            val enter = fadeIn(tween(durationMillis = 180)) +
                slideIntoContainer(
                    from = incomingEdge,
                    animationSpec = tween(durationMillis = 240),
                    distanceFraction = 0.35f,
                ) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(durationMillis = 240),
                )
            val exit = fadeOut(tween(durationMillis = 140)) +
                slideOutOfContainer(
                    towards = outgoingEdge,
                    animationSpec = tween(durationMillis = 220),
                    distanceFraction = 0.2f,
                ) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(durationMillis = 220),
                )
            (enter togetherWith exit).copy(
                targetContentZIndex = 1f,
            ) using SizeTransform(
                animationSpec = tween(durationMillis = 240),
                clip = true,
            )
        },
    ) { value ->
        Text("Page $value")
    }
}

/** Adds measured-size animation to a node modifier. */
fun animateContentSizeSample(): Modifier {
    return Modifier.animateContentSize(
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 230f),
    )
}

/** Resolves one frame of a compatible continuous-corner shape transition. */
fun uiShapeInterpolationSample(progress: Float): UiShape {
    return interpolateUiShape(
        start = UiShape.continuous(8.dp),
        end = UiShape.continuous(24.dp),
        fraction = progress,
    ).shape
}

data class Point(
    val x: Float,
    val y: Float,
)

data class TransitionValues(
    val alpha: State<Float>,
    val height: State<UiDp>,
)

data class SeekableTransitionValues(
    val point: State<Point>,
    val alpha: State<Float>,
)

enum class PanelState {
    Collapsed,
    Expanded,
}

private object PointConverter : AnimationConverter<Point, Point> {
    override val vectorSize: Int = 2
    override val zeroVelocity: Point = Point(0f, 0f)
    override val visibilityThreshold: Point = Point(0.01f, 0.01f)

    override fun convertToVector(value: Point, destination: FloatArray) {
        destination[0] = value.x
        destination[1] = value.y
    }

    override fun convertFromVector(vector: FloatArray): Point {
        return Point(
            x = vector[0],
            y = vector[1],
        )
    }

    override fun convertVelocityToVector(velocity: Point, destination: FloatArray) {
        convertToVector(velocity, destination)
    }

    override fun convertVelocityFromVector(vector: FloatArray): Point = convertFromVector(vector)
}
