package com.viewcompose.ui.gesture

enum class GestureOrientation {
    Horizontal,
    Vertical,
    Free,
}

enum class GesturePriority {
    Default,
    High,
}

enum class GestureCancellationReason {
    SystemCancelled,
    TransformTookOver,
    PointerInputConsumed,
    ModifierChanged,
    Disposed,
}

enum class PointerEventType {
    Down,
    Move,
    Up,
    Cancel,
}

enum class PointerEventResult {
    Ignored,
    Consumed,
}

data class PointerChange(
    val id: Long,
    val x: Float,
    val y: Float,
    val pressed: Boolean,
)

data class PointerEvent(
    val type: PointerEventType,
    val uptimeMillis: Long,
    val changes: List<PointerChange>,
)

enum class SwipeDirection {
    StartToEnd,
    EndToStart,
    TopToBottom,
    BottomToTop,
}

data class TransformDelta(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f,
)
