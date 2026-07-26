package com.viewcompose.widget.core

fun <T> remember(calculation: () -> T): T {
    return remember(*emptyArray(), calculation = calculation)
}

fun <T> remember(
    vararg keys: Any?,
    calculation: () -> T,
): T {
    return ComposerContext.requireCurrentComposer("remember").remember(
        keys = keys.toList(),
        calculation = calculation,
    )
}
