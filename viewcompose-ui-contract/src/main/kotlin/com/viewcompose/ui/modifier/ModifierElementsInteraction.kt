package com.viewcompose.ui.modifier

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.GestureCancellationReason
import com.viewcompose.ui.gesture.PointerEvent
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.TransformDelta
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.focus.FocusProperties
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.focus.FocusState
import com.viewcompose.ui.input.KeyEvent

/**
 * Registers a click callback for a node.
 *
 * @property onClick callback invoked synchronously after the renderer accepts a click
 */
data class ClickableModifierElement(
    val onClick: () -> Unit,
) : ModifierElement

/**
 * Registers raw pointer-event handling with a semantic restart key.
 *
 * @property key identity used to replace pointer handling when declarative input changes
 * @property onEvent synchronous callback returning whether the event was consumed
 */
data class PointerInputModifierElement(
    val key: Any,
    val onEvent: (PointerEvent) -> PointerEventResult,
) : ModifierElement

/**
 * Registers optional single-click, double-click, and long-click callbacks as one recognizer.
 *
 * @property enabled whether the recognizer participates in input
 * @property onClick callback for an accepted single click, or `null` to disable that gesture
 * @property onDoubleClick callback for an accepted double click, or `null`
 * @property onLongClick callback for an accepted long press, or `null`
 */
data class CombinedClickableModifierElement(
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val onDoubleClick: (() -> Unit)?,
    val onLongClick: (() -> Unit)?,
) : ModifierElement

/**
 * Describes one-axis drag recognition and lifecycle callbacks.
 *
 * Delta and velocity units follow the renderer, normally physical pixels and pixels per second on
 * Android. Callbacks execute synchronously on the input thread.
 *
 * @property enabled whether drag recognition participates in input
 * @property orientation axis used to project pointer movement
 * @property onDragStarted optional callback invoked once after a drag is accepted
 * @property onDragStopped optional callback invoked once with terminal projected velocity
 * @property onDelta callback invoked for each accepted projected movement delta
 * @property onDragCancelled optional callback invoked instead of normal stop when recognition cancels
 */
data class DraggableModifierElement(
    val enabled: Boolean,
    val orientation: GestureOrientation,
    val onDragStarted: (() -> Unit)?,
    val onDragStopped: ((velocity: Float) -> Unit)?,
    val onDelta: (delta: Float) -> Unit,
    val onDragCancelled: ((reason: GestureCancellationReason) -> Unit)? = null,
) : ModifierElement

/**
 * Describes one-axis dragging that settles to a declared pixel anchor.
 *
 * @property enabled whether drag recognition participates in input
 * @property orientation axis used to project pointer movement
 * @property anchorOffsetsPx platform-pixel anchor positions available for settling
 * @property currentOffsetPx externally controlled current position, or `null` before resolution
 * @property onDelta callback for each accepted projected movement delta in pixels
 * @property onSettleToOffset callback requesting the selected anchor offset in pixels
 * @property onDragCancelled callback invoked when an active drag is cancelled
 */
data class AnchoredDraggableModifierElement(
    val enabled: Boolean,
    val orientation: GestureOrientation,
    val anchorOffsetsPx: List<Float>,
    val currentOffsetPx: Float?,
    val onDelta: (delta: Float) -> Unit,
    val onSettleToOffset: (offsetPx: Float) -> Unit,
    val onDragCancelled: ((reason: GestureCancellationReason) -> Unit)? = null,
) : ModifierElement

/**
 * Describes multi-pointer pan, zoom, and rotation recognition.
 *
 * @property enabled whether transform recognition participates in input
 * @property onTransform callback for each incremental transform delta
 * @property onTransformStarted optional callback invoked once when recognition starts
 * @property onTransformStopped optional callback invoked once on normal completion
 * @property onTransformCancelled optional callback invoked instead of stop on cancellation
 */
data class TransformableModifierElement(
    val enabled: Boolean,
    val onTransform: (TransformDelta) -> Unit,
    val onTransformStarted: (() -> Unit)? = null,
    val onTransformStopped: (() -> Unit)? = null,
    val onTransformCancelled: ((reason: GestureCancellationReason) -> Unit)? = null,
) : ModifierElement

