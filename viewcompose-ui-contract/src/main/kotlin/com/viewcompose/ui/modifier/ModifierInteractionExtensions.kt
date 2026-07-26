package com.viewcompose.ui.modifier

import com.viewcompose.ui.focus.FocusPropertiesReceiver
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.focus.FocusState
import com.viewcompose.ui.input.KeyEvent

fun Modifier.clickable(onClick: () -> Unit): Modifier {
    return then(
        ClickableModifierElement(onClick),
    )
}

fun Modifier.focusable(enabled: Boolean = true): Modifier {
    return then(
        FocusableModifierElement(enabled),
    )
}

fun Modifier.focusRequester(requester: FocusRequester): Modifier {
    return then(
        FocusRequesterModifierElement(requester),
    )
}

fun Modifier.focusProperties(
    properties: FocusPropertiesReceiver.() -> Unit,
): Modifier {
    val receiver = FocusPropertiesReceiver().apply(properties)
    return then(
        FocusPropertiesModifierElement(receiver.build()),
    )
}

fun Modifier.focusGroup(enabled: Boolean = true): Modifier {
    return then(
        FocusGroupModifierElement(enabled),
    )
}

fun Modifier.onFocusChanged(
    onFocusChanged: (FocusState) -> Unit,
): Modifier {
    return then(
        OnFocusChangedModifierElement(onFocusChanged),
    )
}

fun Modifier.onPreviewKeyEvent(
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
): Modifier {
    return then(
        PreviewKeyEventModifierElement(onPreviewKeyEvent),
    )
}

fun Modifier.onKeyEvent(
    onKeyEvent: (KeyEvent) -> Boolean,
): Modifier {
    return then(
        KeyEventModifierElement(onKeyEvent),
    )
}

fun Modifier.contentDescription(description: String?): Modifier {
    return semantics {
        contentDescription = description
    }
}

fun Modifier.testTag(tag: String): Modifier {
    return then(
        TestTagModifierElement(tag),
    )
}

fun Modifier.overlayAnchor(anchorId: String): Modifier {
    return then(
        OverlayAnchorModifierElement(anchorId),
    )
}
