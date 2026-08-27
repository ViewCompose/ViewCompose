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
import com.viewcompose.animation.animateBounds
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
import com.viewcompose.animation.tooling.AnimationTimelineChannelSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineRegistration
import com.viewcompose.animation.tooling.AnimationTimelineRunState
import com.viewcompose.animation.tooling.AnimationTimelineSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineSource
import com.viewcompose.animation.tooling.AnimationTimelineSpecFamily
import com.viewcompose.animation.tooling.AnimationTimelineStateSummary
import com.viewcompose.animation.tooling.AnimationTimelineTerminalCondition
import com.viewcompose.animation.tooling.AnimationTimelineTooling
import com.viewcompose.animation.tooling.AnimationTimelineValue
import com.viewcompose.animation.tooling.AnimationTimelineValueKind
import com.viewcompose.animation.tooling.installAnimationTimelineTooling
import com.viewcompose.runtime.State
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.foundation.BoxScope
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import java.lang.ref.WeakReference

/** Installs an optional downstream timeline port before the first transition starts. */
fun installAnimationTimelineToolingSample(tooling: AnimationTimelineTooling) {
    installAnimationTimelineTooling(tooling)
}

/** Implements the neutral request-time projection without retaining application state. */
fun animationTimelineToolingSample(): AnimationTimelineTooling {
    return object : AnimationTimelineTooling {
        override fun register(source: AnimationTimelineSource): AnimationTimelineRegistration {
            val sourceReference = WeakReference(source)
            return object : AnimationTimelineRegistration {
                override fun captureRequested(): Boolean = false

                override fun record(snapshot: AnimationTimelineSnapshot) {
                    check(snapshot.identity == sourceReference.get()?.identity)
                }

                override fun dispose() {
                    sourceReference.clear()
                }
            }
        }
    }.also {
        // A downstream test provider can construct the same bounded, numeric-only wire model.
        AnimationTimelineSnapshot(
            identity = "transition-1",
            label = "panel",
            currentState = AnimationTimelineStateSummary("boolean", "false"),
            targetState = AnimationTimelineStateSummary("boolean", "true"),
            segmentInitialState = AnimationTimelineStateSummary("boolean", "false"),
            segmentTargetState = AnimationTimelineStateSummary("boolean", "true"),
            segmentVersion = 1L,
            playTimeNanos = 80_000_000L,
            durationNanos = 240_000_000L,
            runState = AnimationTimelineRunState.Running,
            channels = listOf(
                AnimationTimelineChannelSnapshot(
                    identity = "channel-1",
                    name = "Float 1",
                    specFamily = AnimationTimelineSpecFamily.Tween,
                    startValue = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(0f)),
                    currentValue = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(0.33f)),
                    targetValue = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(1f)),
                    velocity = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(4.1f)),
                    durationNanos = 240_000_000L,
                    finished = false,
                    terminalCondition = AnimationTimelineTerminalCondition.Finished,
                ),
            ),
        )
    }
}

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

/** Adds real parent-local position-and-size animation to a node modifier. */
fun animateBoundsSample(): Modifier {
    return Modifier.animateBounds(
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

private fun UiTreeBuilder.documentationTargetAsStateSample(enabled: Boolean) {
    // DOCS_REGION_START(animation-target-as-state)
val alpha = animateFloatAsState(
    targetValue = if (enabled) 1f else 0.5f,
    animationSpec = tween(durationMillis = 180),
)
    // DOCS_REGION_END(animation-target-as-state)
    check(alpha.value.isFinite())
}

private fun UiTreeBuilder.documentationAnimatableSample(command: Command) {
    // DOCS_REGION_START(animation-animatable)
val progress = rememberAnimatable(
    initialValue = 0f,
    converter = AnimationConverters.Float,
)

LaunchedEffect(command) {
    when (command) {
        Command.Open -> progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
        )
        Command.Close -> progress.animateDecay(AnimationVelocity(-2.4f))
        Command.Stop -> progress.stop()
        else -> Unit
    }
}
    // DOCS_REGION_END(animation-animatable)
}

private fun UiTreeBuilder.documentationTransitionSample(expanded: Boolean) {
    // DOCS_REGION_START(animation-transition)
val transition = updateTransition(
    targetState = if (expanded) PanelState.Expanded else PanelState.Collapsed,
    label = "panel",
)
val alpha = transition.animateFloat { state ->
    if (state == PanelState.Expanded) 1f else 0.6f
}
val height = transition.animateDp(
    transitionSpec = {
        if (isTransitioningTo(PanelState.Collapsed, PanelState.Expanded)) {
            spring(dampingRatio = 0.8f, stiffness = 240f)
        } else {
            tween(durationMillis = 180)
        }
    },
) { state ->
    if (state == PanelState.Expanded) 240.dp else 80.dp
}
    // DOCS_REGION_END(animation-transition)
    check(alpha.value.isFinite() && height.value.value.isFinite())
}

private fun UiTreeBuilder.documentationSeekableTransitionSample(command: Command) {
    val pointConverter = PointConverter
    // DOCS_REGION_START(animation-seekable-transition)
val seekState = remember { SeekableTransitionState(PanelState.Collapsed) }
val transition = rememberTransition(seekState, label = "seekable panel")
val position = transition.animateValue(
    converter = pointConverter,
    transitionSpec = { tween(durationMillis = 600) },
) { state ->
    if (state == PanelState.Expanded) Point(96f, 32f) else Point(0f, 0f)
}

LaunchedEffect(command) {
    when (command) {
        Command.Preview -> seekState.seekTo(0.7f, PanelState.Expanded)
        Command.Commit -> seekState.animateTo(PanelState.Expanded)
        Command.Reset -> seekState.snapTo(PanelState.Collapsed)
        else -> Unit
    }
}
    // DOCS_REGION_END(animation-seekable-transition)
    check(position.value.x.isFinite())
}

private fun UiTreeBuilder.documentationInfiniteTransitionSample() {
    // DOCS_REGION_START(animation-infinite-transition)
val pulse = rememberInfiniteTransition(label = "pulse")
val scale = pulse.animateFloat(
    initialValue = 0.9f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 600),
        repeatMode = RepeatMode.Reverse,
    ),
)
    // DOCS_REGION_END(animation-infinite-transition)
    check(scale.value.isFinite())
}

