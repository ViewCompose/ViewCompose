package com.viewcompose.widget.core

/**
 * Caches one value in the current composition scope.
 */
fun <T> remember(calculation: () -> T): T {
    return remember(*emptyArray(), calculation = calculation)
}

/**
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
