package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.tween
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.BoxScope
import com.viewcompose.widget.core.ColumnScope
import com.viewcompose.widget.core.RowScope
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.remember
import kotlin.math.abs

/** Selects which measured axes participate in an expand or shrink transition. */
enum class SizeTransformAxis {
    /** Scales both measured width and height. */
    Both,

    /** Scales measured width while height remains at its full size. */
    Horizontal,

    /** Scales measured height while width remains at its full size. */
    Vertical,
}

/**
 * Defines one primitive that can contribute to an [EnterTransition].
 *
 * Consumers should use [fadeIn], [expandIn], [expandHorizontally], or [expandVertically] unless they
 * need to inspect or transform transition element lists.
 */
sealed interface EnterTransitionElement {
    /**
     * Animates host alpha from [initialAlpha] to fully opaque.
     *
     * @property animationSpec timing policy for the alpha channel
     * @property initialAlpha requested alpha at the start of a fully hidden enter; rendered alpha is
     * clamped to `0f..1f`
     */
    data class Fade(
        val animationSpec: AnimationSpec = tween(),
        val initialAlpha: Float = 0f,
    ) : EnterTransitionElement

    /**
     * Animates measured size from [initialScale] to full size on [axis].
     *
     * The renderer clips content to the animated bounds. Negative scales render as zero.
     *
     * @property animationSpec timing policy shared by each affected size channel
     * @property initialScale fraction of full measured size at the start of a fully hidden enter
     * @property axis measured axes affected by the element
     */
    data class Expand(
        val animationSpec: AnimationSpec = tween(),
        val initialScale: Float = 0f,
        val axis: SizeTransformAxis = SizeTransformAxis.Both,
    ) : EnterTransitionElement
}

/**
 * Defines one primitive that can contribute to an [ExitTransition].
 *
 * Consumers should use [fadeOut], [shrinkOut], [shrinkHorizontally], or [shrinkVertically] unless
 * they need to inspect or transform transition element lists.
 */
sealed interface ExitTransitionElement {
    /**
     * Animates host alpha from its current value to [targetAlpha].
     *
     * @property animationSpec timing policy for the alpha channel
     * @property targetAlpha requested hidden alpha; rendered alpha is clamped to `0f..1f`
     */
    data class Fade(
        val animationSpec: AnimationSpec = tween(),
        val targetAlpha: Float = 0f,
    ) : ExitTransitionElement

    /**
     * Animates measured size from its current value to [targetScale] on [axis].
     *
     * The renderer clips content to the animated bounds. Negative scales render as zero.
     *
     * @property animationSpec timing policy shared by each affected size channel
     * @property targetScale fraction of full measured size at the hidden endpoint
     * @property axis measured axes affected by the element
     */
    data class Shrink(
        val animationSpec: AnimationSpec = tween(),
        val targetScale: Float = 0f,
        val axis: SizeTransformAxis = SizeTransformAxis.Both,
    ) : ExitTransitionElement
}

/**
 * Collects enter primitives applied by [AnimatedVisibility].
 *
 * Elements are interpreted by channel. If several fade or applicable expand elements are present,
 * the last one in [elements] wins for that channel.
 *
 * @property elements ordered primitives available to the visibility host
 */
data class EnterTransition(
    val elements: List<EnterTransitionElement>,
) {
    /**
     * Returns a transition whose ordered elements are this transition followed by [other].
     *
     * Because the last applicable element wins, [other] overrides duplicate channels.
     *
     * @param other transition appended after this transition
     * @return a new transition containing both element lists
     */
    operator fun plus(other: EnterTransition): EnterTransition {
        return EnterTransition(elements + other.elements)
    }
}

/**
 * Collects exit primitives applied by [AnimatedVisibility].
 *
 * Elements are interpreted by channel. If several fade or applicable shrink elements are present,
 * the last one in [elements] wins for that channel.
 *
 * @property elements ordered primitives available to the visibility host
 */
data class ExitTransition(
    val elements: List<ExitTransitionElement>,
) {
    /**
     * Returns a transition whose ordered elements are this transition followed by [other].
     *
     * Because the last applicable element wins, [other] overrides duplicate channels.
     *
     * @param other transition appended after this transition
     * @return a new transition containing both element lists
     */
    operator fun plus(other: ExitTransition): ExitTransition {
        return ExitTransition(elements + other.elements)
    }
}

/**
 * Creates an enter transition that fades from [initialAlpha] to fully opaque.
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for alpha
 * @param initialAlpha alpha used when entering from the fully hidden endpoint
 * @return a one-element enter transition
 */
