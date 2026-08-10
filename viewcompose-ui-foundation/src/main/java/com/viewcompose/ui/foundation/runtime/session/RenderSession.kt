package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.composition.CompositionSourceCallSite
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.tooling.UiNodeTooling
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns composition, rendering, effects, overlays, and cleanup for one renderer container.
 *
 * A render attempt first prepares composition and a candidate native tree. Failure in either step
 * aborts the attempt and preserves the previous frame. Once the renderer returns a mounted tree,
 * later composition, native-effect, overlay, or diagnostics failures are reported but cannot roll
 * back that native frame. The installed render-session platform determines scheduling and the
 * concrete renderer.
 *
 * Call [dispose] when the container leaves its host lifecycle. A disposed session is terminal.
 *
 * @param container exclusive native root owned by this session
 * @param content declarative root rebuilt for render attempts
 * @param debug enables detailed renderer diagnostics and logging
 * @param debugTag Android log tag used by this session
 * @param overlayHost host that reconciles overlays declared by [content]
 * @param onRenderStats invoked after a committed frame with aggregate renderer counters
 * @param onRenderResult invoked after a committed frame with detailed diagnostics
 * @param onRenderFailure invoked for synchronous, asynchronous, and disposal failures
 */
class RenderSession(
    private val container: RenderContainerHandle,
    private val content: UiTreeBuilder.() -> Unit,
    private val debug: Boolean = false,
    private val debugTag: String = "ViewCompose",
    private val overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    private val onRenderStats: ((RenderStats) -> Unit)? = null,
    private val onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    private val onRenderFailure: ((RenderFailure) -> Unit)? = null,
) {
    private val platform = RenderSessionPlatformProvider.requirePlatform()
    private val sourceTooling = platform.diagnostics.sourceTooling
    private val overlaySessionId = OverlaySessionId("render-session-${nextSessionId.incrementAndGet()}")
    private val focusManager = platform.focusManagerFactory.create(container)
    private var mountedNodes: List<Any> = emptyList()
    private var disposed: Boolean = false
    private var sourceRegistration: RenderSessionSourceRegistration? = null
    private val overlayRequestStore = OverlayRequestStore()
    private var requestRender: (() -> Unit)? = null
    private val nextFrameId = AtomicLong(0)
    @Volatile
    private var committedFrameId: Long? = null

    /** Most recently reported failure, including asynchronous and disposal failures. */
    @Volatile
    var lastRenderFailure: RenderFailure? = null
        private set

    /** Most recently completed synchronous frame report, or `null` before the first render. */
    @Volatile
    var lastFrameReport: RenderFrameReport? = null
        private set

    /**
     * Coroutine scope shared by composition effects, scoped to the RenderSession lifecycle.
     */
    private val compositionCoroutineScope = CompositionCoroutineScopeOwner(
        parentContext = platform.coroutineContext,
        onError = { error ->
            reportFailure(
                frameId = lastCommittedFrameId(),
                phase = RenderFailurePhase.CompositionCoroutine,
                recovery = RenderFailureRecovery.FrameUnchanged,
                error = error,
            )
        },
    )
    private val composer = ComposerLite(
        warningLogger = { message -> platform.diagnostics.warning(debugTag, message) },
        onInvalidated = { requestRender?.invoke() },
        localSnapshotInspector = LocalContext::describeSnapshot,
        sourceCallSiteCollector = {
            UiNodeTooling.captureCallSites().map { source ->
                CompositionSourceCallSite(
                    className = source.className,
                    methodName = source.methodName,
                    fileName = source.fileName,
                    lineNumber = source.lineNumber,
                )
            }
        },
    )
    private val runtime = platform.runtimeFactory
        .create(
            onRenderNow = ::renderNow,
            onDisposeNow = ::disposeNow,
        ).also { installedRuntime ->
            requestRender = installedRuntime::requestRender
        }

    /**
     * Requests rendering according to the installed runtime policy.
     *
     * The runtime may render synchronously or coalesce the request with a scheduled frame.
     */
    fun render() {
        runtime.render()
    }

    /**
     * Enables or suspends frame-driven rendering for a retained surface.
     *
     * Pending invalidations are retained while inactive and coalesced when reactivated. Explicit
     * [render] calls remain subject to the runtime's documented behavior.
     */
    fun setRenderingActive(active: Boolean) {
        runtime.setRenderingActive(active)
        sourceRegistration?.let { registration ->
            runSourceToolingOperation("update source session") {
                registration.setRenderingActive(active)
            }
        }
    }

    /**
     * Disposes the mounted tree, overlays, composition, coroutine effects, and scheduling runtime.
     *
     * Cleanup is idempotent. Failures are reported individually and do not prevent later cleanup
     * steps from running.
     */
    fun dispose() {
        runtime.dispose()
    }

    /**
     * Renders one synchronous frame; failures attempt to roll back the composition attempt and record recovery state.
     */
    private fun renderNow() {
        if (disposed) return
        val frameId = nextFrameId.incrementAndGet()
        val frameFailures = mutableListOf<RenderFailure>()
        var failurePhase = RenderFailurePhase.CompositionPrepare
        var preparedComposition:
            ComposerLite.PreparedComposition<List<com.viewcompose.ui.node.VNode>>? = null
        var tree: List<com.viewcompose.ui.node.VNode> = emptyList()
        var capturedSourceCandidates =
            emptyList<List<com.viewcompose.ui.tooling.UiSourceCallSite>>()
        val collectDiagnostics = debug || onRenderStats != null || onRenderResult != null
        val frame = try {
            if (!composer.hasPendingInvalidations()) {
                // External render requests (e.g. lazy/pager sessionUpdater) must recompose root even without runtime state invalidation signals.
                composer.requestRootRecompose()
            }
            platform.diagnostics.trace("VC.Compose") {
                FocusManagerContext.withFocusManager(focusManager) {
                    LocalContext.provide(LocalOverlayHost.holder, overlayHost) {
                        OverlayRequestContext.withStore(overlayRequestStore) {
                            ComposerContext.withComposer(
                                composer = composer,
                                coroutineContext = compositionCoroutineScope.coroutineContext,
                            ) {
                                preparedComposition = composer.prepareRoot(
                                    collectDiagnostics = collectDiagnostics,
                                ) {
                                    if (
                                        sourceRegistration == null &&
                                        shouldCaptureSource()
                                    ) {
                                        UiNodeTooling.withSourceCandidateCapture(
                                            onSourceCandidatesCaptured = { candidates ->
                                                capturedSourceCandidates = candidates
                                            },
                                        ) {
                                            buildVNodeTree(content)
                                        }
                                    } else {
                                        buildVNodeTree(content)
                                    }
                                }
                                tree = checkNotNull(preparedComposition).value
                            }
                        }
                    }
                }
            }
            failurePhase = RenderFailurePhase.ViewTreeRender
            platform.diagnostics.trace("VC.RenderTree") {
                platform.renderEngine.renderInto(
                    container = container,
                    previousMountedNodes = mountedNodes,
                    nodes = tree,
                    collectDiagnostics = collectDiagnostics,
                )
            }
        } catch (error: Exception) {
            try {
                preparedComposition?.abort()
            } catch (abortError: Throwable) {
                error.addSuppressed(abortError)
            }
            reportFailure(
                frameId = frameId,
                phase = failurePhase,
                recovery = RenderFailureRecovery.PreviousFrameRestored,
                error = error,
                frameFailures = frameFailures,
            )
            lastFrameReport = RenderFrameReport(
                frameId = frameId,
                status = RenderFrameStatus.RolledBack,
                failures = frameFailures.toList(),
            )
            return
        }

        // Once the renderer has produced the mounted tree, later failures report FrameCommitted and do not roll back the native tree.
        mountedNodes = frame.mountedNodes
        frame.commitFailures.forEach { failure ->
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.ViewTreeCommit,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = failure.cause,
                operation = failure.operation,
                nodeKey = failure.nodeKey,
                frameFailures = frameFailures,
            )
        }
        committedFrameId = frameId
        if (sourceRegistration == null && capturedSourceCandidates.isNotEmpty()) {
            runSourceToolingOperation("register source session") {
                sourceRegistration = sourceTooling?.register(
                    container = container,
                    sourceCandidates = capturedSourceCandidates,
                )
            }
        }
        try {
            checkNotNull(preparedComposition).commit()
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.CompositionCommit,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = error,
                frameFailures = frameFailures,
            )
        }
        try {
            composer.commitSideEffects()
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.CompositionSideEffect,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = error,
                frameFailures = frameFailures,
            )
        }
        frame.commitEffects.forEach { effect ->
            try {
                effect.commit()
            } catch (error: Exception) {
                reportFailure(
                    frameId = frameId,
                    phase = RenderFailurePhase.NativeViewCommit,
                    recovery = RenderFailureRecovery.FrameCommitted,
                    error = error,
                    operation = effect.operation,
                    nodeKey = effect.nodeKey,
                    frameFailures = frameFailures,
                )
            }
        }
        try {
            overlayHost.commit(
                sessionId = overlaySessionId,
                requests = overlayRequestStore.currentRequests(),
            )
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.OverlayCommit,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = error,
                frameFailures = frameFailures,
            )
        }
        if (debug && frame.renderResult == null) {
            platform.diagnostics.debug(debugTag, "Rendered ${tree.size} root nodes")
        }
        invokeDiagnosticsCallback(
            frameId = frameId,
            frameFailures = frameFailures,
        ) {
            onRenderStats?.invoke(frame.renderStats)
        }
        frame.renderResult?.let { result ->
            invokeDiagnosticsCallback(
                frameId = frameId,
                frameFailures = frameFailures,
            ) {
                onRenderResult?.invoke(
                    result.copy(
                        composition = checkNotNull(preparedComposition).diagnostics,
                    ),
                )
            }
        }
        lastFrameReport = RenderFrameReport(
            frameId = frameId,
            status = RenderFrameStatus.Committed,
            failures = frameFailures.toList(),
        )
    }

    /**
     * Disposes the session synchronously, attempting every cleanup step and reporting failures independently.
     */
    private fun disposeNow() {
        if (disposed) return
        disposed = true
        requestRender = null
        disposeOperation {
            compositionCoroutineScope.cancel()
        }
        val disposeFailures = try {
            platform.renderEngine.disposeMounted(
                container = container,
                mountedNodes = mountedNodes,
            )
        } catch (error: Exception) {
            reportFailure(
                frameId = null,
                phase = RenderFailurePhase.SessionDispose,
                recovery = RenderFailureRecovery.SessionDisposed,
                error = error,
            )
            emptyList()
        }
        disposeFailures.forEach { failure ->
            reportFailure(
                frameId = null,
                phase = RenderFailurePhase.SessionDispose,
                recovery = RenderFailureRecovery.SessionDisposed,
                error = failure.cause,
                operation = failure.operation,
                nodeKey = failure.nodeKey,
            )
        }
        mountedNodes = emptyList()
        disposeOperation {
            overlayHost.clear(overlaySessionId)
        }
        disposeOperation {
            composer.dispose()
        }
        sourceRegistration?.let { registration ->
            disposeOperation { registration.dispose() }
            sourceRegistration = null
        }
    }

    private inline fun runSourceToolingOperation(
        operation: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (error: Exception) {
            platform.diagnostics.error(
                debugTag,
                "Render-session tooling could not $operation.",
                error,
            )
        }
    }

    private fun shouldCaptureSource(): Boolean {
        return try {
            sourceTooling?.shouldCapture(container) == true
        } catch (error: Exception) {
            platform.diagnostics.error(
                debugTag,
                "Render-session tooling could not evaluate source capture.",
                error,
            )
            false
        }
    }

    /**
     * Invokes a diagnostics callback and folds callback failures into the current frame report.
     */
    private inline fun invokeDiagnosticsCallback(
        frameId: Long,
        frameFailures: MutableList<RenderFailure>,
        callback: () -> Unit,
    ) {
        try {
            callback()
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.DiagnosticsCallback,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = error,
                frameFailures = frameFailures,
            )
        }
    }

    /**
     * Runs one dispose operation; failure does not stop remaining cleanup steps.
     */
    private inline fun disposeOperation(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            reportFailure(
                frameId = null,
                phase = RenderFailurePhase.SessionDispose,
                recovery = RenderFailureRecovery.SessionDisposed,
                error = error,
            )
        }
    }

    /**
     * Records a failure, updates the latest failure state, and notifies the listener.
     */
    private fun reportFailure(
        frameId: Long?,
        phase: RenderFailurePhase,
        recovery: RenderFailureRecovery,
        error: Throwable,
        operation: RenderFailureOperation? = null,
        nodeKey: Any? = null,
        frameFailures: MutableList<RenderFailure>? = null,
    ) {
        val nativeFailure = error.findAndroidViewOperationFailure()
        val failure = RenderFailure(
            frameId = frameId,
            phase = phase,
            recovery = recovery,
            cause = error,
            operation = operation ?: nativeFailure?.operation?.toRenderFailureOperation(),
            nodeKey = nodeKey ?: nativeFailure?.nodeKey,
        )
        frameFailures?.add(failure)
        lastRenderFailure = failure
        platform.diagnostics.error(
            debugTag,
            "Render failure phase=$phase recovery=$recovery frameId=$frameId " +
                "operation=${failure.operation} nodeKey=${failure.nodeKey}",
            error,
        )
        try {
            onRenderFailure?.invoke(failure)
        } catch (listenerError: Exception) {
            platform.diagnostics.error(debugTag, "Render failure callback failed", listenerError)
        }
    }

    private fun Throwable.findAndroidViewOperationFailure(): AndroidViewOperationException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is AndroidViewOperationException) return current
            current = current.cause
        }
        return null
    }

    private fun AndroidViewOperation.toRenderFailureOperation(): RenderFailureOperation {
        return when (this) {
            AndroidViewOperation.Factory -> RenderFailureOperation.AndroidViewFactory
            AndroidViewOperation.Update -> RenderFailureOperation.AndroidViewUpdate
            AndroidViewOperation.Reset -> RenderFailureOperation.AndroidViewReset
            AndroidViewOperation.Commit -> RenderFailureOperation.AndroidViewCommit
            AndroidViewOperation.Release -> RenderFailureOperation.AndroidViewRelease
        }
    }

    private fun lastCommittedFrameId(): Long? {
        return committedFrameId
    }

    private companion object {
        val nextSessionId = AtomicLong(0)
    }
}
