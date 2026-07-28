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
 * 注册原始 pointer event 处理器。
 * Registers a raw pointer-event handler.
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
 * 同时支持 click、double-click 和 long-click 的点击 modifier。
 * Click modifier that can handle click, double-click, and long-click.
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
 * 将 [DraggableState] 连接到 renderer 的一维拖拽识别器。
 * Connects [DraggableState] to the renderer's one-dimensional drag recognizer.
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
 * 将拖拽位置限制到一组离散 anchors。
 * Constrains drag position to a discrete set of anchors.
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
 * 注册缩放、平移和旋转的组合 transform 手势。
 * Registers a combined transform gesture for zoom, pan, and rotation.
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
 * 调整同一节点或祖先链上手势竞争时的优先级。
 * Adjusts priority when gestures compete on the same node or ancestor chain.
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
 * 接入嵌套滚动链，可选 dispatcher 用于业务侧主动分发滚动。
 * Joins the nested-scroll chain, with an optional dispatcher for app-initiated scroll dispatch.
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
