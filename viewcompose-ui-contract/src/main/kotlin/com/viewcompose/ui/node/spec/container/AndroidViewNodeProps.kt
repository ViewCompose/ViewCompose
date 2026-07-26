package com.viewcompose.ui.node.spec

data class AndroidViewNodeProps(
    val factory: (Any) -> Any,
    val update: ((Any) -> Unit)?,
    val onReset: ((Any) -> Unit)? = null,
    val onRelease: ((Any) -> Unit)? = null,
    val onCommit: ((Any) -> Unit)? = null,
) : NodeSpec

enum class AndroidViewOperation {
    Factory,
    Update,
    Reset,
    Commit,
    Release,
}

class AndroidViewOperationException(
    val operation: AndroidViewOperation,
    val nodeKey: Any?,
    cause: Throwable,
) : RuntimeException(
    "AndroidView $operation failed for key=$nodeKey",
    cause,
)
