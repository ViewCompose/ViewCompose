package com.viewcompose.ui.node.spec

/**
 * Defines the transactional lifecycle of one platform Android view node.
 *
 * [update] and [onReset] must be replay-safe and limited to configuring the supplied view because
 * a failed render may rebind the previous node during rollback. Non-replayable external actions
 * belong in [onCommit].
 *
 * @property factory creates the platform view from an opaque platform context
 * @property update replay-safe binding invoked with the current platform view
 * @property onReset replay-safe reset invoked before a retained view is rebound for another node
 * @property onRelease one-shot cleanup whenever a created view is permanently abandoned, including
 * candidate rollback, committed removal, final reuse-cache eviction, or render-session disposal
 * @property onCommit action deferred until the complete view-tree transaction commits
 */
data class AndroidViewNodeProps(
    val factory: (Any) -> Any,
    val update: ((Any) -> Unit)?,
    val onReset: ((Any) -> Unit)? = null,
    val onRelease: ((Any) -> Unit)? = null,
    val onCommit: ((Any) -> Unit)? = null,
) : NodeSpec

/** Lifecycle operation reported when an Android view callback fails. */
enum class AndroidViewOperation {
    Factory,
    Update,
    Reset,
    Commit,
    Release,
}

/**
 * Wraps a failure from an Android view lifecycle callback with operation and node identity.
 *
 * @property operation callback phase that failed
 * @property nodeKey semantic key of the affected node, or `null` when it has no key
 * @param cause original callback failure
 */
class AndroidViewOperationException(
    val operation: AndroidViewOperation,
    val nodeKey: Any?,
    cause: Throwable,
) : RuntimeException(
    "AndroidView $operation failed for key=$nodeKey",
    cause,
)
