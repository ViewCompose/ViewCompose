package com.viewcompose.widget.core

/**
 * Widget layer marker for declarative core widget capabilities and dependency-chain identification.
 */
object UiWidgetCore {
    /**
     * Lower-level dependency chain required by the current widget-core layer.
     */
    val dependencyChain: List<String> = listOf(
        "viewcompose-runtime",
        "viewcompose-ui-contract",
        "viewcompose-widget-core",
    )
}
