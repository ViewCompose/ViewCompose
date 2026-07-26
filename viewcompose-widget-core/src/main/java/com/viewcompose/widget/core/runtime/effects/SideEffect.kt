package com.viewcompose.widget.core

fun SideEffect(
    effect: () -> Unit,
) {
    ComposerContext.requireCurrentComposer("SideEffect").sideEffect(effect)
}
