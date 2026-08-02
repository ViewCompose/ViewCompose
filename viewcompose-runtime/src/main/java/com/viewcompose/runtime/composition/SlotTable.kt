package com.viewcompose.runtime.composition

/** Owns the root of one lightweight composition-scope tree. */
class SlotTable {
    /**
     * Returns the live root scope owned by this table.
     *
     * The root is created once and disposed with [dispose]. Its constructor and mutable runtime
     * state remain internal even though integrations can use the scope as an opaque identity.
     */
    val root: RecomposeScope = RecomposeScope(
        signature = RootSignature,
        parent = null,
        saveablePath = "root",
    )

    /**
     * Recursively disposes every scope and its remembered values, observations, and effects.
     *
     * Disposal is idempotent. Cleanup continues after callback failures, then propagates the first
     * failure with subsequent failures attached as suppressed exceptions.
     */
    fun dispose() {
        root.disposeRecursively()
    }

    internal object RootSignature {
        override fun toString(): String = "Root"
    }
}
