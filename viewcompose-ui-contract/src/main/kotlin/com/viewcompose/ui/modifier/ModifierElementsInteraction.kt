package com.viewcompose.ui.modifier

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.PointerEvent
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.TransformDelta

data class ClickableModifierElement(
    val onClick: () -> Unit,
) : ModifierElement

data class PointerInputModifierElement(
    val key: Any,
    val onEvent: (PointerEvent) -> PointerEventResult,
) : ModifierElement

data class CombinedClickableModifierElement(
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val onDoubleClick: (() -> Unit)?,
    val onLongClick: (() -> Unit)?,
) : ModifierElement

data class DraggableModifierElement(
    val enabled: Boolean,
    val orientation: GestureOrientation,
    val onDragStarted: (() -> Unit)?,
    val onDragStopped: ((velocity: Float) -> Unit)?,
    val onDelta: (delta: Float) -> Unit,
) : ModifierElement

data class AnchoredDraggableModifierElement(
    val enabled: Boolean,
    val orientation: GestureOrientation,
    val anchorOffsetsPx: List<Float>,
    val currentOffsetPx: Float?,
    val onDelta: (delta: Float) -> Unit,
    val onSettleToOffset: (offsetPx: Float) -> Unit,
) : ModifierElement

data class TransformableModifierElement(
    val enabled: Boolean,
    val onTransform: (TransformDelta) -> Unit,
) : ModifierElement

data class GesturePriorityModifierElement(
    val priority: GesturePriority,
) : ModifierElement

data class SemanticsModifierElement(
    val configuration: SemanticsConfiguration,
) : ModifierElement

data class TestTagModifierElement(
    val tag: String,
) : ModifierElement

data class OverlayAnchorModifierElement(
    val anchorId: String,
) : ModifierElement

class NativeViewElement(
    val stableKey: Any,
    val configure: (Any) -> Unit,
) : ModifierElement {
    override fun equals(other: Any?): Boolean =
        other is NativeViewElement && stableKey == other.stableKey

    override fun hashCode(): Int = stableKey.hashCode()
}
