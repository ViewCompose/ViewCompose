package com.viewcompose.gesture.samples

import com.viewcompose.gesture.anchoredDraggable
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.gesture.draggable
import com.viewcompose.gesture.draggableAnchors
import com.viewcompose.gesture.nestedScroll
import com.viewcompose.gesture.pointerInput
import com.viewcompose.gesture.rememberAnchoredDraggableState
import com.viewcompose.gesture.rememberDraggableState
import com.viewcompose.gesture.rememberTransformableState
import com.viewcompose.gesture.rememberToggleDragState
import com.viewcompose.gesture.ToggleDragCompletion
import com.viewcompose.gesture.toggleDraggable
import com.viewcompose.gesture.transformable
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp

fun rawPointerInput(): Modifier {
    return Modifier.pointerInput(key = "canvas-input") { event ->
        if (event.changes.any { it.pressed }) {
            PointerEventResult.Consumed
        } else {
            PointerEventResult.Ignored
        }
    }
}

fun combinedClick(onOpen: () -> Unit): Modifier {
    return Modifier.combinedClickable(
        onClick = onOpen,
        onLongClick = { /* Show contextual actions. */ },
    )
}

private fun openItem() = Unit

private fun openContextMenu() = Unit

// DOCS_REGION_START(gesture-combined-click)
val actions = Modifier.combinedClickable(
    onClick = { openItem() },
    onLongClick = { openContextMenu() },
)
// DOCS_REGION_END(gesture-combined-click)

fun UiTreeBuilder.dragState(): Modifier {
    val offsetPx = mutableStateOf(0f)
    val dragState = rememberDraggableState { deltaPx ->
        offsetPx.value += deltaPx
    }
    return Modifier.draggable(
        state = dragState,
        orientation = GestureOrientation.Horizontal,
    )
}

fun UiTreeBuilder.anchoredDragState(): Modifier {
// DOCS_REGION_START(gesture-anchored-drag)
val anchors = draggableAnchors<SheetValue> {
    anchor(0f, SheetValue.Collapsed)
    anchor(480f, SheetValue.Expanded)
}
val sheet = rememberAnchoredDraggableState(SheetValue.Collapsed)
val modifier = Modifier.anchoredDraggable(
    state = sheet,
    anchors = anchors,
    orientation = GestureOrientation.Vertical,
)
// DOCS_REGION_END(gesture-anchored-drag)
    return modifier
}

fun UiTreeBuilder.toggleDragState(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Triple<Modifier, Float, ToggleDragCompletion?> {
    val state = rememberToggleDragState(
        checked = checked,
        checkedAnchorOffsetPx = 72f,
        onCheckedChange = onCheckedChange,
    )
    return Triple(
        Modifier.toggleDraggable(state),
        state.progress.value,
        state.lastCompletion.value,
    )
}

fun UiTreeBuilder.toggleDragModuleSample(
    checked: Boolean,
    rtl: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Modifier {
    val density = Environment.density
// DOCS_REGION_START(gesture-toggle-drag)
val drag = rememberToggleDragState(
    checked = checked,
    checkedAnchorOffsetPx = density.toPx(if (rtl) (-20).dp else 20.dp),
    onCheckedChange = onCheckedChange,
)
val target = Modifier
    .clickable { onCheckedChange(!checked) }
    .toggleDraggable(drag)
// DOCS_REGION_END(gesture-toggle-drag)
    return target
}

fun UiTreeBuilder.transformState(): Modifier {
    val scale = mutableStateOf(1f)
    val translationX = mutableStateOf(0f)
    val transformState = rememberTransformableState { zoom, panX, _, _ ->
        scale.value *= zoom
        translationX.value += panX
    }
    return Modifier.transformable(state = transformState)
}

fun nestedScrollConnection(): Pair<Modifier, NestedScrollDispatcher> {
    val dispatcher = NestedScrollDispatcher()
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: ScrollDelta,
            source: NestedScrollSource,
        ): ScrollDelta {
            val consumedY = available.y.coerceIn(-24f, 24f)
            return ScrollDelta(x = 0f, y = consumedY)
        }
    }
    return Modifier.nestedScroll(connection, dispatcher) to dispatcher
}

enum class SheetValue {
    Collapsed,
    Expanded,
}
