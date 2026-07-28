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

/**
 * 尺寸转场作用的轴向。
 * Axis affected by a size transition.
 */
enum class SizeTransformAxis {
    Both,
    Horizontal,
    Vertical,
}

/**
 * 进入转场的基础元素。
 * Primitive element of an enter transition.
 */
sealed interface EnterTransitionElement {
    data class Fade(
        val animationSpec: AnimationSpec = tween(),
        val initialAlpha: Float = 0f,
    ) : EnterTransitionElement

    data class Expand(
        val animationSpec: AnimationSpec = tween(),
        val initialScale: Float = 0f,
        val axis: SizeTransformAxis = SizeTransformAxis.Both,
    ) : EnterTransitionElement
}

/**
 * 离开转场的基础元素。
 * Primitive element of an exit transition.
 */
sealed interface ExitTransitionElement {
    data class Fade(
        val animationSpec: AnimationSpec = tween(),
        val targetAlpha: Float = 0f,
    ) : ExitTransitionElement

    data class Shrink(
        val animationSpec: AnimationSpec = tween(),
        val targetScale: Float = 0f,
        val axis: SizeTransformAxis = SizeTransformAxis.Both,
    ) : ExitTransitionElement
}

/**
 * 可组合的进入转场。
 * Composable enter transition.
 */
data class EnterTransition(
    val elements: List<EnterTransitionElement>,
) {
    operator fun plus(other: EnterTransition): EnterTransition {
        return EnterTransition(elements + other.elements)
    }
}

/**
 * 可组合的离开转场。
 * Composable exit transition.
 */
data class ExitTransition(
    val elements: List<ExitTransitionElement>,
) {
    operator fun plus(other: ExitTransition): ExitTransition {
        return ExitTransition(elements + other.elements)
    }
}

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
 * 根据 [visible] 控制内容挂载，并在进入/离开时应用 alpha 和尺寸转场。
 * Mounts content according to [visible] and applies alpha/size transitions on enter and exit.
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
    // 将内部 Transition 的实时状态写回外部 state，使调用方能观察 idle/current/target。
    // Mirror the internal Transition back to external state so callers can observe idle/current/target.
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
