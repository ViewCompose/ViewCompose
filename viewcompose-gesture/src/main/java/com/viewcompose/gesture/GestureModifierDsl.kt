package com.viewcompose.gesture

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.PointerEvent
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.modifier.AnchoredDraggableModifierElement
import com.viewcompose.ui.modifier.CombinedClickableModifierElement
import com.viewcompose.ui.modifier.DraggableModifierElement
import com.viewcompose.ui.modifier.GesturePriorityModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NestedScrollModifierElement
import com.viewcompose.ui.modifier.PointerInputModifierElement
import com.viewcompose.ui.modifier.TransformableModifierElement

fun Modifier.pointerInput(
    key: Any = Unit,
    onEvent: (PointerEvent) -> PointerEventResult,
): Modifier {
    return then(
        PointerInputModifierElement(
            key = key,
            onEvent = onEvent,
        ),
    )
}

fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    if (!enabled || (onClick == null && onDoubleClick == null && onLongClick == null)) {
        return this
    }
    return then(
        CombinedClickableModifierElement(
            enabled = enabled,
            onClick = onClick,
            onDoubleClick = onDoubleClick,
            onLongClick = onLongClick,
        ),
    )
}

fun Modifier.draggable(
    state: DraggableState,
    orientation: GestureOrientation = GestureOrientation.Horizontal,
    enabled: Boolean = true,
    onDragStarted: (() -> Unit)? = null,
    onDragStopped: ((velocity: Float) -> Unit)? = null,
): Modifier {
    return then(
        DraggableModifierElement(
            enabled = enabled,
            orientation = orientation,
            onDragStarted = onDragStarted,
            onDragStopped = onDragStopped,
            onDelta = state::dispatchRawDelta,
        ),
    )
}

fun <T> Modifier.anchoredDraggable(
    state: AnchoredDraggableState<T>,
    anchors: DraggableAnchors<T>,
    orientation: GestureOrientation = GestureOrientation.Horizontal,
    enabled: Boolean = true,
): Modifier {
    require(orientation != GestureOrientation.Free) {
        "anchoredDraggable only supports Horizontal or Vertical orientation."
    }
    state.updateAnchors(anchors)
    return then(
        AnchoredDraggableModifierElement(
            enabled = enabled,
            orientation = orientation,
            anchorOffsetsPx = anchors.offsetsPx,
            currentOffsetPx = state.currentOffsetPx.value,
            onDelta = state::dispatchRawDelta,
            onSettleToOffset = state::settleToOffset,
        ),
    )
}

fun Modifier.transformable(
    state: TransformableState,
    enabled: Boolean = true,
): Modifier {
    return then(
        TransformableModifierElement(
            enabled = enabled,
            onTransform = state::dispatchTransform,
        ),
    )
}

fun Modifier.gesturePriority(
    priority: GesturePriority = GesturePriority.Default,
): Modifier {
    return then(
        GesturePriorityModifierElement(
            priority = priority,
        ),
    )
}

fun Modifier.nestedScroll(
    connection: NestedScrollConnection,
    dispatcher: NestedScrollDispatcher? = null,
): Modifier {
    return then(
        NestedScrollModifierElement(
            connection = connection,
            dispatcher = dispatcher,
        ),
    )
}
