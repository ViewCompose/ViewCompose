package com.viewcompose.ui.node.spec

import com.viewcompose.ui.environment.UiEnvironmentValues

/**
 * Defines the transactional lifecycle of one platform Android view node.
 *
 * [factory], [update], [onReset], and [onCommit] receive the immutable environment captured by the
 * VNode that owns the operation. [update] and [onReset] must be replay-safe and limited to
 * configuring the supplied view because a failed render may rebind the previous node during
 * rollback. Non-replayable external actions belong in [onCommit].
 *
 * Equal [constructionIdentity] values permit physical View reuse for the same logical node. A
 * changed identity requires an atomic candidate replacement. The value must therefore have stable
 * equality and hash behavior for the lifetime of the VNode. [adapterName] is bounded diagnostic
 * metadata and must not contain application state, keys, or View content.
 *
 * @sample com.viewcompose.ui.samples.androidViewNodePropsSample
 * @property factory creates the platform view from an opaque platform context and environment
 * @property update replay-safe binding invoked with the current platform view and environment
 * @property onReset replay-safe reset invoked only before cross-key mounted-tree reuse
 * @property onRelease one-shot cleanup whenever a created view is permanently abandoned, including
 * candidate rollback, committed removal, final reuse-cache eviction, or render-session disposal
 * @property onCommit action deferred until the containing composition commits
 * @property constructionIdentity opaque, equality-stable constructor-sensitive View identity
 * @property adapterName privacy-safe adapter family label used only for bounded diagnostics
 */
data class AndroidViewNodeProps(
    val factory: (Any, UiEnvironmentValues) -> Any,
    val update: ((Any, UiEnvironmentValues) -> Unit)?,
    val onReset: ((Any, UiEnvironmentValues) -> Unit)? = null,
    val onRelease: ((Any) -> Unit)? = null,
    val onCommit: ((Any, UiEnvironmentValues) -> Unit)? = null,
    val constructionIdentity: Any? = Unit,
    val adapterName: String = "callback",
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
