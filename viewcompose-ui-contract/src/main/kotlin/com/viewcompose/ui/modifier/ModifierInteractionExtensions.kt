package com.viewcompose.ui.modifier

fun Modifier.clickable(onClick: () -> Unit): Modifier {
    return then(
        ClickableModifierElement(onClick),
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
