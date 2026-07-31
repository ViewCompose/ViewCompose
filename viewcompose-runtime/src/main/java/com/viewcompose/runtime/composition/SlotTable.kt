package com.viewcompose.runtime.composition

/**
 * 轻量 slot table，持有 composition scope 树的根节点。
 * Lightweight slot table that owns the root of the composition scope tree.
 */
class SlotTable {
    val root: RecomposeScope = RecomposeScope(
        signature = RootSignature,
        parent = null,
        saveablePath = "root",
    )

    /**
     * 递归释放整棵 scope 树及其 remember/effect 资源。
     * Recursively disposes the whole scope tree and its remember/effect resources.
     */
    fun dispose() {
        root.disposeRecursively()
    }

    internal object RootSignature {
        override fun toString(): String = "Root"
    }
}
