package com.viewcompose.ui

/**
 * UI contract 模块标记，承载平台无关的声明式节点、修饰符和状态契约。
 * UI contract marker for platform-neutral declarative nodes, modifiers, and state contracts.
 */
object UiContract {
    /**
     * 当前契约层的依赖链，用于宿主模块暴露能力或做依赖守卫。
     * Dependency chain for this contract layer, used by hosts for capability exposure or dependency guards.
     */
    val dependencyChain: List<String> = listOf("ui-contract")
}
