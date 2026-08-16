package com.viewcompose.ui.foundation

/** Stage in the render-session pipeline where an operation failed. */
enum class RenderFailurePhase {
    /** Declarative composition could not prepare a candidate tree. */
    CompositionPrepare,
    /** One coalesced observed-property batch could not prepare candidate values or targets. */
    ObservedPropertyPrepare,
    /** The renderer rejected or rolled back an observed-property native patch batch. */
    ObservedPropertyRender,
    /** A rendered observed-property batch could not publish its dependency snapshot. */
    ObservedPropertyCommit,
    /** The renderer could not reconcile the candidate tree. */
    ViewTreeRender,
    /** The renderer reported a failure while establishing its mounted tree. */
    ViewTreeCommit,
    /** The prepared composition could not commit. */
    CompositionCommit,
    /** A composition side effect threw after commit. */
    CompositionSideEffect,
    /** A deferred native-view mutation threw after commit. */
    NativeViewCommit,
    /** Declarative overlay requests could not be committed. */
    OverlayCommit,
    /** A diagnostics listener threw while receiving a committed frame. */
    DiagnosticsCallback,
    /** A composition-scoped coroutine failed. */
    CompositionCoroutine,
    /** A cleanup operation failed while disposing the session. */
    SessionDispose,
}

/** State guaranteed by the framework after a render failure has been handled. */
enum class RenderFailureRecovery {
    /** The candidate composition was aborted and the previous mounted frame remains active. */
    PreviousFrameRestored,
    /** The native frame is committed and later pipeline failures cannot roll it back. */
    FrameCommitted,
    /** No synchronous frame changed; typically used for an asynchronous coroutine failure. */
    FrameUnchanged,
    /** Cleanup continued and the session is terminal despite one or more failures. */
    SessionDisposed,
}

/** Native Android-view interoperability operation associated with a failure. */
enum class RenderFailureOperation {
    /** Creating an Android view from its factory. */
    AndroidViewFactory,
    /** Applying a normal Android-view update. */
    AndroidViewUpdate,
    /** Resetting a reusable Android view. */
    AndroidViewReset,
    /** Committing a deferred Android-view mutation. */
    AndroidViewCommit,
    /** Releasing an Android view removed from the mounted tree. */
    AndroidViewRelease,
}

/**
 * Structured record for one render-session failure.
 *
 * @property frameId synchronous frame identity, or `null` for session-level cleanup
 * @property phase pipeline stage that failed
 * @property recovery state guaranteed after handling the failure
 * @property cause original exception
 * @property operation native interoperability operation, if known
 * @property nodeKey declarative identity of the affected node, if known
 */
data class RenderFailure(
    val frameId: Long?,
    val phase: RenderFailurePhase,
    val recovery: RenderFailureRecovery,
    val cause: Throwable,
    val operation: RenderFailureOperation? = null,
    val nodeKey: Any? = null,
)

/** Final status of one synchronous render attempt. */
enum class RenderFrameStatus {
    /** The candidate native tree became the active frame. */
    Committed,
    /** Preparation or tree rendering failed and the previous frame stayed active. */
    RolledBack,
}

/**
 * Result of one completed synchronous render attempt.
 *
 * Asynchronous coroutine failures are delivered through [RenderFailure] callbacks and do not
 * rewrite an already completed frame report.
 *
 * @property frameId monotonically increasing identity within one [RenderSession]
 * @property status whether the candidate frame committed or rolled back
 * @property failures failures encountered during this synchronous attempt in reporting order
 */
data class RenderFrameReport(
    val frameId: Long,
    val status: RenderFrameStatus,
    val failures: List<RenderFailure> = emptyList(),
)
