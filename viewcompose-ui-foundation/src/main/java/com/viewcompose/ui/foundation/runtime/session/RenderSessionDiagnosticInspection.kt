package com.viewcompose.ui.foundation

import java.lang.ref.WeakReference

/**
 * Q3 request-only access to one render session's current safe diagnostic summary.
 *
 * [snapshot] runs on the owning platform render thread and reads only state already retained by
 * [RenderSession]. It installs no sink, listener, history, timer, or recurring frame work. The
 * returned values omit application keys, exception messages, causes, stacks, node content, and
 * every native object. A tooling implementation may retain this handle, but the handle owns the
 * session weakly and returns an ended snapshot after its owner is released.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionInspectionToolingSample
 */
interface RenderSessionDiagnosticInspection {
    /**
     * Returns the current bounded, privacy-safe session summary.
     *
     * @return one immutable snapshot correlated to the registration's logical session
     */
    fun snapshot(): RenderSessionDiagnosticSnapshot
}

/**
 * Current request-time diagnostic state for one logical render session.
 *
 * This Q2 value is process-local. [sessionId] and [parentSessionId] must not be persisted or used as
 * application identity. [latestFrame] is the most recently completed synchronous attempt;
 * [latestFailure] may instead describe a later asynchronous or disposal failure. `null` means no
 * matching result has been observed, not that the session succeeded.
 *
 * @property sessionId process-local logical owner
 * @property parentSessionId process-local parent owner, if any
 * @property role semantic lifetime category of the owner
 * @property renderingActive whether scheduled invalidation rendering is enabled
 * @property committedFrameId most recent committed native frame, or `null` before one commits
 * @property latestFrame most recently completed synchronous frame attempt
 * @property latestFailure most recently observed safe failure summary
 * @property ended whether the session is terminal or its weak owner has been released
 */
data class RenderSessionDiagnosticSnapshot(
    val sessionId: RenderSessionTraceId,
    val parentSessionId: RenderSessionTraceId?,
    val role: RenderSessionRole,
    val renderingActive: Boolean,
    val committedFrameId: Long?,
    val latestFrame: RenderSessionInspectedFrame?,
    val latestFailure: RenderSessionInspectedFailure?,
    val ended: Boolean,
)

/**
 * Q2 bounded safe summary of one completed synchronous frame.
 *
 * @property frameId session-local frame identity
 * @property status whether the candidate committed or rolled back
 * @property failures first retained failure summaries in reporting order
 * @property droppedFailures failures omitted after the inspection cap
 */
data class RenderSessionInspectedFrame(
    val frameId: Long,
    val status: RenderFrameStatus,
    val failures: List<RenderSessionInspectedFailure>,
    val droppedFailures: Int,
)

/**
 * Q2 privacy-safe failure summary for request-driven inspection.
 *
 * The original `Throwable`, message, cause, stack, application key, and node content are absent.
 * [exceptionType] is only the binary class name, capped at 256 characters, and is not a stable
 * fingerprint.
 *
 * @property frameId synchronous frame identity when attribution is proven
 * @property phase pipeline stage that failed
 * @property recovery framework state after handling the failure
 * @property operation Android View operation when known
 * @property exceptionType binary exception class name without message or stack
 */
data class RenderSessionInspectedFailure(
    val frameId: Long?,
    val phase: RenderFailurePhase,
    val recovery: RenderFailureRecovery,
    val operation: RenderFailureOperation?,
    val exceptionType: String,
)

internal class DefaultRenderSessionDiagnosticInspection(
    owner: RenderSession,
    private val context: RenderDiagnosticContext,
) : RenderSessionDiagnosticInspection {
    private val ownerReference = WeakReference(owner)

    override fun snapshot(): RenderSessionDiagnosticSnapshot {
        return ownerReference.get()?.diagnosticInspectionSnapshot()
            ?: RenderSessionDiagnosticSnapshot(
                sessionId = context.sessionId,
                parentSessionId = context.parentSessionId,
                role = context.role,
                renderingActive = false,
                committedFrameId = null,
                latestFrame = null,
                latestFailure = null,
                ended = true,
            )
    }
}

internal const val MAX_INSPECTED_FRAME_FAILURES: Int = 16
internal const val MAX_INSPECTED_EXCEPTION_TYPE_LENGTH: Int = 256

internal fun RenderFailure.toInspectionSummary(): RenderSessionInspectedFailure {
    return RenderSessionInspectedFailure(
        frameId = frameId,
        phase = phase,
        recovery = recovery,
        operation = operation,
        exceptionType = cause.javaClass.name.take(MAX_INSPECTED_EXCEPTION_TYPE_LENGTH),
    )
}
