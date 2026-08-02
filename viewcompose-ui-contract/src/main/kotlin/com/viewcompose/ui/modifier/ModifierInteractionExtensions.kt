package com.viewcompose.ui.modifier

import com.viewcompose.ui.focus.FocusPropertiesReceiver
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.focus.FocusState
import com.viewcompose.ui.input.KeyEvent

/**
 * Appends click handling to the modified node.
 *
 * The renderer invokes [onClick] synchronously on its input thread after accepting a click; Android
 * renderers use the main thread. Later click elements override earlier ones during resolution.
 *
 * @receiver modifier chain to extend
 * @param onClick callback for accepted clicks
 * @return a new modifier chain
 */
fun Modifier.clickable(onClick: () -> Unit): Modifier {
    return then(
        ClickableModifierElement(onClick),
    )
}

/**
 * Appends platform focus participation to the modified node.
 *
 * @receiver modifier chain to extend
 * @param enabled whether the node may receive focus
 * @return a new modifier chain
 */
fun Modifier.focusable(enabled: Boolean = true): Modifier {
    return then(
        FocusableModifierElement(enabled),
    )
}

/**
 * Attaches [requester] to the modified focus target while it is mounted.
 *
 * @receiver modifier chain to extend
 * @param requester stable imperative focus handle
 * @return a new modifier chain
 */
fun Modifier.focusRequester(requester: FocusRequester): Modifier {
    return then(
        FocusRequesterModifierElement(requester),
    )
}

/**
 * Appends directional focus policies collected by [properties].
 *
 * The DSL block executes immediately while constructing the immutable modifier element. Later
 * non-null policies override earlier values.
 *
 * @receiver modifier chain to extend
 * @param properties assignments to collect into a focus-property element
 * @return a new modifier chain
 */
fun Modifier.focusProperties(
    properties: FocusPropertiesReceiver.() -> Unit,
): Modifier {
    val receiver = FocusPropertiesReceiver().apply(properties)
    return then(
        FocusPropertiesModifierElement(receiver.build()),
    )
}

/**
 * Marks the modified subtree as one platform focus-traversal group.
 *
 * @receiver modifier chain to extend
 * @param enabled whether grouping is active
 * @return a new modifier chain
 */
fun Modifier.focusGroup(enabled: Boolean = true): Modifier {
    return then(
        FocusGroupModifierElement(enabled),
    )
}

/**
 * Appends a listener for focus changes on the modified target and its descendants.
 *
 * @receiver modifier chain to extend
 * @param onFocusChanged synchronous renderer callback containing the latest focus snapshot
 * @return a new modifier chain
 */
fun Modifier.onFocusChanged(
    onFocusChanged: (FocusState) -> Unit,
): Modifier {
    return then(
        OnFocusChangedModifierElement(onFocusChanged),
    )
}

/**
 * Appends a key handler for the preview phase before focused-child dispatch.
 *
 * Returning `true` consumes the event and prevents later dispatch.
 *
 * @receiver modifier chain to extend
 * @param onPreviewKeyEvent callback invoked synchronously for preview-phase key events
 * @return a new modifier chain
 */
fun Modifier.onPreviewKeyEvent(
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
): Modifier {
    return then(
        PreviewKeyEventModifierElement(onPreviewKeyEvent),
    )
}

/**
 * Appends a key handler for the bubbling phase after focused-child dispatch.
 *
 * Returning `true` consumes the event for remaining ancestors.
 *
 * @receiver modifier chain to extend
 * @param onKeyEvent callback invoked synchronously for bubbling key events
 * @return a new modifier chain
 */
fun Modifier.onKeyEvent(
    onKeyEvent: (KeyEvent) -> Boolean,
): Modifier {
    return then(
        KeyEventModifierElement(onKeyEvent),
    )
}

/**
 * Appends an accessibility content description through [semantics].
 *
 * @receiver modifier chain to extend
 * @param description localized description, or `null` to leave earlier semantics unchanged
 * @return a new modifier chain
 */
fun Modifier.contentDescription(description: String?): Modifier {
    return semantics {
        contentDescription = description
    }
}

/**
 * Appends a caller-defined identifier for testing and diagnostics.
 *
 * This contract does not require global uniqueness or reject blank tags.
 *
 * @receiver modifier chain to extend
 * @param tag identifier exposed through the renderer testing bridge
 * @return a new modifier chain
 */
fun Modifier.testTag(tag: String): Modifier {
    return then(
        TestTagModifierElement(tag),
    )
}

/**
 * Publishes the modified node's native bounds under [anchorId] for overlay positioning.
 *
 * Later anchor elements override earlier ones on the same node. The overlay host decides behavior
 * for duplicate IDs across different mounted nodes.
 *
 * @receiver modifier chain to extend
 * @param anchorId identifier shared with an overlay request
 * @return a new modifier chain
 */
fun Modifier.overlayAnchor(anchorId: String): Modifier {
    return then(
        OverlayAnchorModifierElement(anchorId),
    )
}
