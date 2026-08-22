package com.viewcompose.preview.catalog.domain

import com.viewcompose.animation.Crossfade
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.SlideDirection
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.core.tween
import com.viewcompose.animation.expandVertically
import com.viewcompose.animation.fadeIn
import com.viewcompose.animation.fadeOut
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
    )
}
