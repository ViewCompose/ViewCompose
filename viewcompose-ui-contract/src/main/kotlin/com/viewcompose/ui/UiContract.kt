package com.viewcompose.ui

/** Identifies the platform-neutral UI contract artifact for capability and dependency diagnostics. */
object UiContract {
    /**
     * Returns the stable capability names supplied by this artifact.
     *
     * The returned list is immutable and intentionally contains only `ui-contract`; host modules
     * may append it to their own diagnostic dependency chain.
     */
    val dependencyChain: List<String> = listOf("ui-contract")
}
