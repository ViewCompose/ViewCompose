package com.viewcompose

import android.view.Choreographer
import com.viewcompose.animation.animateColorAsState
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.gesture.anchoredDraggable
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.gesture.draggable
import com.viewcompose.gesture.draggableAnchorsOf
import com.viewcompose.gesture.gesturePriority
import com.viewcompose.gesture.pointerInput
import com.viewcompose.gesture.rememberAnchoredDraggableState
import com.viewcompose.gesture.rememberDraggableState
import com.viewcompose.gesture.rememberTransformableState
import com.viewcompose.gesture.transformable
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
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
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import kotlin.math.roundToInt

@ViewComposePreview(name = "Gestures · Tap", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGesturesTap() {
    GesturePage(GestureFixture.Tap)
}

@ViewComposePreview(name = "Gestures · Drag and swipe", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGesturesDragSwipe() {
    GesturePage(GestureFixture.DragSwipe)
}

@ViewComposePreview(name = "Gestures · Transform", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGesturesTransform() {
    GesturePage(GestureFixture.Transform)
}

internal enum class GestureFixture(
    val scenarioId: DemoScenarioId,
) {
    Tap(DemoScenarioIds.GestureTap),
    DragSwipe(DemoScenarioIds.GestureDragSwipe),
    Transform(DemoScenarioIds.GestureTransform),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): GestureFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported gesture scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.GesturePage(
    fixture: GestureFixture,
    scenario: DemoScenarioSpec? = null,
) {
    // Each strict route enters exactly one fixture function. State objects, frame callbacks, and
    // gesture recognizers from another fixture must never join this composition session.
    when (fixture) {
        GestureFixture.Tap -> TapGestureFixture(scenario)
        GestureFixture.DragSwipe -> DragSwipeGestureFixture(scenario)
        GestureFixture.Transform -> TransformGestureFixture(scenario)
    }
}

private fun UiTreeBuilder.TapGestureFixture(scenario: DemoScenarioSpec?) {
    val tapCountState = remember { mutableStateOf(0) }
    val pointerEventState = remember { mutableStateOf<PointerEventType?>(null) }
    val consumedPointerClickCountState = remember { mutableStateOf(0) }
    val consumedPointerBlockedTapCountState = remember { mutableStateOf(0) }
    val consumedPointerEventState = remember { mutableStateOf<PointerEventType?>(null) }

    fun reset() {
        tapCountState.value = 0
        pointerEventState.value = null
        consumedPointerClickCountState.value = 0
        consumedPointerBlockedTapCountState.value = 0
        consumedPointerEventState.value = null
    }

    LazyColumn(
        items = listOf("tap"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        ScenarioSection(
            kind = ScenarioKind.Core,
            title = stringResource(R.string.demo_gesture_tap_title),
            subtitle = stringResource(R.string.demo_gesture_tap_summary),
        ) {
            Text(
                text = stringResource(
                    R.string.demo_gesture_tap_state,
                    tapCountState.value,
                    consumedPointerClickCountState.value,
                    consumedPointerBlockedTapCountState.value,
                ),
                modifier = Modifier.gestureScenarioTarget(scenario, DemoAutomationRole.State),
            )
            Button(
                text = stringResource(R.string.demo_gesture_tap_action),
                onClick = { tapCountState.value += 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_gesture_reset),
                variant = ButtonVariant.Outlined,
                onClick = ::reset,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
            Surface(
                variant = SurfaceVariant.Variant,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 10.dp)
                    .combinedClickable(
                        onClick = { tapCountState.value += 1 },
                        onDoubleClick = { tapCountState.value += 2 },
                        onLongClick = { tapCountState.value += 10 },
                    )
                    .pointerInput(key = "tap-pointer") { event ->
                        pointerEventState.value = event.type
                        PointerEventResult.Ignored
                    }
                    .padding(14.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(text = stringResource(R.string.demo_gesture_tap_target))
            }
            Text(
                text = stringResource(R.string.demo_gesture_tap_count, tapCountState.value),
                modifier = Modifier.testTag(DemoGestureTestTags.GESTURE_TAP_COUNT),
            )
            Text(
                text = stringResource(
                    R.string.demo_gesture_pointer_event,
                    pointerEventState.value?.name
                        ?: stringResource(R.string.demo_gesture_pointer_none),
                ),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.testTag(DemoGestureTestTags.GESTURE_POINTER_LOG),
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
                        consumedPointerEventState.value = event.type
                        if (event.type == PointerEventType.Up) {
                            consumedPointerBlockedTapCountState.value += 1
                        }
                        PointerEventResult.Consumed
                    }
                    .padding(14.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.SecondaryTarget),
            ) {
                Text(text = stringResource(R.string.demo_gesture_consumed_target))
            }
            Text(
                text = stringResource(
                    R.string.demo_gesture_consumed_state,
                    consumedPointerClickCountState.value,
                    consumedPointerBlockedTapCountState.value,
                ),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.testTag(DemoGestureTestTags.GESTURE_POINTER_CONSUMED_CLICK_COUNT),
            )
            Text(
                text = stringResource(
                    R.string.demo_gesture_consumed_pointer_event,
                    consumedPointerEventState.value?.name
                        ?: stringResource(R.string.demo_gesture_pointer_none),
                ),
                color = TextDefaults.secondaryColor(),
                style = UiTextStyle(fontSizeSp = 12.sp),
            )
        }
    }
}

private fun UiTreeBuilder.DragSwipeGestureFixture(scenario: DemoScenarioSpec?) {
    val dragOffsetState = remember { mutableStateOf(0f) }
    val dragTextOffsetState = remember { mutableStateOf(0f) }
    val dragTextFrameUpdater = remember {
        FrameCoalescedFloatUpdater { value -> dragTextOffsetState.value = value }
    }
    val swipeState = rememberAnchoredDraggableState(GestureAnchor.Center)
    DisposableEffect(dragTextFrameUpdater) {
        onDispose(dragTextFrameUpdater::dispose)
    }
    val draggableState = rememberDraggableState { delta ->
        val nextOffset = (dragOffsetState.value + delta).coerceIn(-240f, 240f)
        dragOffsetState.value = nextOffset
        dragTextFrameUpdater.submit(nextOffset)
    }

    fun setDeterministicState(
        dragOffset: Float,
        anchor: GestureAnchor,
    ) {
        dragOffsetState.value = dragOffset
        dragTextFrameUpdater.submit(dragOffset)
        swipeState.snapTo(anchor)
    }

    LazyColumn(
        items = listOf("drag-swipe"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        ScenarioSection(
            kind = ScenarioKind.Stress,
            title = stringResource(R.string.demo_gesture_drag_swipe_title),
            subtitle = stringResource(R.string.demo_gesture_drag_swipe_summary),
        ) {
            val swipeCurrentValue = swipeState.currentValue.value
            val swipeTargetValue = swipeState.targetValue.value
            val swipeOffsetValue = swipeState.currentOffsetPx.value ?: 0f
            val swipeVisualOffset = animateFloatAsState(
                targetValue = when (swipeCurrentValue) {
                    GestureAnchor.Left -> -112f
                    GestureAnchor.Right -> 112f
                    GestureAnchor.Center -> 0f
                },
                animationSpec = spring(durationMillis = 280),
            )
            val swipeVisualColor = animateColorAsState(
                targetValue = when (swipeCurrentValue) {
                    GestureAnchor.Left -> 0xFFDBEAFE.toInt()
                    GestureAnchor.Right -> 0xFFD9FBE8.toInt()
                    GestureAnchor.Center -> 0xFFF1F5F9.toInt()
                },
                animationSpec = tween(durationMillis = 220),
            )
            Text(
                text = stringResource(
                    R.string.demo_gesture_drag_state,
                    dragTextOffsetState.value.roundToInt(),
                    gestureAnchorLabel(swipeCurrentValue),
                    gestureAnchorLabel(swipeTargetValue),
                    swipeOffsetValue.roundToInt(),
                ),
                modifier = Modifier.gestureScenarioTarget(scenario, DemoAutomationRole.State),
            )
            Button(
                text = stringResource(R.string.demo_gesture_drag_action),
                onClick = { setDeterministicState(120f, GestureAnchor.Right) },
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_gesture_reset),
                variant = ButtonVariant.Outlined,
                onClick = { setDeterministicState(0f, GestureAnchor.Center) },
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
            Surface(
                variant = SurfaceVariant.Variant,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 10.dp)
                    .gesturePriority(GesturePriority.High)
                    .draggable(
                        state = draggableState,
                        orientation = GestureOrientation.Horizontal,
                    )
                    .graphicsLayer(translationX = dragOffsetState.value)
                    .padding(12.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(text = stringResource(R.string.demo_gesture_drag_target))
            }
            Text(
                text = stringResource(
                    R.string.demo_gesture_drag_value,
                    dragTextOffsetState.value.roundToInt(),
                ),
                modifier = Modifier
                    .margin(top = 6.dp)
                    .testTag(DemoGestureTestTags.GESTURE_DRAG_VALUE),
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
                            -120f to GestureAnchor.Left,
                            0f to GestureAnchor.Center,
                            120f to GestureAnchor.Right,
                        ),
                        orientation = GestureOrientation.Horizontal,
                    )
                    .graphicsLayer(translationX = swipeVisualOffset.value)
                    .backgroundColor(swipeVisualColor.value)
                    .padding(12.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.SecondaryTarget),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_gesture_swipe_target,
                        gestureAnchorLabel(swipeCurrentValue),
                    ),
                )
            }
            Text(
                text = stringResource(
                    R.string.demo_gesture_swipe_current,
                    gestureAnchorLabel(swipeCurrentValue),
                ),
                modifier = Modifier
                    .margin(top = 6.dp)
                    .testTag(DemoGestureTestTags.GESTURE_SWIPE_VALUE),
            )
            Text(
                text = stringResource(
                    R.string.demo_gesture_swipe_target_value,
                    gestureAnchorLabel(swipeTargetValue),
                ),
                color = TextDefaults.secondaryColor(),
                style = UiTextStyle(fontSizeSp = 12.sp),
                modifier = Modifier.testTag(DemoGestureTestTags.GESTURE_SWIPE_TARGET_VALUE),
            )
            Text(
                text = stringResource(
                    R.string.demo_gesture_swipe_offset,
                    swipeOffsetValue.roundToInt(),
                ),
                color = TextDefaults.secondaryColor(),
                style = UiTextStyle(fontSizeSp = 12.sp),
                modifier = Modifier.testTag(DemoGestureTestTags.GESTURE_SWIPE_OFFSET_VALUE),
            )
            Text(
                text = stringResource(
                    when (swipeCurrentValue) {
                        GestureAnchor.Left -> R.string.demo_gesture_swipe_feedback_left
                        GestureAnchor.Right -> R.string.demo_gesture_swipe_feedback_right
                        GestureAnchor.Center -> R.string.demo_gesture_swipe_feedback_center
                    },
                ),
                color = TextDefaults.secondaryColor(),
                style = UiTextStyle(fontSizeSp = 12.sp),
                modifier = Modifier.margin(top = 4.dp),
            )
        }
    }
}

private fun UiTreeBuilder.TransformGestureFixture(scenario: DemoScenarioSpec?) {
    val pointerEventState = remember { mutableStateOf<PointerEventType?>(null) }
    val scaleState = remember { mutableStateOf(1f) }
    val rotationState = remember { mutableStateOf(0f) }
    val panXState = remember { mutableStateOf(0f) }
    val panYState = remember { mutableStateOf(0f) }
    val lastPanDeltaXState = remember { mutableStateOf(0) }
    val lastPanDeltaYState = remember { mutableStateOf(0) }
    val lastRotationDeltaState = remember { mutableStateOf(0f) }
    val hasTransformDeltaState = remember { mutableStateOf(false) }
    val transformState = rememberTransformableState { zoom, panX, panY, rotation ->
        scaleState.value = (scaleState.value * zoom).coerceIn(0.6f, 2.2f)
        panXState.value = (panXState.value + panX).coerceIn(-120f, 120f)
        panYState.value = (panYState.value + panY).coerceIn(-120f, 120f)
        rotationState.value += rotation
        lastPanDeltaXState.value = panX.roundToInt()
        lastPanDeltaYState.value = panY.roundToInt()
        lastRotationDeltaState.value = rotation
        hasTransformDeltaState.value = true
    }

    fun reset() {
        pointerEventState.value = null
        scaleState.value = 1f
        rotationState.value = 0f
        panXState.value = 0f
        panYState.value = 0f
        lastPanDeltaXState.value = 0
        lastPanDeltaYState.value = 0
        lastRotationDeltaState.value = 0f
        hasTransformDeltaState.value = false
    }

    LazyColumn(
        items = listOf("transform"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        ScenarioSection(
            kind = ScenarioKind.Visual,
            title = stringResource(R.string.demo_gesture_transform_title),
            subtitle = stringResource(R.string.demo_gesture_transform_summary),
        ) {
            Text(
                text = stringResource(
                    R.string.demo_gesture_transform_state,
                    scaleState.value,
                    panXState.value.roundToInt(),
                    panYState.value.roundToInt(),
                    rotationState.value,
                ),
                style = UiTextStyle(fontSizeSp = 13.sp),
                modifier = Modifier.gestureScenarioTarget(scenario, DemoAutomationRole.State),
            )
            Button(
                text = stringResource(R.string.demo_gesture_transform_action),
                onClick = {
                    scaleState.value = 1.25f
                    panXState.value = 32f
                    panYState.value = 16f
                    rotationState.value = 12f
                    lastPanDeltaXState.value = 32
                    lastPanDeltaYState.value = 16
                    lastRotationDeltaState.value = 12f
                    hasTransformDeltaState.value = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_gesture_reset),
                variant = ButtonVariant.Outlined,
                onClick = ::reset,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
            Surface(
                variant = SurfaceVariant.Variant,
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .margin(top = 10.dp)
                    .gesturePriority(GesturePriority.High)
                    .backgroundColor(0xFFEFF6FF.toInt())
                    .padding(8.dp)
                    .pointerInput(key = "transform-pointer") { event ->
                        pointerEventState.value = event.type
                        PointerEventResult.Ignored
                    }
                    .transformable(state = transformState)
                    .gestureScenarioTarget(scenario, DemoAutomationRole.Target),
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
                        text = stringResource(R.string.demo_gesture_transform_target),
                        style = UiTextStyle(fontSizeSp = 15.sp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.demo_gesture_transform_instruction),
                color = TextDefaults.secondaryColor(),
                style = UiTextStyle(fontSizeSp = 12.sp),
                modifier = Modifier.margin(top = 6.dp),
            )
            Text(
                text = stringResource(
                    R.string.demo_gesture_pointer_event,
                    pointerEventState.value?.name
                        ?: stringResource(R.string.demo_gesture_pointer_none),
                ),
                color = TextDefaults.secondaryColor(),
            )
            Text(
                text = if (hasTransformDeltaState.value) {
                    stringResource(
                        R.string.demo_gesture_transform_delta,
                        lastPanDeltaXState.value,
                        lastPanDeltaYState.value,
                        lastRotationDeltaState.value,
                    )
                } else {
                    stringResource(R.string.demo_gesture_transform_idle)
                },
                color = TextDefaults.secondaryColor(),
                style = UiTextStyle(fontSizeSp = 12.sp),
                modifier = Modifier.margin(top = 4.dp),
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
                    text = stringResource(R.string.demo_gesture_transform_observation),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
        }
    }
}

private enum class GestureAnchor {
    Left,
    Center,
    Right,
}

private fun UiTreeBuilder.gestureAnchorLabel(anchor: GestureAnchor): String = stringResource(
    when (anchor) {
        GestureAnchor.Left -> R.string.demo_gesture_anchor_left
        GestureAnchor.Center -> R.string.demo_gesture_anchor_center
        GestureAnchor.Right -> R.string.demo_gesture_anchor_right
    },
)

private fun Modifier.gestureScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
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
        if (scheduled) return
        scheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!hasPending) return
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
