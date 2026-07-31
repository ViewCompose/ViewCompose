package com.viewcompose.widget.core

import android.os.Trace
import android.util.Log
import android.view.ViewGroup
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.composition.CompositionSourceCallSite
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.tooling.UiNodeTooling
import java.util.concurrent.atomic.AtomicLong

/**
 * 一个绑定到 ViewGroup 的 ViewCompose 渲染会话。
 * ViewCompose render session bound to one ViewGroup.
 *
 * session 负责组合 VNode、交给 renderer 更新 mounted tree、提交 composition/effect/overlay，
 * 并在每个阶段记录可恢复 failure。
 * The session composes VNodes, asks the renderer to update the mounted tree, commits
 * composition/effects/overlays, and reports recoverable failures for each phase.
 */
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

    /**
     * composition effect 共享的协程作用域，生命周期跟随 RenderSession。
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
        warningLogger = { message -> Log.w(debugTag, message) },
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
     * 按 runtime 策略请求渲染。
     * Requests rendering according to the runtime policy.
     */
    fun render() {
        runtime.render()
    }

    /**
     * 控制帧驱动渲染是否激活。
     * Controls whether frame-driven rendering is active.
     */
    fun setRenderingActive(active: Boolean) {
        runtime.setRenderingActive(active)
    }

    /**
     * 释放 session 及其 mounted tree、overlay、composer 和 effect scope。
     * Disposes the session, mounted tree, overlays, composer, and effect scope.
     */
    fun dispose() {
        runtime.dispose()
    }

    /**
     * 同步渲染一帧；任何阶段失败都会尽量回滚 composition attempt 并记录恢复状态。
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
        val collectDiagnostics = debug || onRenderStats != null || onRenderResult != null
        val frame = try {
            if (!composer.hasPendingInvalidations()) {
                // 外部渲染请求（如 lazy/pager sessionUpdater）即使没有 runtime state invalidation，也必须重组根节点。
                // External render requests (e.g. lazy/pager sessionUpdater) must recompose root even without runtime state invalidation signals.
                composer.requestRootRecompose()
            }
            traceSection("VC.Compose") {
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
                                    buildVNodeTree(content)
                                }
                                tree = checkNotNull(preparedComposition).value
                            }
                        }
                    }
                }
            }
            failurePhase = RenderFailurePhase.ViewTreeRender
            traceSection("VC.RenderTree") {
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

        // renderer 已经产生 mounted tree 后，后续失败只能报告 FrameCommitted，不能回滚 native tree。
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
     * 同步释放 session，尽量执行所有清理步骤并分别报告失败。
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
    }

    /**
     * 调用诊断回调并把回调自身失败纳入当前帧报告。
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
     * 执行单个 dispose 操作，失败不会阻断其它清理步骤。
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
     * 统一记录 failure、更新最近失败状态并通知监听者。
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

private inline fun <T> traceSection(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
