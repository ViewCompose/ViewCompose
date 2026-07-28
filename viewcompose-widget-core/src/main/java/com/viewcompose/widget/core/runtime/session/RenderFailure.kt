package com.viewcompose.widget.core

/**
 * render-session 操作失败时所处的阶段。
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
 * failure 处理后框架保证处于的状态。
 * The state guaranteed by the framework after a failure has been handled.
 */
enum class RenderFailureRecovery {
    PreviousFrameRestored,
    FrameCommitted,
    FrameUnchanged,
    SessionDisposed,
}

/**
 * 与 render failure 关联的可选 native interop 操作。
 * Optional native interop operation associated with a render failure.
 */
enum class RenderFailureOperation {
    AndroidViewFactory,
    AndroidViewUpdate,
    AndroidViewReset,
    AndroidViewCommit,
    AndroidViewRelease,
}

/**
 * 一次渲染失败的结构化记录。
 * Structured record for one render failure.
 */
data class RenderFailure(
    val frameId: Long?,
    val phase: RenderFailurePhase,
    val recovery: RenderFailureRecovery,
    val cause: Throwable,
    val operation: RenderFailureOperation? = null,
    val nodeKey: Any? = null,
)

/**
 * 同步帧最终状态。
 * Final status of one synchronous frame.
 */
enum class RenderFrameStatus {
    Committed,
    RolledBack,
}

/**
 * 最近一次同步帧结果。
 * The latest synchronous frame result.
 *
 * 异步 coroutine failure 会通过 [RenderFailure] 回调传递，不会重写已完成的 frame report。
 * Asynchronous coroutine failures are delivered through [RenderFailure] callbacks and do not
 * rewrite an already completed frame report.
 */
data class RenderFrameReport(
    val frameId: Long,
    val status: RenderFrameStatus,
    val failures: List<RenderFailure> = emptyList(),
)
