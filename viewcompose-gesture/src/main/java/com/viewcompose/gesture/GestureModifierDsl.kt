package com.viewcompose.gesture

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.GestureCancellationReason
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

/**
 * Appends a renderer-neutral raw pointer-event handler.
 *
 * The renderer calls [onEvent] synchronously in modifier dispatch order. Returning
 * [PointerEventResult.Consumed] participates in subsequent dispatch arbitration; it does not mutate
 * the immutable [PointerEvent]. Changing [key] replaces the modifier element's logical input and is
 * useful when a handler's recognition context must restart.
 *
 * @param key identity input stored with the handler; use a stable value while recognition should continue
 * @param onEvent callback for down, move, up, and cancel events
 * @return this chain followed by the pointer-input element
 * @sample com.viewcompose.gesture.samples.rawPointerInput
 */
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

/**
 * Appends one recognizer for click, double-click, and long-click callbacks.
 *
 * The renderer owns timing, movement slop, competition, and callback ordering. When [enabled] is
 * false or every callback is `null`, this function returns the receiver unchanged and adds no
 * native recognition work.
 *
 * @param enabled whether the recognizer participates in pointer dispatch
 * @param onClick optional single-click callback
 * @param onDoubleClick optional double-click callback
 * @param onLongClick optional long-press callback
 * @return the unchanged receiver when inactive, otherwise a chain with the clickable element
 * @sample com.viewcompose.gesture.samples.combinedClick
 */
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

/**
 * Connects [state] to the renderer's one-dimensional drag recognizer.
 *
 * Raw incremental deltas are forwarded synchronously to [DraggableState.dispatchRawDelta]. The
 * renderer determines touch slop and axis locking. A successful gesture calls [onDragStarted]
 * before deltas and [onDragStopped] with signed terminal velocity in physical pixels per second.
 * Cancellation calls [onDragCancelled] instead of normal stop. Modifier replacement, disposal,
 * pointer consumption, system cancellation, or transform takeover can cancel recognition.
 *
 * @param state stable application-owned delta receiver
 * @param orientation allowed drag axis; free orientation locks to the dominant axis
 * @param enabled whether the recognizer participates in pointer dispatch
 * @param onDragStarted callback after recognition activates
 * @param onDragStopped callback after normal completion with signed axis velocity
 * @param onDragCancelled callback after abnormal termination
 * @return this chain followed by the drag element
 * @sample com.viewcompose.gesture.samples.dragState
 */
fun Modifier.draggable(
    state: DraggableState,
    orientation: GestureOrientation = GestureOrientation.Horizontal,
    enabled: Boolean = true,
    onDragStarted: (() -> Unit)? = null,
    onDragStopped: ((velocity: Float) -> Unit)? = null,
    onDragCancelled: ((reason: GestureCancellationReason) -> Unit)? = null,
): Modifier {
    return then(
        DraggableModifierElement(
            enabled = enabled,
            orientation = orientation,
            onDragStarted = onDragStarted,
            onDragStopped = onDragStopped,
            onDelta = state::dispatchRawDelta,
            onDragCancelled = onDragCancelled,
        ),
    )
}

/**
 * Connects [state] to a drag recognizer that settles to discrete [anchors].
 *
 * Every invocation installs the latest anchor set into [state] before appending the modifier. Raw
 * movement updates the state's visual offset, while normal completion commits the renderer-selected
 * nearest anchor synchronously. This release does not animate settle movement. Free orientation is
 * unsupported because anchor offsets describe one physical axis.
 *
 * @param state remembered semantic value and offset owner
 * @param anchors non-empty validated positions used for drag and settle
 * @param orientation horizontal or vertical drag axis
 * @param enabled whether the recognizer participates in pointer dispatch
 * @param onDragCancelled callback when an active drag ends without settling
 * @return this chain followed by the anchored-drag element
 * @throws IllegalArgumentException if [orientation] is [GestureOrientation.Free]
 * @sample com.viewcompose.gesture.samples.anchoredDragState
 */
fun <T> Modifier.anchoredDraggable(
    state: AnchoredDraggableState<T>,
    anchors: DraggableAnchors<T>,
    orientation: GestureOrientation = GestureOrientation.Horizontal,
    enabled: Boolean = true,
    onDragCancelled: ((reason: GestureCancellationReason) -> Unit)? = null,
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
            onDragCancelled = onDragCancelled,
        ),
    )
}

/**
 * Connects [state] to a combined pan, zoom, and rotation recognizer.
 *
 * The renderer performs multi-pointer activation and sends incremental deltas synchronously.
 * [onTransformStarted] precedes transform delivery, [onTransformStopped] follows normal completion,
 * and [onTransformCancelled] reports abnormal termination instead of normal stop. Transform
 * takeover can cancel an active one-dimensional drag on the same dispatch path.
 *
 * @param state stable application-owned transform receiver
 * @param enabled whether the recognizer participates in pointer dispatch
 * @param onTransformStarted callback after multi-pointer transform activation
 * @param onTransformStopped callback after normal completion
 * @param onTransformCancelled callback after abnormal termination
 * @return this chain followed by the transform element
 * @sample com.viewcompose.gesture.samples.transformState
 */
fun Modifier.transformable(
    state: TransformableState,
    enabled: Boolean = true,
    onTransformStarted: (() -> Unit)? = null,
    onTransformStopped: (() -> Unit)? = null,
    onTransformCancelled: ((reason: GestureCancellationReason) -> Unit)? = null,
): Modifier {
    return then(
        TransformableModifierElement(
            enabled = enabled,
            onTransform = state::dispatchTransform,
            onTransformStarted = onTransformStarted,
            onTransformStopped = onTransformStopped,
            onTransformCancelled = onTransformCancelled,
        ),
    )
}

/**
 * Appends a recognition-priority hint for gestures on this node and its dispatch path.
 *
 * High priority gives a recognizer an earlier opportunity; it does not guarantee consumption when
 * that recognizer rejects the event. The Android renderer defines the exact competition order.
 *
 * @param priority requested recognition opportunity
 * @return this chain followed by the priority element
 */
fun Modifier.gesturePriority(
    priority: GesturePriority = GesturePriority.Default,
): Modifier {
    return then(
        GesturePriorityModifierElement(
            priority = priority,
        ),
    )
}

/**
 * Joins the renderer's nested-scroll chain with [connection].
 *
 * Ancestor pre-scroll callbacks run before child consumption and post-scroll callbacks run after it.
 * Supplying [dispatcher] attaches that application handle to the mounted chain for imperative
 * dispatch and detaches it when the modifier binding is disposed. A detached dispatcher consumes
 * zero. Callbacks are synchronous and Android integrations should dispatch on the UI thread.
 *
 * @param connection receiver for ancestor pre/post distance and velocity offers
 * @param dispatcher optional reusable handle for application-initiated nested scrolling
 * @return this chain followed by the nested-scroll element
 * @sample com.viewcompose.gesture.samples.nestedScrollConnection
 */
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
