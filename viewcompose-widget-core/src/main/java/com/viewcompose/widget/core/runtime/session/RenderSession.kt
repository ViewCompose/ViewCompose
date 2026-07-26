package com.viewcompose.widget.core

import android.util.Log
import android.view.ViewGroup
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import java.util.concurrent.atomic.AtomicLong

class RenderSession(
    private val container: ViewGroup,
    private val content: UiTreeBuilder.() -> Unit,
    private val debug: Boolean = false,
    private val debugTag: String = "ViewCompose",
    private val overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    private val onRenderStats: ((RenderStats) -> Unit)? = null,
    private val onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    private val onRenderFailure: ((RenderFailure) -> Unit)? = null,
) {
    private val platform = RenderSessionPlatformProvider.requirePlatform()
    private val overlaySessionId = OverlaySessionId("render-session-${nextSessionId.incrementAndGet()}")
    private val focusManager = SessionFocusManager(container)
    private var mountedNodes: List<Any> = emptyList()
    private var disposed: Boolean = false
    private val overlayRequestStore = OverlayRequestStore()
    private var requestRender: (() -> Unit)? = null
    private val nextFrameId = AtomicLong(0)
    @Volatile
    private var committedFrameId: Long? = null

    @Volatile
    var lastRenderFailure: RenderFailure? = null
        private set

    @Volatile
    var lastFrameReport: RenderFrameReport? = null
        private set

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
        warningLogger = { message -> Log.w(debugTag, message) },
        onInvalidated = { requestRender?.invoke() },
    )
    private val runtime = platform.runtimeFactory
        .create(
            onRenderNow = ::renderNow,
            onDisposeNow = ::disposeNow,
        ).also { installedRuntime ->
            requestRender = installedRuntime::requestRender
        }

    fun render() {
        runtime.render()
    }

    fun dispose() {
        runtime.dispose()
    }

    private fun renderNow() {
        if (disposed) return
        val frameId = nextFrameId.incrementAndGet()
        val frameFailures = mutableListOf<RenderFailure>()
        var failurePhase = RenderFailurePhase.CompositionPrepare
        var preparedComposition:
            ComposerLite.PreparedComposition<List<com.viewcompose.ui.node.VNode>>? = null
        var tree: List<com.viewcompose.ui.node.VNode> = emptyList()
        val frame = try {
            if (!composer.hasPendingInvalidations()) {
                // External render requests (e.g. lazy/pager sessionUpdater) must recompose root even
                // without runtime state invalidation signals.
                composer.requestRootRecompose()
            }
            FocusManagerContext.withFocusManager(focusManager) {
                LocalContext.provide(LocalOverlayHost.holder, overlayHost) {
                    OverlayRequestContext.withStore(overlayRequestStore) {
                        ComposerContext.withComposer(
                            composer = composer,
                            coroutineContext = compositionCoroutineScope.coroutineContext,
                        ) {
                            preparedComposition = composer.prepareRoot {
                                buildVNodeTree(content)
                            }
                            tree = checkNotNull(preparedComposition).value
                        }
                    }
                }
            }
            failurePhase = RenderFailurePhase.ViewTreeRender
            platform.renderEngine.renderInto(
                container = container,
                previousMountedNodes = mountedNodes,
                nodes = tree,
                collectDiagnostics = debug || onRenderStats != null || onRenderResult != null,
            )
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
            Log.d(debugTag, "Rendered ${tree.size} root nodes")
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
                onRenderResult?.invoke(result)
            }
        }
        lastFrameReport = RenderFrameReport(
            frameId = frameId,
            status = RenderFrameStatus.Committed,
            failures = frameFailures.toList(),
        )
    }

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
    }

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
        Log.e(
            debugTag,
            "Render failure phase=$phase recovery=$recovery frameId=$frameId " +
                "operation=${failure.operation} nodeKey=${failure.nodeKey}",
            error,
        )
        try {
            onRenderFailure?.invoke(failure)
        } catch (listenerError: Exception) {
            Log.e(debugTag, "Render failure callback failed", listenerError)
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
