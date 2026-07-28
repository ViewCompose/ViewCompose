package com.viewcompose.ui.node.spec

/**
 * AndroidView 节点的工厂、更新和生命周期回调属性。
 * Factory, update, and lifecycle-callback properties for an AndroidView node.
 */
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
