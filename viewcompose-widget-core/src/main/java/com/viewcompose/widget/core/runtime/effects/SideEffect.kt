package com.viewcompose.widget.core

/**
 * 注册一次提交后副作用，用于把最新 composition 结果同步到外部对象。
 * Registers one post-commit side effect for synchronizing the latest composition result to external objects.
 */
fun SideEffect(
    effect: () -> Unit,
) {
    ComposerContext.requireCurrentComposer("SideEffect").sideEffect(effect)
}
