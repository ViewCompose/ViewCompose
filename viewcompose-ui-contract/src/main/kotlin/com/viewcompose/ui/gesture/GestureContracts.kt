package com.viewcompose.ui.gesture

/** Selects horizontal, vertical, or unrestricted motion for a gesture contract. */
enum class GestureOrientation {
    Horizontal,
    Vertical,
    Free,
}

/**
 * Requests the renderer's default recognition order or an earlier high-priority opportunity.
 *
 * Priority affects competition between recognizers; it does not guarantee consumption when a
 * high-priority recognizer rejects the event.
 */
enum class GesturePriority {
    Default,
    High,
}

/** Identifies why an active gesture ended without its normal completion callback. */
enum class GestureCancellationReason {
    SystemCancelled,
    TransformTookOver,
    PointerInputConsumed,
    ModifierChanged,
    Disposed,
}

/** Classifies a pointer event using down, move, up, and cancellation semantics. */
enum class PointerEventType {
    Down,
    Move,
    Up,
    Cancel,
}

/** Reports whether a handler ignored an event or consumed it for subsequent dispatch decisions. */
enum class PointerEventResult {
    Ignored,
    Consumed,
}

/**
 * Captures one pointer's platform-neutral state in a [PointerEvent].
 *
 * Coordinates use the renderer-defined local coordinate space and are normally physical pixels
 * on Android.
 *
 * @property id stable platform pointer identifier for the active gesture
 * @property x horizontal local coordinate
 * @property y vertical local coordinate
 * @property pressed whether the pointer remains down after this event
 */
data class PointerChange(
    val id: Long,
    val x: Float,
    val y: Float,
    val pressed: Boolean,
)

/**
 * Captures a platform pointer event without exposing the native event object.
 *
 * @property type semantic action represented by this event
 * @property uptimeMillis monotonic platform uptime in milliseconds
 * @property changes ordered snapshots for pointers participating in the event
 */
data class PointerEvent(
    val type: PointerEventType,
    val uptimeMillis: Long,
    val changes: List<PointerChange>,
)

/**
 * Selects a logical horizontal or physical vertical swipe direction.
 *
 * A renderer resolves start/end from the current layout direction. Top/bottom directions are
 * independent of layout direction.
 */
enum class SwipeDirection {
    StartToEnd,
    EndToStart,
    TopToBottom,
    BottomToTop,
}

/**
 * Captures the incremental pan, zoom, and rotation produced by a transform gesture.
 *
 * This value represents a delta since the previous callback, not an accumulated transform.
 *
 * @property panX horizontal translation in renderer-local units, normally physical pixels
 * @property panY vertical translation in renderer-local units, normally physical pixels
 * @property zoom multiplicative scale delta where `1.0` means no scale change
 * @property rotation clockwise rotation delta in degrees where `0.0` means no rotation
 */
data class TransformDelta(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val rotation: Float = 0f,
)
