package com.viewcompose.ui.foundation

/**
 * Registers one post-commit side effect for synchronizing the latest composition result to external objects.
 */
fun SideEffect(
    effect: () -> Unit,
) {
    ComposerContext.requireCurrentComposer("SideEffect").sideEffect(effect)
}
