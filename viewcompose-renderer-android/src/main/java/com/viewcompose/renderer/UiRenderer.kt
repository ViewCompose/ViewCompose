package com.viewcompose.renderer

import com.viewcompose.runtime.UiRuntime

/**
 * Module entry marker describing the dependency chain from VNodes through reconciliation to mounting.
 * Renderer module entry marker declaring the dependency chain for vnode, reconciler, and mount APIs.
 */
object UiRenderer {
    /**
     * Module dependency chain exposed to runtime diagnostics.
     * Module dependency chain used by runtime diagnostics.
     */
    val dependencyChain: List<String> = listOf(
        UiRuntime.NAME,
        "ui-renderer",
    )
}
