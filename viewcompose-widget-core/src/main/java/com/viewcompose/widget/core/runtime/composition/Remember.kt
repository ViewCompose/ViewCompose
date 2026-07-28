package com.viewcompose.widget.core

/**
 * 在当前 composition scope 中缓存一个值。
 * Caches one value in the current composition scope.
 */
fun <T> remember(calculation: () -> T): T {
    return remember(*emptyArray(), calculation = calculation)
}

/**
 * 使用 keys 控制缓存失效的 remember。
 * remember whose cached value is invalidated when keys change.
 */
fun <T> remember(
    vararg keys: Any?,
    calculation: () -> T,
): T {
    return ComposerContext.requireCurrentComposer("remember").remember(
        keys = keys.toList(),
        calculation = calculation,
    )
}
