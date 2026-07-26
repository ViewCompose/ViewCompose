package com.viewcompose.widget.core

fun DisposableEffect(
    vararg keys: Any?,
    effect: () -> (() -> Unit),
) {
    ComposerContext.requireCurrentComposer("DisposableEffect").disposableEffect(
        keys = keys.toList(),
        effect = effect,
    )
}
