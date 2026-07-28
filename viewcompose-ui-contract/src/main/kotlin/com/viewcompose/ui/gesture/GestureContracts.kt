package com.viewcompose.ui.gesture

/**
 * 手势主轴方向，用于拖拽、滑动和嵌套滚动等契约。
 * Primary gesture axis used by drag, swipe, and nested-scroll contracts.
 */
enum class GestureOrientation {
    Horizontal,
    Vertical,
    Free,
}

/**
 * 手势识别优先级，高优先级可让 renderer 更早分发或抢占事件。
 * Gesture recognition priority; high priority lets renderers dispatch earlier or take over events.
 */
enum class GesturePriority {
    Default,
    High,
}

/**
 * 手势取消原因，帮助业务或测试区分系统取消、抢占和生命周期清理。
 * Gesture cancellation reason, helping app code or tests distinguish system cancel, takeover, and lifecycle cleanup.
 */
enum class GestureCancellationReason {
    SystemCancelled,
    TransformTookOver,
    PointerInputConsumed,
    ModifierChanged,
    Disposed,
}

/**
 * 指针事件类型，对齐平台 down/move/up/cancel 语义。
 * Pointer event type aligned with platform down/move/up/cancel semantics.
 */
enum class PointerEventType {
    Down,
    Move,
    Up,
    Cancel,
}

/**
 * 指针事件处理结果，renderer 可据此决定是否继续向后分发。
 * Pointer event handling result; renderers may use it to decide whether dispatch should continue.
 */
enum class PointerEventResult {
    Ignored,
    Consumed,
}

/**
 * 单个指针在一次事件中的状态快照。
 * Snapshot of one pointer inside a pointer event.
 */
data class PointerChange(
    val id: Long,
    val x: Float,
    val y: Float,
    val pressed: Boolean,
)

/**
 * 一次平台指针事件的跨平台描述。
 * Cross-platform representation of one platform pointer event.
 */
data class PointerEvent(
    val type: PointerEventType,
    val uptimeMillis: Long,
    val changes: List<PointerChange>,
)

/**
 * 声明式滑动方向，使用逻辑方向而不是绝对左右时由 renderer 处理布局方向。
 * Declarative swipe direction; renderers handle layout direction when logical directions are used.
 */
enum class SwipeDirection {
    StartToEnd,
    EndToStart,
    TopToBottom,
    BottomToTop,
}

/**
 * 多指变换增量，pan 使用像素，zoom 为倍率，rotation 为角度。
 * Multi-pointer transform delta; pan uses pixels, zoom is a scale factor, and rotation is in degrees.
 */
data class TransformDelta(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f,
)