fun fadeIn(
    animationSpec: AnimationSpec = tween(),
    initialAlpha: Float = 0f,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Fade(
            animationSpec = animationSpec,
            initialAlpha = initialAlpha,
        ),
    ),
)

/**
 * Creates an enter transition that expands both measured axes to full size.
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for width and height scale
 * @param initialScale initial fraction of full measured width and height
 * @return a one-element enter transition
 */
fun expandIn(
    animationSpec: AnimationSpec = tween(),
    initialScale: Float = 0f,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Expand(
            animationSpec = animationSpec,
            initialScale = initialScale,
            axis = SizeTransformAxis.Both,
        ),
    ),
)

/**
 * Creates an enter transition that expands measured width to full size.
 *
 * @param animationSpec timing policy for width scale
 * @param initialScale initial fraction of full measured width
 * @return a one-element horizontal enter transition
 */
fun expandHorizontally(
    animationSpec: AnimationSpec = tween(),
    initialScale: Float = 0f,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Expand(
            animationSpec = animationSpec,
            initialScale = initialScale,
            axis = SizeTransformAxis.Horizontal,
        ),
    ),
)

/**
 * Creates an enter transition that expands measured height to full size.
 *
 * @param animationSpec timing policy for height scale
 * @param initialScale initial fraction of full measured height
 * @return a one-element vertical enter transition
 */
fun expandVertically(
    animationSpec: AnimationSpec = tween(),
    initialScale: Float = 0f,
): EnterTransition = EnterTransition(
    elements = listOf(
        EnterTransitionElement.Expand(
            animationSpec = animationSpec,
            initialScale = initialScale,
            axis = SizeTransformAxis.Vertical,
        ),
    ),
)

/**
 * Creates an exit transition that fades from the current alpha to [targetAlpha].
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for alpha
 * @param targetAlpha alpha at the hidden endpoint
 * @return a one-element exit transition
 */
fun fadeOut(
    animationSpec: AnimationSpec = tween(),
    targetAlpha: Float = 0f,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Fade(
            animationSpec = animationSpec,
            targetAlpha = targetAlpha,
        ),
    ),
)

/**
 * Creates an exit transition that shrinks both measured axes to [targetScale].
 *
 * @sample com.viewcompose.animation.samples.visibilityTransitionsSample
 *
 * @param animationSpec timing policy for width and height scale
 * @param targetScale terminal fraction of full measured width and height
 * @return a one-element exit transition
 */
fun shrinkOut(
    animationSpec: AnimationSpec = tween(),
    targetScale: Float = 0f,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Shrink(
            animationSpec = animationSpec,
            targetScale = targetScale,
            axis = SizeTransformAxis.Both,
        ),
    ),
)

/**
 * Creates an exit transition that shrinks measured width to [targetScale].
 *
 * @param animationSpec timing policy for width scale
 * @param targetScale terminal fraction of full measured width
 * @return a one-element horizontal exit transition
 */
fun shrinkHorizontally(
    animationSpec: AnimationSpec = tween(),
    targetScale: Float = 0f,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Shrink(
            animationSpec = animationSpec,
            targetScale = targetScale,
            axis = SizeTransformAxis.Horizontal,
        ),
    ),
)

/**
 * Creates an exit transition that shrinks measured height to [targetScale].
 *
 * @param animationSpec timing policy for height scale
 * @param targetScale terminal fraction of full measured height
 * @return a one-element vertical exit transition
 */
fun shrinkVertically(
    animationSpec: AnimationSpec = tween(),
    targetScale: Float = 0f,
): ExitTransition = ExitTransition(
    elements = listOf(
        ExitTransitionElement.Shrink(
            animationSpec = animationSpec,
            targetScale = targetScale,
            axis = SizeTransformAxis.Vertical,
        ),
    ),
)

/**
 * Mounts or removes [content] with alpha and measured-size transitions when [visible] changes.
 *
 * The first composition is settled at [visible] and does not play an enter animation. A later
 * `false` target keeps content mounted through the exit segment and removes it after every channel
 * finishes. Retargeting resumes each channel from its current sample. Size elements clip content to
 * animated width and height; [modifier] applies to the animated visibility host.
 *
 * The default transition fades and expands or shrinks both axes. Within a composed transition, the
 * last element applicable to a channel wins.
 *
 * @sample com.viewcompose.animation.samples.animatedVisibilitySample
 *
 * @receiver tree builder for the current composition
 * @param visible whether content should be mounted after the transition settles
 * @param modifier modifier applied to the visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content subtree retained until an exit segment completes
 */
