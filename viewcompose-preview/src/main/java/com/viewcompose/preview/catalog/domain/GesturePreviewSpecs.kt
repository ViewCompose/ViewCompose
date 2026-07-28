package com.viewcompose.preview.catalog.domain

import com.viewcompose.gesture.combinedClickable
import com.viewcompose.gesture.draggableAnchorsOf
import com.viewcompose.gesture.draggable
import com.viewcompose.gesture.anchoredDraggable
import com.viewcompose.gesture.rememberAnchoredDraggableState
import com.viewcompose.gesture.rememberDraggableState
import com.viewcompose.gesture.rememberTransformableState
import com.viewcompose.gesture.transformable
import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.graphicsLayer
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.remember

/**
 * 提供手势组件的预览规格，用最小可交互场景覆盖点击、拖拽、滑动和缩放。
 * Provides preview specs for gesture components using minimal interactive cases for tap, drag, swipe, and scale.
 */
internal object GesturePreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "gesture-tap-drag-swipe-transform",
            title = "Tap + Drag + Swipe + Transform",
            domain = PreviewDomain.Gesture,
            content = {
                val taps = remember { mutableStateOf(0) }
                val dragX = remember { mutableStateOf(0f) }
                val swipeState = rememberAnchoredDraggableState("Left")
                val scale = remember { mutableStateOf(1f) }
                val draggableState = rememberDraggableState { delta ->
                    dragX.value = (dragX.value + delta).coerceIn(-80f, 80f)
                }
                val transformState = rememberTransformableState { zoom, _, _, _ ->
                    scale.value = (scale.value * zoom).coerceIn(0.7f, 1.6f)
                }
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(vertical = 6.dp),
                ) {
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { taps.value += 1 })
                            .padding(10.dp),
                    ) {
                        Text(text = "Tap target: ${taps.value}")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggable(
                                state = draggableState,
                                orientation = GestureOrientation.Horizontal,
                            )
                            .graphicsLayer(translationX = dragX.value)
                            .padding(10.dp),
                    ) {
                        Text(text = "Drag x=${dragX.value.toInt()}")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .anchoredDraggable(
                                state = swipeState,
                                anchors = draggableAnchorsOf(0f to "Left", 100f to "Right"),
                                orientation = GestureOrientation.Horizontal,
                            )
                            .padding(10.dp),
                    ) {
                        Text(text = "Swipe state=${swipeState.currentValue.value}")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformable(state = transformState)
                            .graphicsLayer(
                                scaleX = scale.value,
                                scaleY = scale.value,
                            )
                            .padding(10.dp),
                    ) {
                        Text(text = "Transform scale=${"%.2f".format(scale.value)}")
                    }
                }
            },
        ),
    )
}