/**
 * Requests a recognition priority for gesture elements in the same chain.
 *
 * @property priority default or early/takeover recognition policy
 */
data class GesturePriorityModifierElement(
    val priority: GesturePriority,
) : ModifierElement

/**
 * Installs an ancestor nested-scroll connection and optional imperative dispatcher.
 *
 * @property connection callback contract that consumes pre/post distance and velocity
 * @property dispatcher optional stable handle attached to the renderer's platform chain
 */
data class NestedScrollModifierElement(
    val connection: NestedScrollConnection,
    val dispatcher: NestedScrollDispatcher?,
) : ModifierElement

/**
 * Requests focus participation for the modified node.
 *
 * @property enabled whether the node may become a platform focus target
 */
data class FocusableModifierElement(
    val enabled: Boolean,
) : ModifierElement

/**
 * Associates a stable [FocusRequester] with the modified platform focus target.
 *
 * @property requester handle attached while the target is mounted
 */
data class FocusRequesterModifierElement(
    val requester: FocusRequester,
) : ModifierElement

/**
 * Applies declarative traversal and participation overrides.
 *
 * @property properties nullable policies merged in modifier-chain order
 */
data class FocusPropertiesModifierElement(
    val properties: FocusProperties,
) : ModifierElement

/**
 * Requests that a subtree participate as one focus-traversal group.
 *
 * @property enabled whether grouping is active
 */
data class FocusGroupModifierElement(
    val enabled: Boolean,
) : ModifierElement

/**
 * Registers a focus-state listener for the modified target.
 *
 * @property onFocusChanged callback invoked with renderer-reported distinct or platform updates
 */
data class OnFocusChangedModifierElement(
    val onFocusChanged: (FocusState) -> Unit,
) : ModifierElement

/**
 * Registers a key callback for the preview/tunneling phase before focused-child dispatch.
 *
 * @property onPreviewKeyEvent callback returning `true` to consume the event
 */
data class PreviewKeyEventModifierElement(
    val onPreviewKeyEvent: (KeyEvent) -> Boolean,
) : ModifierElement

/**
 * Registers a key callback for the bubbling phase after focused-child dispatch.
 *
 * @property onKeyEvent callback returning `true` to consume the event
 */
data class KeyEventModifierElement(
    val onKeyEvent: (KeyEvent) -> Boolean,
) : ModifierElement

/**
 * Adds accessibility and testing semantics to a node.
 *
 * @property configuration semantic values merged in modifier-chain order
 */
data class SemanticsModifierElement(
    val configuration: SemanticsConfiguration,
) : ModifierElement

/**
 * Associates a stable testing identifier with a rendered node.
 *
 * @property tag caller-defined tag exposed through the renderer's testing bridge
 */
data class TestTagModifierElement(
    val tag: String,
) : ModifierElement

/**
 * Publishes a rendered node as an anchor for an overlay host.
 *
 * @property anchorId identifier used to match overlay requests with native bounds
 */
data class OverlayAnchorModifierElement(
    val anchorId: String,
) : ModifierElement

/**
 * Applies a platform-specific configuration callback without placing a native type in this module.
 *
 * Equality and hashing use only [stableKey], deliberately ignoring callback instance identity.
 * Renderers invoke [configure] with their native view on the renderer thread; callers must cast or
 * validate the value expected by the platform-specific extension that created this element.
 *
 * @property stableKey semantic identity controlling reconciliation
 * @property configure native-view update callback
 */
class NativeViewElement(
    val stableKey: Any,
    val configure: (Any) -> Unit,
) : ModifierElement {
    /**
     * Returns whether [other] is a native-view element with an equal [stableKey].
     *
     * @param other value to compare
     * @return `true` when stable keys are equal
     */
    override fun equals(other: Any?): Boolean =
        other is NativeViewElement && stableKey == other.stableKey

    /**
     * Returns the hash of [stableKey].
     *
     * @return hash consistent with [equals]
     */
    override fun hashCode(): Int = stableKey.hashCode()
}
