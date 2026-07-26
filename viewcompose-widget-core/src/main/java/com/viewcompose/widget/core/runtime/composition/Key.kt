package com.viewcompose.widget.core

fun <T> key(
    vararg keys: Any?,
    block: () -> T,
): T {
    return ComposerContext.requireCurrentComposer("key").withKeys(
        keys = keys.toList(),
        block = block,
    )
}
