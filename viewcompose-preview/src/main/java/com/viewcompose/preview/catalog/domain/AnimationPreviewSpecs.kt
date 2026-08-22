package com.viewcompose.preview.catalog.domain

import com.viewcompose.animation.Crossfade
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.SeekableTransitionState
import com.viewcompose.animation.SlideDirection
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.tween
import com.viewcompose.animation.expandVertically
import com.viewcompose.animation.fadeIn
import com.viewcompose.animation.fadeOut
import com.viewcompose.animation.rememberTransition
import com.viewcompose.animation.scaleIn
import com.viewcompose.animation.scaleOut
import com.viewcompose.animation.shrinkVertically
import com.viewcompose.animation.slideInHorizontally
import com.viewcompose.animation.slideInVertically
import com.viewcompose.animation.slideOutHorizontally
import com.viewcompose.animation.slideOutVertically
import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.graphicsLayer
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember

internal object AnimationPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "animation-core-transitions",
            title = "Core + Transition",
            domain = PreviewDomain.Animation,
            content = {
                val visibleState = remember { mutableStateOf(true) }
                val contentState = remember { mutableStateOf(false) }
                val scale = animateFloatAsState(
                    targetValue = if (contentState.value) 1.08f else 0.92f,
                    animationSpec = tween(240),
                )
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(vertical = 6.dp),
                ) {
                    Button(
                        text = if (visibleState.value) "隐藏" else "显示",
                        onClick = { visibleState.value = !visibleState.value },
                    )
                    AnimatedVisibility(
                        visible = visibleState.value,
                        enter = fadeIn(tween(180), initialAlpha = 0.15f) +
                            slideInHorizontally(
                                from = SlideDirection.Start,
                                animationSpec = tween(320),
                                distanceFraction = 0.4f,
                            ) +
                            scaleIn(
                                animationSpec = tween(320),
                                initialScale = 0.78f,
                                transformOrigin = TransformOrigin(0f, 1f),
                            ) +
                            expandVertically(
                                animationSpec = tween(320),
                                alignment = BoxAlignment.BottomStart,
                            ),
                        exit = shrinkVertically(
                            animationSpec = tween(300),
                            alignment = BoxAlignment.TopEnd,
                        ) + scaleOut(
                            animationSpec = tween(300),
                            targetScale = 0.72f,
                            transformOrigin = TransformOrigin(1f, 0f),
                        ) + slideOutHorizontally(
                            towards = SlideDirection.End,
                            animationSpec = tween(300),
                            distanceFraction = 0.5f,
                        ) + fadeOut(tween(180), targetAlpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .graphicsLayer(
                                    scaleX = scale.value,
                                    scaleY = scale.value,
                                )
                                .padding(10.dp),
                        ) {
                            Text(text = "Parent slide + reveal + pivot scale")
                        }
                        AnimatedEnterExit(
                            enter = slideInVertically(
                                from = SlideDirection.Down,
                                animationSpec = tween(420),
                            ) + fadeIn(tween(180)),
                            exit = slideOutVertically(
                                towards = SlideDirection.Up,
                                animationSpec = tween(420),
                            ) + fadeOut(tween(180)),
                            modifier = Modifier.margin(left = 88.dp, top = 46.dp),
                        ) {
                            Surface(
                                variant = SurfaceVariant.Default,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(text = "Shared-clock child")
                            }
                        }
                    }
                    Crossfade(
                        targetState = contentState.value,
                        animationSpec = tween(260),
                        modifier = Modifier.fillMaxWidth(),
                    ) { alt ->
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                        ) {
                            Text(text = if (alt) "替代文案" else "主文案")
                        }
                    }
                    Button(
                        text = "切换内容",
                        onClick = { contentState.value = !contentState.value },
                    )
                }
            },
        ),
        PreviewSpec(
            id = "animation-seekable-transition",
            title = "Seekable Transition",
            domain = PreviewDomain.Animation,
            content = {
                val state = remember { SeekableTransitionState(false) }
                val command = remember { mutableStateOf<PreviewSeekCommand>(PreviewSeekCommand.None) }
                val commandNonce = remember { mutableStateOf(0) }
                val transition = rememberTransition(state, label = "preview seekable transition")
                val position = transition.animateValue(
                    converter = PreviewPointConverter,
                    transitionSpec = {
                        if (isTransitioningTo(false, true)) tween(720) else tween(520)
                    },
                    targetValueByState = { expanded ->
                        if (expanded) PreviewPoint(96f, 28f) else PreviewPoint(0f, 0f)
                    },
                )
                val alpha = transition.animateFloat(
                    transitionSpec = { tween(180) },
                    targetValueByState = { expanded -> if (expanded) 1f else 0.35f },
                )
                LaunchedEffect(command.value, commandNonce.value, state) {
                    when (val request = command.value) {
                        PreviewSeekCommand.None -> Unit
                        is PreviewSeekCommand.Seek -> state.seekTo(request.fraction, request.target)
                        is PreviewSeekCommand.Animate -> state.animateTo(request.target)
                        is PreviewSeekCommand.Snap -> state.snapTo(request.target)
                    }
                }
                Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "current=${state.currentState} · target=${state.targetState} · " +
                            "fraction=${String.format("%.2f", state.fraction)}",
                    )
                    Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            text = "Seek 70%",
                            onClick = {
                                command.value = PreviewSeekCommand.Seek(0.7f, true)
                                commandNonce.value += 1
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            text = "Animate",
                            onClick = {
                                command.value = PreviewSeekCommand.Animate(state.targetState)
                                commandNonce.value += 1
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.fillMaxWidth().height(112.dp).padding(10.dp),
                    ) {
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .graphicsLayer(
                                    translationX = position.value.x,
                                    translationY = position.value.y,
                                    alpha = alpha.value,
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(text = "Generic 2D channel")
                        }
                    }
                    Button(
                        text = "Reset",
                        onClick = {
                            command.value = PreviewSeekCommand.Snap(false)
                            commandNonce.value += 1
                        },
                    )
                }
            },
        ),
    )
}

private sealed interface PreviewSeekCommand {
    data object None : PreviewSeekCommand

    data class Seek(val fraction: Float, val target: Boolean) : PreviewSeekCommand

    data class Animate(val target: Boolean) : PreviewSeekCommand

    data class Snap(val target: Boolean) : PreviewSeekCommand
}

private data class PreviewPoint(
    val x: Float,
    val y: Float,
)

private object PreviewPointConverter : AnimationConverter<PreviewPoint, PreviewPoint> {
    override val vectorSize: Int = 2
    override val zeroVelocity: PreviewPoint = PreviewPoint(0f, 0f)
    override val visibilityThreshold: PreviewPoint = PreviewPoint(0.01f, 0.01f)

    override fun convertToVector(value: PreviewPoint, destination: FloatArray) {
        destination[0] = value.x
        destination[1] = value.y
    }

    override fun convertFromVector(vector: FloatArray): PreviewPoint {
        return PreviewPoint(vector[0], vector[1])
    }

    override fun convertVelocityToVector(velocity: PreviewPoint, destination: FloatArray) {
        convertToVector(velocity, destination)
    }

    override fun convertVelocityFromVector(vector: FloatArray): PreviewPoint {
        return convertFromVector(vector)
    }
}
