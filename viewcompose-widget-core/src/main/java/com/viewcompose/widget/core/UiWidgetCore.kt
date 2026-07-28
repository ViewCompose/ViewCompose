package com.viewcompose.widget.core

/**
 * widget-core 层标记，用于声明式核心 widget 能力和依赖链识别。
 * Widget layer marker for declarative core widget capabilities and dependency-chain identification.
 */
object UiWidgetCore {
    /**
     * 当前 widget-core 层所需的底层依赖链。
     * Lower-level dependency chain required by the current widget-core layer.
     */
    val dependencyChain: List<String> = listOf(
        "viewcompose-runtime",
        "viewcompose-ui-contract",
        "viewcompose-widget-core",
    )
}
