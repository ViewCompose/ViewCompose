package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.composition.CompositionSourceCallSite
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.ReusableItemPresentation
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
    private var awaitingActivation: Boolean = false
    private var preparedFrame: PreparedRenderFrame? = null
    @Volatile
    private var committedFrameId: Long? = null
    private var disposalMode: DisposalMode = DisposalMode.Release
    private var detachedPresentation: CoreReusableItemPresentation? = null
    private var logicalOwnerRelease: (() -> Unit)? = null
    private var adoptedUncommittedTree: Boolean = false

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
        onInvalidated = {
            if (!awaitingActivation) {
                requestRender?.invoke()
            }
        },
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
        synchronousEffectWarningThresholdNanos = if (debug) {
            SlowSynchronousEffectWarningNanos
        } else {
            null
        },
        effectFrameIdProvider = ::lastCommittedFrameId,
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

    /** Builds an initial native tree while retaining the composition commit boundary. */
    internal fun prepareForActivation() {
        if (disposed || committedFrameId != null || preparedFrame != null) return
        awaitingActivation = true
        preparedFrame = prepareFrame()
        if (preparedFrame == null) {
            awaitingActivation = false
        }
    }

    /** Commits a valid prepared tree, or rebuilds when observed state changed before attachment. */
    internal fun activatePrepared() {
        if (disposed) return
        val pending = preparedFrame
        preparedFrame = null
        awaitingActivation = false
        if (pending == null) {
            runtime.render()
            return
        }
        if (composer.hasPendingInvalidations()) {
            abortPreparedFrame(pending)
            runtime.render()
            return
        }
        commitFrame(pending)
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

    /** Disposes composition, invokes [releaseOwner], then releases the mounted native tree. */
    internal fun disposeWithLogicalOwnerRelease(releaseOwner: () -> Unit) {
        if (disposed) {
            releaseOwner()
            return
        }
        logicalOwnerRelease = releaseOwner
        runtime.dispose()
    }

    /** Terminates logical ownership and returns a reset native tree when the renderer permits it. */
    internal fun disposeForReuse(releaseOwner: () -> Unit = {}): ReusableItemPresentation? {
        if (disposed) {
            releaseOwner()
            return null
        }
        disposalMode = DisposalMode.DetachForReuse
        logicalOwnerRelease = releaseOwner
        runtime.dispose()
        return detachedPresentation
    }

    /** Installs a detached physical tree before this session produces its first frame. */
    internal fun adoptReusablePresentation(presentation: ReusableItemPresentation): Boolean {
        if (disposed || mountedNodes.isNotEmpty() || committedFrameId != null || preparedFrame != null) {
            return false
        }
        val reusable = presentation as? CoreReusableItemPresentation ?: return false
        val adopted = reusable.takeTree() ?: return false
        return try {
            val nodes = platform.renderEngine.attachReusableMounted(container, adopted)
            if (nodes.isEmpty()) {
                platform.renderEngine.releaseReusableMounted(adopted)
                false
            } else {
                mountedNodes = nodes
                adoptedUncommittedTree = true
                true
            }
        } catch (error: Exception) {
            platform.renderEngine.releaseReusableMounted(adopted)
            reportFailure(
                frameId = null,
                phase = RenderFailurePhase.SessionDispose,
                recovery = RenderFailureRecovery.SessionDisposed,
                error = error,
            )
            false
        }
    }

    /**
     * Renders one synchronous frame; failures attempt to roll back the composition attempt and record recovery state.
     */
    private fun renderNow() {
        if (disposed) return
        preparedFrame?.let { pending ->
            preparedFrame = null
            awaitingActivation = false
            abortPreparedFrame(pending)
        }
        prepareFrame()?.let(::commitFrame)
    }

    /** Prepares composition and the native tree without crossing the commit boundary. */
    private fun prepareFrame(): PreparedRenderFrame? {
        if (disposed) return null
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
            val recovery = if (adoptedUncommittedTree) {
                discardAdoptedUncommittedTree(error)
                RenderFailureRecovery.FrameUnchanged
            } else {
                RenderFailureRecovery.PreviousFrameRestored
            }
            reportFailure(
                frameId = frameId,
                phase = failurePhase,
                recovery = recovery,
                error = error,
                frameFailures = frameFailures,
            )
            lastFrameReport = RenderFrameReport(
                frameId = frameId,
                status = RenderFrameStatus.RolledBack,
                failures = frameFailures.toList(),
            )
            return null
        }

        mountedNodes = frame.mountedNodes
        adoptedUncommittedTree = false
        return PreparedRenderFrame(
            frameId = frameId,
            frameFailures = frameFailures,
            composition = checkNotNull(preparedComposition),
            tree = tree,
            sourceCandidates = capturedSourceCandidates,
            frame = frame,
        )
    }

    /** Crosses the single composition/native/effect commit boundary for a prepared frame. */
    private fun commitFrame(prepared: PreparedRenderFrame) {
        val frameId = prepared.frameId
        val frameFailures = prepared.frameFailures
        val composition = prepared.composition
        val tree = prepared.tree
        val frame = prepared.frame

        // Once the renderer has produced the mounted tree, later failures report FrameCommitted and do not roll back the native tree.
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
        if (sourceRegistration == null && prepared.sourceCandidates.isNotEmpty()) {
            runSourceToolingOperation("register source session") {
                sourceRegistration = sourceTooling?.register(
                    container = container,
                    sourceCandidates = prepared.sourceCandidates,
                )
            }
        }
        try {
            composition.commit()
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
                        composition = composition.diagnostics,
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

    /** Aborts a speculative composition while retaining its native tree as the next diff input. */
    private fun abortPreparedFrame(prepared: PreparedRenderFrame) {
        try {
            prepared.composition.abort()
        } catch (error: Exception) {
            reportFailure(
                frameId = prepared.frameId,
                phase = RenderFailurePhase.CompositionPrepare,
                recovery = RenderFailureRecovery.PreviousFrameRestored,
                error = error,
            )
        }
    }

    /**
     * Disposes the session synchronously, attempting every cleanup step and reporting failures independently.
     */
    private fun disposeNow() {
        if (disposed) return
        disposed = true
        requestRender = null
        preparedFrame = null
        awaitingActivation = false
        disposeOperation {
            compositionCoroutineScope.cancel()
        }
        disposeOperation {
            overlayHost.clear(overlaySessionId)
        }
        // Logical ownership ends before any native reset can call application code.
        disposeOperation {
            composer.dispose()
        }
        val releaseOwner = logicalOwnerRelease
        logicalOwnerRelease = null
        releaseOwner?.let { operation ->
            disposeOperation(operation)
        }
        sourceRegistration?.let { registration ->
            disposeOperation { registration.dispose() }
            sourceRegistration = null
        }
        val disposeFailures = try {
            val reusableTree = if (disposalMode == DisposalMode.DetachForReuse) {
                platform.renderEngine.detachMountedForReuse(
                    container = container,
                    mountedNodes = mountedNodes,
                )
            } else {
                null
            }
            if (reusableTree != null) {
                detachedPresentation = CoreReusableItemPresentation(
                    tree = reusableTree,
                    releaseTree = platform.renderEngine::releaseReusableMounted,
                )
                emptyList()
            } else {
                platform.renderEngine.disposeMounted(
                    container = container,
                    mountedNodes = mountedNodes,
                )
            }
        } catch (error: Exception) {
            reportFailure(
                frameId = null,
                phase = RenderFailurePhase.SessionDispose,
                recovery = RenderFailureRecovery.SessionDisposed,
                error = error,
            )
            try {
                platform.renderEngine.disposeMounted(
                    container = container,
                    mountedNodes = mountedNodes,
                )
            } catch (releaseError: Exception) {
                error.addSuppressed(releaseError)
                emptyList()
            }
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
        adoptedUncommittedTree = false
    }

    /**
     * A tree adopted from another item is not a previous frame of this logical session. If the
     * first composition or native rebind fails, release it instead of exposing or restoring the
     * old item's declaration and callbacks.
     */
    private fun discardAdoptedUncommittedTree(primaryFailure: Exception) {
        val failures = try {
            platform.renderEngine.disposeMounted(
                container = container,
                mountedNodes = mountedNodes,
            )
        } catch (cleanupError: Exception) {
            primaryFailure.addSuppressed(cleanupError)
            emptyList()
        }
        failures.forEach { failure ->
            primaryFailure.addSuppressed(failure.cause)
        }
        mountedNodes = emptyList()
        adoptedUncommittedTree = false
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
        const val SlowSynchronousEffectWarningNanos: Long = 16_000_000L
        val nextSessionId = AtomicLong(0)
    }

    private enum class DisposalMode {
        Release,
        DetachForReuse,
    }

    private data class PreparedRenderFrame(
        val frameId: Long,
        val frameFailures: MutableList<RenderFailure>,
        val composition: ComposerLite.PreparedComposition<List<com.viewcompose.ui.node.VNode>>,
        val tree: List<com.viewcompose.ui.node.VNode>,
        val sourceCandidates: List<List<com.viewcompose.ui.tooling.UiSourceCallSite>>,
        val frame: CoreRenderFrame,
    )
}

private class CoreReusableItemPresentation(
    tree: CoreReusableRenderTree,
    private val releaseTree: (CoreReusableRenderTree) -> List<CoreRenderCommitFailure>,
) : ReusableItemPresentation {
    private var tree: CoreReusableRenderTree? = tree

    fun takeTree(): CoreReusableRenderTree? {
        val owned = tree
        tree = null
        return owned
    }

    override fun release() {
        val owned = tree ?: return
        tree = null
        releaseTree(owned)
    }
}
