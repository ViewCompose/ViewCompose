package com.viewcompose.runtime.composition

class SlotTable {
    val root: RecomposeScope = RecomposeScope(
        signature = RootSignature,
        parent = null,
        saveablePath = "root",
    )

    fun dispose() {
        root.disposeRecursively()
    }

    internal object RootSignature
}
