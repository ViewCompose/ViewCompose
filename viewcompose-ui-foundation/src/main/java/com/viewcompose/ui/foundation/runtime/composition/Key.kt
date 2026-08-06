package com.viewcompose.ui.foundation

/**
 * Declares stable identity for a composition subtree so state follows business keys instead of call-order reuse.
 */
fun <T> key(
    vararg keys: Any?,
    block: () -> T,
): T {
    return ComposerContext.requireCurrentComposer("key").withKeys(
        keys = keys.toList(),
        block = block,
    )
}
