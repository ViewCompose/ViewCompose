package com.viewcompose.widget.core

/**
 * 为组合子树声明稳定身份，使同一位置的状态可按业务 key 迁移而不是按调用顺序误复用。
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