private fun UiTreeBuilder.documentationAnimatedVisibilitySample(showDetails: Boolean) {
    // DOCS_REGION_START(animation-visibility)
AnimatedVisibility(
    visible = showDetails,
    enter = fadeIn(tween(durationMillis = 160)) +
        slideInHorizontally(
            from = SlideDirection.Start,
            distanceFraction = 0.5f,
        ) +
        scaleIn(
            initialScale = 0.9f,
            transformOrigin = TransformOrigin(0f, 1f),
        ) +
        expandVertically(alignment = BoxAlignment.BottomStart),
    exit = shrinkVertically(alignment = BoxAlignment.TopEnd) +
        scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(1f, 0f),
        ) +
        slideOutHorizontally(towards = SlideDirection.End) +
        fadeOut(tween(durationMillis = 120)),
) {
    Text("Parent transition running: ${transition.isRunning}")
    AnimatedEnterExit(
        enter = slideInVertically(from = SlideDirection.Down),
        exit = slideOutVertically(towards = SlideDirection.Up),
    ) {
        Text("Descendant shares the parent clock")
    }
}
    // DOCS_REGION_END(animation-visibility)
}

private fun UiTreeBuilder.documentationAnimatedContentSample(page: DocumentationPage) {
    // DOCS_REGION_START(animation-content)
AnimatedContent(
    targetState = page,
    contentKey = { it.id },
    transitionSpec = {
        val forward = targetState.index > initialState.index
        val enter = fadeIn() + slideIntoContainer(
            from = if (forward) ContentSlideDirection.End else ContentSlideDirection.Start,
            distanceFraction = 0.35f,
        ) + scaleIn(initialScale = 0.96f)
        val exit = fadeOut() + slideOutOfContainer(
            towards = if (forward) ContentSlideDirection.Start else ContentSlideDirection.End,
            distanceFraction = 0.2f,
        )
        (enter togetherWith exit) using SizeTransform(clip = true)
    },
) { state ->
    Page(state)
}
    // DOCS_REGION_END(animation-content)
}

private fun UiTreeBuilder.documentationAnimateContentSizeSample() {
    // DOCS_REGION_START(animation-content-size)
Column(
    modifier = Modifier.animateContentSize(
        spring(dampingRatio = 0.75f, stiffness = 240f),
    ),
) {
    // Content whose measured size changes.
}
    // DOCS_REGION_END(animation-content-size)
}

private fun BoxScope.documentationAnimateBoundsSample(
    expanded: Boolean,
    onTargetClick: () -> Unit,
) {
    // DOCS_REGION_START(animation-bounds)
Button(
    text = "Move and resize",
    onClick = onTargetClick,
    modifier = Modifier
        .width(if (expanded) 204.dp else 152.dp)
        .height(if (expanded) 58.dp else 48.dp)
        .align(if (expanded) BoxAlignment.BottomEnd else BoxAlignment.BottomStart)
        .animateBounds(tween(durationMillis = 900)),
)
    // DOCS_REGION_END(animation-bounds)
}

private fun UiTreeBuilder.Page(page: DocumentationPage) {
    Text("Page ${page.id}")
}

private data class DocumentationPage(
    val id: String,
    val index: Int,
)

private enum class Command {
    Open,
    Close,
    Stop,
    Preview,
    Commit,
    Reset,
}