fun UiTreeBuilder.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: BoxScope.() -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(visible)
    }
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes [content] according to an externally controlled [visibleState].
 *
 * Callers update [MutableTransitionState.targetState]; this host mirrors committed state and idle
 * status back into the same object. Bind one state object to one active host to avoid competing
 * writers. The first composition is settled at the state's current target, so setting a different
 * target before the state is first consumed does not play an initial enter animation in this
 * release. Compose the hidden target once before changing it when an initial enter is required.
 *
 * Content remains mounted through exit and interrupted segments. The default transition affects
 * alpha and both measured axes.
 *
 * @sample com.viewcompose.animation.samples.mutableTransitionStateSample
 *
 * @receiver tree builder for the current composition
 * @param visibleState externally retained target and observation state
 * @param modifier modifier applied to the visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content subtree retained until an exit segment completes
 */
fun UiTreeBuilder.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: BoxScope.() -> Unit,
) {
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visibleState.targetState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes row [content], defaulting size motion to the horizontal axis.
 *
 * The first composition is settled at [visible]. Later exits retain content until all alpha and
 * width channels finish. Retargeting resumes from current samples.
 *
 * @receiver row scope that receives the animated visibility host as one child
 * @param visible whether content should remain after the transition settles
 * @param modifier modifier applied to the row child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content box-scoped subtree inside the row child
 */
fun RowScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandHorizontally(),
    exit: ExitTransition = shrinkHorizontally() + fadeOut(),
    content: BoxScope.() -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(visible)
    }
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes row [content] from an externally controlled [visibleState].
 *
 * This overload shares the state ownership and first-consumption behavior of the tree-builder state
 * overload, while its defaults expand and shrink only measured width. Bind one state object to one
 * active host.
 *
 * @receiver row scope that receives the animated visibility host as one child
 * @param visibleState externally retained target and observation state
 * @param modifier modifier applied to the row child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content box-scoped subtree inside the row child
 */
