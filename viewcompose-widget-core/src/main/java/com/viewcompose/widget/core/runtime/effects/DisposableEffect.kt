package com.viewcompose.widget.core

/**
 * 在提交后启动需要清理的副作用，keys 变化或离开 composition 时执行清理。
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
