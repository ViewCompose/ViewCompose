package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import android.view.Choreographer
import com.viewcompose.animation.animateColorAsState
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.gesture.draggableAnchorsOf
import com.viewcompose.gesture.draggable
import com.viewcompose.gesture.gesturePriority
import com.viewcompose.gesture.pointerInput
import com.viewcompose.gesture.rememberAnchoredDraggableState
import com.viewcompose.gesture.rememberDraggableState
import com.viewcompose.gesture.rememberTransformableState
import com.viewcompose.gesture.anchoredDraggable
import com.viewcompose.gesture.transformable
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.PointerEventType
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.graphicsLayer
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp
import kotlin.math.roundToInt

@ViewComposePreview(name = "Gestures · Tap", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGesturesTap() {
    GesturePage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Gestures · Drag and swipe", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGesturesDragSwipe() {
    GesturePage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Gestures · Transform", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGesturesTransform() {
    GesturePage(initialPageIndex = 2)
}

internal fun UiTreeBuilder.GesturePage(
    initialPageIndex: Int = 0,
) {
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 2)) }
    val tapCountState = remember { mutableStateOf(0) }
    val consumedPointerClickCountState = remember { mutableStateOf(0) }
    val consumedPointerBlockedTapCountState = remember { mutableStateOf(0) }
    val consumedPointerEventState = remember { mutableStateOf("None") }
    val dragOffsetState = remember { mutableStateOf(0f) }
    val dragTextOffsetState = remember { mutableStateOf(0f) }
    val dragTextFrameUpdater = remember {
        FrameCoalescedFloatUpdater { value ->
            dragTextOffsetState.value = value
        }
    }
    val swipeState = rememberAnchoredDraggableState("Center")
    val pointerEventState = remember { mutableStateOf("None") }
    val scaleState = remember { mutableStateOf(1f) }
    val rotationState = remember { mutableStateOf(0f) }
    val panXState = remember { mutableStateOf(0f) }
    val panYState = remember { mutableStateOf(0f) }
    val transformLogState = remember { mutableStateOf("idle") }
    DisposableEffect(dragTextFrameUpdater) {
        onDispose {
            dragTextFrameUpdater.dispose()
        }
    }

    val draggableState = rememberDraggableState { delta ->
        val nextOffset = (dragOffsetState.value + delta).coerceIn(-240f, 240f)
        dragOffsetState.value = nextOffset
        dragTextFrameUpdater.submit(nextOffset)
    }
    val transformState = rememberTransformableState { zoom, panX, panY, rotation ->
        scaleState.value = (scaleState.value * zoom).coerceIn(0.6f, 2.2f)
        panXState.value = (panXState.value + panX).coerceIn(-120f, 120f)
        panYState.value = (panYState.value + panY).coerceIn(-120f, 120f)
        rotationState.value += rotation
        transformLogState.value = "delta pan=(${panX.roundToInt()}, ${panY.roundToInt()}) delta rot=${"%.2f".format(rotation)}"
    }
    val swipeCurrentValue = swipeState.currentValue.value
    val swipeTargetValue = swipeState.targetValue.value
    val swipeOffsetValue = swipeState.currentOffsetPx.value ?: 0f
    val swipeVisualOffset = animateFloatAsState(
        targetValue = when (swipeCurrentValue) {
            "Left" -> -112f
            "Right" -> 112f
            else -> 0f
        },
        animationSpec = spring(durationMillis = 280),
    )
    val swipeVisualColor = animateColorAsState(
        targetValue = when (swipeCurrentValue) {
            "Left" -> 0xFFDBEAFE.toInt()
            "Right" -> 0xFFD9FBE8.toInt()
            else -> 0xFFF1F5F9.toInt()
        },
        animationSpec = tween(durationMillis = 220),
    )

    val sections = when (selectedPageState.value) {
        0 -> listOf("page", "filter", "tap", "verify")
        1 -> listOf("page", "filter", "drag_swipe", "verify")
        else -> listOf("page", "filter", "transform", "verify")
    }

    LazyColumn(
        items = sections,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "Gestures",
                goal = "验证 pointer/tap/drag/swipe/transform 在 renderer 分发链路中可用，并与 clickable 回落策略兼容。",
                modules = listOf("viewcompose-gesture", "renderer gesture dispatcher", "nested conflict policy"),
            )

            "filter" -> ChapterPageFilterSection(
                pages = listOf("Tap", "Drag+Swipe", "Transform"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "tap" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Tap / Double / Long",
                subtitle = "combinedClickable + pointerInput 组合，验证点击族事件与 pointer 事件日志。",
            ) {
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { tapCountState.value += 1 },
                            onDoubleClick = { tapCountState.value += 2 },
                            onLongClick = { tapCountState.value += 10 },
                        )
                        .pointerInput(key = "tap-pointer") { event ->
                            pointerEventState.value = event.type.name
                            PointerEventResult.Ignored
                        }
                        .padding(14.dp)
                        .testTag(DemoTestTags.GESTURE_TAP_TARGET),
                ) {
                    Text(text = "Tap target (single + double + long)")
                }
                Text(
                    text = "Tap count: ${tapCountState.value}",
                    modifier = Modifier.testTag(DemoTestTags.GESTURE_TAP_COUNT),
                )
                Text(
                    text = "Pointer: ${pointerEventState.value}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.testTag(DemoTestTags.GESTURE_POINTER_LOG),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .combinedClickable(
                            onClick = { consumedPointerClickCountState.value += 1 },
                        )
                        .pointerInput(key = "tap-pointer-consumed") { event ->
                            consumedPointerEventState.value = event.type.name
                            if (event.type == PointerEventType.Up) {
                                consumedPointerBlockedTapCountState.value += 1
                            }
                            PointerEventResult.Consumed
                        }
                        .padding(14.dp)
                        .testTag(DemoTestTags.GESTURE_POINTER_CONSUMED_TARGET),
                ) {
                    Text(text = "PointerInput consumed target")
                }
                Text(
                    text = "Consumed click count: ${consumedPointerClickCountState.value} · Blocked taps: ${consumedPointerBlockedTapCountState.value}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.testTag(DemoTestTags.GESTURE_POINTER_CONSUMED_CLICK_COUNT),
                )
                Text(
                    text = "Consumed pointer: ${consumedPointerEventState.value}",
                    color = TextDefaults.secondaryColor(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }

            "drag_swipe" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "Drag / Swipe",
                subtitle = "draggable 与 anchoredDraggable 在同一页共存，含方向锁、slop 与多锚点 settle。",
            ) {
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .gesturePriority(GesturePriority.High)
                        .draggable(
                            state = draggableState,
                            orientation = GestureOrientation.Horizontal,
                        )
                        .graphicsLayer(translationX = dragOffsetState.value)
                        .padding(12.dp)
                        .testTag(DemoTestTags.GESTURE_DRAG_TARGET),
                ) {
                    Text(text = "Drag horizontally")
                }
                Text(
                    text = "Drag x = ${dragTextOffsetState.value.roundToInt()}",
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.GESTURE_DRAG_VALUE),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .margin(top = 10.dp)
                        .anchoredDraggable(
                            state = swipeState,
                            anchors = draggableAnchorsOf(
                                -120f to "Left",
                                0f to "Center",
                                120f to "Right",
                            ),
                            orientation = GestureOrientation.Horizontal,
                        )
                        .graphicsLayer(translationX = swipeVisualOffset.value)
                        .backgroundColor(swipeVisualColor.value)
                        .padding(12.dp)
                        .testTag(DemoTestTags.GESTURE_SWIPE_TARGET),
                ) {
                    Text(
                        text = when (swipeCurrentValue) {
                            "Left" -> "Swipe horizontally · Left ⬅"
                            "Right" -> "Swipe horizontally · Right ✅"
                            else -> "Swipe horizontally · Center ◉"
                        },
                    )
                }
                Text(
                    text = "Swipe current = $swipeCurrentValue",
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.GESTURE_SWIPE_VALUE),
                )
                Text(
                    text = "Swipe target = $swipeTargetValue",
                    color = TextDefaults.secondaryColor(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    modifier = Modifier.testTag(DemoTestTags.GESTURE_SWIPE_TARGET_VALUE),
                )
                Text(
                    text = "Swipe offset(px) = ${swipeOffsetValue.roundToInt()}",
                    color = TextDefaults.secondaryColor(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    modifier = Modifier.testTag(DemoTestTags.GESTURE_SWIPE_OFFSET_VALUE),
                )
                Text(
                    text = when (swipeCurrentValue) {
                        "Left" -> "视觉反馈：卡片左移并变蓝"
                        "Right" -> "视觉反馈：卡片右移并变绿"
                        else -> "视觉反馈：卡片居中并恢复浅灰"
                    },
                    color = TextDefaults.secondaryColor(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    modifier = Modifier.margin(top = 4.dp),
                )
            }

            "transform" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Transform",
                subtitle = "扩大可操作区域 + 高优先级手势，验证缩放/平移/旋转更直观且更易操作。",
            ) {
                Surface(
                    variant = SurfaceVariant.Variant,
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .gesturePriority(GesturePriority.High)
                        .backgroundColor(0xFFEFF6FF.toInt())
                        .padding(8.dp)
                        .pointerInput(key = "transform-pointer") { event ->
                            pointerEventState.value = event.type.name
                            PointerEventResult.Ignored
                        }
                        .transformable(state = transformState)
                        .testTag(DemoTestTags.GESTURE_TRANSFORM_TARGET),
                ) {
                    Surface(
                        variant = SurfaceVariant.Default,
                        contentAlignment = BoxAlignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(horizontal = 64.dp)
                            .height(92.dp)
                            .graphicsLayer(
                                scaleX = scaleState.value,
                                scaleY = scaleState.value,
                                translationX = panXState.value * 1.6f,
                                translationY = panYState.value * 1.6f,
                                rotationZ = rotationState.value,
                            )
                            .backgroundColor(0xFFDBEAFE.toInt())
                            .padding(8.dp),
                    ) {
                        Text(
                            text = "Transform Target",
                            style = UiTextStyle(fontSizeSp = 15.sp),
                        )
                    }
                }
                Text(
                    text = "双指在蓝色块上缩放/平移/旋转（文本标签不再参与缩放）",
                    color = TextDefaults.secondaryColor(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    modifier = Modifier.margin(top = 6.dp),
                )
                Text(
                    text = "scale=${"%.2f".format(scaleState.value)}  pan=(${panXState.value.roundToInt()}, ${panYState.value.roundToInt()})  rot=${"%.1f".format(rotationState.value)}",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    modifier = Modifier
                        .margin(top = 8.dp)
                        .testTag(DemoTestTags.GESTURE_TRANSFORM_VALUE),
                )
                Text(
                    text = "Pointer: ${pointerEventState.value}",
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = transformLogState.value,
                    color = TextDefaults.secondaryColor(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    modifier = Modifier.margin(top = 4.dp),
                )
                Button(
                    text = "重置 Transform",
                    onClick = {
                        scaleState.value = 1f
                        panXState.value = 0f
                        panYState.value = 0f
                        rotationState.value = 0f
                        transformLogState.value = "idle"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = "观察点：缩放时蓝色块清晰、无明显文字虚影",
                        style = UiTextStyle(fontSizeSp = 12.sp),
                    )
                }
            }

            else -> VerificationNotesSection(
                what = "Gestures 页覆盖 tap、drag/swipe、transform 与 pointer 事件链路。",
                howToVerify = listOf(
                    "Tap 页点击目标区域，观察计数增长与 pointer 日志变化。",
                    "Drag+Swipe 页横向拖拽与滑动，确认 drag 值和 swipe 状态更新。",
                    "Transform 页做双指缩放/平移/旋转，确认日志数值持续变化。",
                ),
                expected = listOf(
                    "手势消费成功时不会回落触发 clickable。",
                    "方向锁与 slop 策略避免误触父滚动容器。",
                    "pointerInput 可以并行记录事件而不破坏高层手势。",
                ),
            )
        }
    }
}

private class FrameCoalescedFloatUpdater(
    private val onValue: (Float) -> Unit,
) : Choreographer.FrameCallback {
    private var scheduled = false
    private var hasPending = false
    private var pendingValue = 0f

    fun submit(value: Float) {
        pendingValue = value
        hasPending = true
        if (scheduled) {
            return
        }
        scheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!hasPending) {
            return
        }
        hasPending = false
        onValue(pendingValue)
    }

    fun dispose() {
        if (scheduled) {
            Choreographer.getInstance().removeFrameCallback(this)
        }
        scheduled = false
        hasPending = false
    }
}