fun RowScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandHorizontally(),
    exit: ExitTransition = shrinkHorizontally() + fadeOut(),
    content: BoxScope.() -> Unit,
) {
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visibleState.targetState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes column [content], defaulting size motion to the vertical axis.
 *
 * The first composition is settled at [visible]. Later exits retain content until all alpha and
 * height channels finish. Retargeting resumes from current samples.
 *
 * @receiver column scope that receives the animated visibility host as one child
 * @param visible whether content should remain after the transition settles
 * @param modifier modifier applied to the column child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content box-scoped subtree inside the column child
 */
fun ColumnScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandVertically(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    content: BoxScope.() -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(visible)
    }
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Mounts or removes column [content] from an externally controlled [visibleState].
 *
 * This overload shares the state ownership and first-consumption behavior of the tree-builder state
 * overload, while its defaults expand and shrink only measured height. Bind one state object to one
 * active host.
 *
 * @receiver column scope that receives the animated visibility host as one child
 * @param visibleState externally retained target and observation state
 * @param modifier modifier applied to the column child visibility host
 * @param enter primitives used for a hidden-to-visible segment
 * @param exit primitives used for a visible-to-hidden segment
 * @param content box-scoped subtree inside the column child
 */
fun ColumnScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandVertically(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    content: BoxScope.() -> Unit,
) {
    animatedVisibilityCore(
        visibleState = visibleState,
        targetVisible = visibleState.targetState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

private fun UiTreeBuilder.animatedVisibilityCore(
    visibleState: MutableTransitionState<Boolean>,
    targetVisible: Boolean,
    modifier: Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: BoxScope.() -> Unit,
) {
    val enterFade = enter.elements.filterIsInstance<EnterTransitionElement.Fade>().lastOrNull()
    val exitFade = exit.elements.filterIsInstance<ExitTransitionElement.Fade>().lastOrNull()
    val enterWidthExpand = enter.findExpandForWidthAxis()
    val enterHeightExpand = enter.findExpandForHeightAxis()
    val exitWidthShrink = exit.findShrinkForWidthAxis()
    val exitHeightShrink = exit.findShrinkForHeightAxis()
    val transition = updateTransition(
        targetState = targetVisible,
        label = "animated_visibility",
    )
    val alphaState = transition.animateFloatBySegment(
        transitionSpec = { initial, target ->
            when {
                !initial && target -> enterFade?.animationSpec ?: snap()
                initial && !target -> exitFade?.animationSpec ?: snap()
                else -> snap()
            }
        },
        segmentEndpoints = { initial, target, current ->
            val hiddenAlpha = exitFade?.targetAlpha ?: 1f
            when {
                !initial && target -> {
                    val start = if (current.isApproximately(hiddenAlpha)) {
                        enterFade?.initialAlpha ?: current
                    } else {
                        current
                    }
                    start to 1f
                }

                initial && !target -> current to hiddenAlpha
                else -> current to if (target) 1f else hiddenAlpha
            }
        },
        valueForSettledState = { settledVisible ->
            if (settledVisible) 1f else (exitFade?.targetAlpha ?: 1f)
        },
    )
    val widthScaleState = transition.animateFloatBySegment(
        transitionSpec = { initial, target ->
            when {
                !initial && target -> enterWidthExpand?.animationSpec ?: snap()
                initial && !target -> exitWidthShrink?.animationSpec ?: snap()
                else -> snap()
            }
        },
        segmentEndpoints = { initial, target, current ->
            val hiddenWidthScale = exitWidthShrink?.targetScale ?: 1f
            when {
                !initial && target -> {
                    val start = if (current.isApproximately(hiddenWidthScale)) {
                        enterWidthExpand?.initialScale ?: current
                    } else {
                        current
                    }
                    start to 1f
                }

                initial && !target -> current to hiddenWidthScale
                else -> current to if (target) 1f else hiddenWidthScale
            }
        },
        valueForSettledState = { settledVisible ->
            if (settledVisible) 1f else (exitWidthShrink?.targetScale ?: 1f)
        },
    )
    val heightScaleState = transition.animateFloatBySegment(
        transitionSpec = { initial, target ->
            when {
                !initial && target -> enterHeightExpand?.animationSpec ?: snap()
                initial && !target -> exitHeightShrink?.animationSpec ?: snap()
                else -> snap()
            }
        },
        segmentEndpoints = { initial, target, current ->
            val hiddenHeightScale = exitHeightShrink?.targetScale ?: 1f
            when {
                !initial && target -> {
                    val start = if (current.isApproximately(hiddenHeightScale)) {
                        enterHeightExpand?.initialScale ?: current
                    } else {
                        current
                    }
                    start to 1f
                }

                initial && !target -> current to hiddenHeightScale
                else -> current to if (target) 1f else hiddenHeightScale
            }
        },
        valueForSettledState = { settledVisible ->
            if (settledVisible) 1f else (exitHeightShrink?.targetScale ?: 1f)
        },
    )
    // Mirror the internal segment after sampling so external observers see committed and idle state.
    visibleState.currentState = transition.currentState
    visibleState.targetState = targetVisible
    visibleState.isIdle = !transition.isRunning && transition.currentState == transition.targetState
    val shouldRender = transition.currentState || transition.targetState || transition.isRunning
    if (!shouldRender) {
        return
    }
    val hasSizeTransform = enterWidthExpand != null ||
        enterHeightExpand != null ||
        exitWidthShrink != null ||
        exitHeightShrink != null
    emit(
        type = NodeType.AnimatedVisibilityHost,
        spec = AnimatedVisibilityHostNodeProps(
            alpha = alphaState.value.coerceIn(0f, 1f),
            widthScale = widthScaleState.value.coerceAtLeast(0f),
            heightScale = heightScaleState.value.coerceAtLeast(0f),
            clipToBounds = hasSizeTransform,
        ),
        modifier = modifier,
    ) {
        Box(content = content)
    }
}

private fun EnterTransition.findExpandForWidthAxis(): EnterTransitionElement.Expand? {
    return elements
        .asReversed()
        .filterIsInstance<EnterTransitionElement.Expand>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Horizontal }
}

private fun EnterTransition.findExpandForHeightAxis(): EnterTransitionElement.Expand? {
    return elements
        .asReversed()
        .filterIsInstance<EnterTransitionElement.Expand>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Vertical }
}

private fun ExitTransition.findShrinkForWidthAxis(): ExitTransitionElement.Shrink? {
    return elements
        .asReversed()
        .filterIsInstance<ExitTransitionElement.Shrink>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Horizontal }
}

private fun ExitTransition.findShrinkForHeightAxis(): ExitTransitionElement.Shrink? {
    return elements
        .asReversed()
        .filterIsInstance<ExitTransitionElement.Shrink>()
        .firstOrNull { it.axis == SizeTransformAxis.Both || it.axis == SizeTransformAxis.Vertical }
}

private fun Float.isApproximately(other: Float): Boolean {
    return abs(this - other) <= 0.001f
}
