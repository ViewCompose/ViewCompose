package com.viewcompose.renderer

import com.viewcompose.runtime.UiRuntime

/**
 * renderer 模块入口标记，声明 vnode、reconciler 与 mount API 的依赖链。
 * Renderer module entry marker declaring the dependency chain for vnode, reconciler, and mount APIs.
 */
object UiRenderer {
    /**
     * 运行时诊断使用的模块依赖链。
     * Module dependency chain used by runtime diagnostics.
     */
    val dependencyChain: List<String> = listOf(
        UiRuntime.NAME,
        "ui-renderer",
    )
}
