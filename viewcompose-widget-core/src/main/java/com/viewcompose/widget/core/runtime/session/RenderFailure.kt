package com.viewcompose.widget.core

/**
 * The stage at which a render-session operation failed.
 */
enum class RenderFailurePhase {
    CompositionPrepare,
    ViewTreeRender,
    ViewTreeCommit,
    CompositionCommit,
    CompositionSideEffect,
    NativeViewCommit,
    OverlayCommit,
    DiagnosticsCallback,
    CompositionCoroutine,
    SessionDispose,
}

/**
 * The state guaranteed by the framework after a failure has been handled.
 */
enum class RenderFailureRecovery {
    PreviousFrameRestored,
    FrameCommitted,
    FrameUnchanged,
    SessionDisposed,
}

/**
 * Optional native interop operation associated with a render failure.
 */
enum class RenderFailureOperation {
    AndroidViewFactory,
    AndroidViewUpdate,
    AndroidViewReset,
    AndroidViewCommit,
    AndroidViewRelease,
}

data class RenderFailure(
    val frameId: Long?,
    val phase: RenderFailurePhase,
    val recovery: RenderFailureRecovery,
    val cause: Throwable,
    val operation: RenderFailureOperation? = null,
    val nodeKey: Any? = null,
)

enum class RenderFrameStatus {
    Committed,
    RolledBack,
}

/**
 * The latest synchronous frame result. Asynchronous coroutine failures are delivered through
 * [RenderFailure] callbacks and do not rewrite an already completed frame report.
 */
data class RenderFrameReport(
    val frameId: Long,
    val status: RenderFrameStatus,
    val failures: List<RenderFailure> = emptyList(),
)
