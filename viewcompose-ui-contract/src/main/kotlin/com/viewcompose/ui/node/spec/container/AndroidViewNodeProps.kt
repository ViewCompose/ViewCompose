package com.viewcompose.ui.node.spec

data class AndroidViewNodeProps(
    val factory: (Any) -> Any,
    val update: ((Any) -> Unit)?,
    val onReset: ((Any) -> Unit)? = null,
    val onRelease: ((Any) -> Unit)? = null,
) : NodeSpec
