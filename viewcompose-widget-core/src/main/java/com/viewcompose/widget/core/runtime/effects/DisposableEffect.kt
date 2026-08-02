package com.viewcompose.widget.core

/**
 * Starts a cleanup-aware effect after commit; cleanup runs when keys change or the call leaves composition.
 */
fun DisposableEffect(
    vararg keys: Any?,
    effect: () -> (() -> Unit),
) {
    ComposerContext.requireCurrentComposer("DisposableEffect").disposableEffect(
        keys = keys.toList(),
        effect = effect,
    )
}
