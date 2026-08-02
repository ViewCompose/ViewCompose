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
import com.viewcompose.gesture.transformable
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.widget.core.UiTreeBuilder

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
    val anchors = draggableAnchors<SheetValue> {
        anchor(offsetPx = 0f, value = SheetValue.Collapsed)
        anchor(offsetPx = 480f, value = SheetValue.Expanded)
    }
    val state = rememberAnchoredDraggableState(SheetValue.Collapsed)
    return Modifier.anchoredDraggable(
        state = state,
        anchors = anchors,
        orientation = GestureOrientation.Vertical,
    )
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
