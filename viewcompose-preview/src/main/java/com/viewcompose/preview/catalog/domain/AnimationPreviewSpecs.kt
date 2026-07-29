package com.viewcompose.preview.catalog.domain

import com.viewcompose.animation.AnimatedContent
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.core.tween
import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.graphicsLayer
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember

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
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = scale.value,
                                    scaleY = scale.value,
                                )
                                .padding(10.dp),
                        ) {
                            Text(text = "Animated surface")
                        }
                    }
                    AnimatedContent(
                        targetState = contentState.value,
                        transitionSpec = { tween(260) },
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
