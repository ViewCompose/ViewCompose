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
 * @param debug enables render logging and slow synchronous-operation warnings
 * @param debugTag Android log tag used by this session
 * @param overlayHost host that reconciles overlays declared by [content]
 * @param role logical ownership role used by diagnostics and source tooling
 * @param diagnostics explicit diagnostics root, or `null` to inherit from [parentLocalSnapshot]
 * @param parentLocalSnapshot optional captured parent used once for correlation and inheritance
 */
class RenderSession(
    private val container: RenderContainerHandle,
    private val content: UiTreeBuilder.() -> Unit,
    private val debug: Boolean = false,
    private val debugTag: String = "ViewCompose",
    private val overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    private val role: RenderSessionRole = RenderSessionRole.Host,
    diagnostics: RenderDiagnostics? = null,
    parentLocalSnapshot: UiLocalSnapshot? = null,
) {
    private val platform = RenderSessionPlatformProvider.requirePlatform()
    private val inspectionTooling = platform.diagnostics.inspectionTooling
    private var resolvedInspectionPolicy: RenderSessionInspectionPolicy? = null
    private var mountedNodeInspectionState: RenderSessionMountedNodeState? = null
    private var nodeInspection: RenderSessionNodeInspection? = null
    private var diagnosticInspection: RenderSessionDiagnosticInspection? = null
    private val timingInspectionState = RenderSessionTimingState()
    private val timingInspection: RenderSessionTimingInspection =
        DefaultRenderSessionTimingInspection(timingInspectionState)
    private var activeTimingCapture: ActiveRenderNodeTimingCapture? = null
    private val traceId = RenderSessionTraceId(nextSessionId.incrementAndGet())
    private val inheritedDiagnosticParent = if (diagnostics == null) {
        parentLocalSnapshot?.renderDiagnosticParentOrNull()
    } else {
        null
    }
    private val activeDiagnostics = diagnostics ?: inheritedDiagnosticParent?.diagnostics
    private val parentSessionId = inheritedDiagnosticParent?.sessionId
    private val diagnosticParent = RenderDiagnosticParent(traceId, activeDiagnostics)
    private val sourceContext: RenderDiagnosticContext by lazy(LazyThreadSafetyMode.NONE) {
        RenderDiagnosticContext(
            sessionId = traceId,
            parentSessionId = parentSessionId,
            role = role,
            frameId = null,
            eventSequence = 0,
            monotonicTimestampNanos = platform.diagnostics.monotonicTimeNanos(),
        )
    }
    private val overlaySessionId = OverlaySessionId("render-session-${traceId.value}")
    private val focusManager = platform.focusManagerFactory.create(container)
    private var mountedNodes: List<Any> = emptyList()
    private var disposalRequested: Boolean = false
    private var disposed: Boolean = false
    private var inspectionRegistration: RenderSessionInspectionRegistration? = null
    private var inspectionRegistrationAttempted: Boolean = false
    private var acceptingPreFrameTimingCapture: Boolean = false
    private val overlayRequestStore = OverlayRequestStore()
    private var requestRender: (() -> Unit)? = null
    private val observedProperties = ObservedPropertyRegistry {
        if (!awaitingActivation) {
            requestRender?.invoke()
        }
    }
    private val observedPropertyTargets = LinkedHashMap<Long, CoreObservedPropertyTarget>()
    @Volatile
    private var structuralRenderRequested: Boolean = true
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
    private var renderingActive: Boolean = true
    private var diagnosticsStarted: Boolean = false
    private var diagnosticsEnded: Boolean = false
    private var diagnosticsSinkDisabled: Boolean = false
    private var deliveringDiagnostics: Boolean = false
    private var nextDiagnosticEventSequence: Long = 0

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

    init {
        timingInspectionState.startCapture = ::startTimingCapture
    }

    /**
     * Requests rendering according to the installed runtime policy.
     *
     * The runtime may render synchronously or coalesce the request with a scheduled frame.
     *
     * @throws IllegalStateException after [dispose] has been requested
     */
    fun render() {
        checkNotDeliveringDiagnostics()
        check(!disposalRequested) {
            "RenderSession is disposed and cannot render again."
        }
        structuralRenderRequested = true
        runtime.render()
    }

    private fun startTimingCapture(
        request: RenderNodeTimingCaptureRequest,
    ): RenderNodeTimingCaptureStart {
        if (disposalRequested || disposed) {
            return RenderNodeTimingCaptureStart(
                status = RenderNodeTimingStartStatus.EndedSession,
                capture = null,
            )
        }
        activeTimingCapture?.let { active ->
            if (!active.isComplete) {
                return RenderNodeTimingCaptureStart(
                    status = RenderNodeTimingStartStatus.AlreadyActive,
                    capture = active,
                )
            }
        }
        lateinit var capture: ActiveRenderNodeTimingCapture
        capture = ActiveRenderNodeTimingCapture(
            request = request,
            context = sourceContext,
            clock = platform.diagnostics::monotonicTimeNanos,
            onFinished = {
                if (activeTimingCapture === capture) activeTimingCapture = null
            },
        )
        activeTimingCapture = capture
        if (!acceptingPreFrameTimingCapture) {
            structuralRenderRequested = true
            runtime.render()
        }
        return RenderNodeTimingCaptureStart(
            status = RenderNodeTimingStartStatus.Started,
            capture = capture,
        )
    }

    /** Builds an initial native tree while retaining the composition commit boundary. */
    internal fun prepareForActivation() {
        checkNotDeliveringDiagnostics()
        if (disposalRequested || disposed || committedFrameId != null || preparedFrame != null) return
        awaitingActivation = true
        structuralRenderRequested = false
        preparedFrame = prepareFrame()
        if (preparedFrame == null) {
            awaitingActivation = false
            if (lastFrameReport?.status == RenderFrameStatus.RolledBack) {
                dispose()
            }
        }
    }

    /** Commits a valid prepared tree, or rebuilds when observed state changed before attachment. */
    internal fun activatePrepared() {
        checkNotDeliveringDiagnostics()
        if (disposalRequested || disposed) return
        val pending = preparedFrame
        preparedFrame = null
        awaitingActivation = false
        if (pending == null) {
            runtime.render()
            return
        }
        if (
            composer.hasPendingInvalidations() ||
            pending.observedPropertyAttempt.hasInvalidatedCandidates()
        ) {
            abortPreparedFrame(pending)
            structuralRenderRequested = true
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
     *
     * @throws IllegalStateException after [dispose] has been requested
     */
    fun setRenderingActive(active: Boolean) {
        checkNotDeliveringDiagnostics()
        check(!disposalRequested) {
            "RenderSession is disposed and cannot change rendering activity."
        }
        if (renderingActive == active) return
        runtime.setRenderingActive(active)
        inspectionRegistration?.let { registration ->
            runInspectionToolingOperation("update inspection session") {
                registration.setRenderingActive(active)
            }
        }
        renderingActive = active
        ensureDiagnosticsStarted()
        if (activeDiagnostics?.collection?.lifecycle == true) {
            emitDiagnosticEvent { context ->
                RenderSessionActivityChanged(
                    context = context,
                    active = active,
                )
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
        checkNotDeliveringDiagnostics()
        if (disposalRequested) return
        disposalRequested = true
        runtime.dispose()
    }

    /** Disposes composition, invokes [releaseOwner], then releases the mounted native tree. */
    internal fun disposeWithLogicalOwnerRelease(releaseOwner: () -> Unit) {
        checkNotDeliveringDiagnostics()
        if (disposalRequested || disposed) {
            releaseOwner()
            return
        }
        disposalRequested = true
        logicalOwnerRelease = releaseOwner
        runtime.dispose()
    }

    /** Terminates logical ownership and returns a reset native tree when the renderer permits it. */
    internal fun disposeForReuse(releaseOwner: () -> Unit = {}): ReusableItemPresentation? {
        checkNotDeliveringDiagnostics()
        if (disposalRequested || disposed) {
            releaseOwner()
            return null
        }
        disposalRequested = true
        disposalMode = DisposalMode.DetachForReuse
        logicalOwnerRelease = releaseOwner
        runtime.dispose()
        return detachedPresentation
    }

    /** Installs a detached physical tree before this session produces its first frame. */
    internal fun adoptReusablePresentation(presentation: ReusableItemPresentation): Boolean {
        if (
            disposalRequested || disposed || mountedNodes.isNotEmpty() ||
            committedFrameId != null || preparedFrame != null
        ) {
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
                mountedNodeInspectionState?.mountedNodes = nodes
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
        if (disposalRequested || disposed) return
        preparedFrame?.let { pending ->
            preparedFrame = null
            awaitingActivation = false
            abortPreparedFrame(pending)
            structuralRenderRequested = true
        }
        val requiresStructuralFrame = structuralRenderRequested ||
            committedFrameId == null ||
            composer.hasPendingInvalidations()
        structuralRenderRequested = false
        if (requiresStructuralFrame) {
            prepareFrame()?.let(::commitFrame)
        } else if (observedProperties.hasDirtyBindings()) {
            renderObservedProperties()
        }
    }

    /** Prepares composition and the native tree without crossing the commit boundary. */
    private fun <T> prepareRootForFrame(
        timingCapture: ActiveRenderNodeTimingCapture?,
        collectDiagnostics: Boolean,
        block: () -> T,
    ): ComposerLite.PreparedComposition<T> {
        return if (timingCapture == null) {
            composer.prepareRoot(
                collectDiagnostics = collectDiagnostics,
                block = block,
            )
        } else {
            composer.prepareRootWithTiming(
                collector = timingCapture,
                collectDiagnostics = collectDiagnostics,
                block = block,
            )
        }
    }

    /** Prepares composition and the native tree without crossing the commit boundary. */
    private fun prepareFrame(): PreparedRenderFrame? {
        if (disposalRequested || disposed) return null
        val initialInspectionPolicy = if (!inspectionRegistrationAttempted) {
            inspectionPolicy()
        } else {
            null
        }
        if (initialInspectionPolicy == RenderSessionInspectionPolicy.TrackSessionBeforeFirstFrame) {
            acceptingPreFrameTimingCapture = true
            try {
                registerInspectionSession(emptyList())
            } finally {
                acceptingPreFrameTimingCapture = false
            }
        }
        val frameId = nextFrameId.incrementAndGet()
        val timingCapture = activeTimingCapture?.takeIf { capture ->
            capture.beginFrame(frameId)
        }
        val frameFailures = mutableListOf<RenderFailure>()
        var failurePhase = RenderFailurePhase.CompositionPrepare
        var preparedComposition:
            ComposerLite.PreparedComposition<List<com.viewcompose.ui.node.VNode>>? = null
        val observedPropertyAttempt = observedProperties.beginFullAttempt()
        var tree: List<com.viewcompose.ui.node.VNode> = emptyList()
        var capturedSourceCandidates =
            emptyList<List<com.viewcompose.ui.tooling.UiSourceCallSite>>()
        val diagnosticLevel = activeDiagnostics?.collection?.frameLevel
            ?: RenderFrameDiagnosticLevel.None
        val frame = try {
            if (!composer.hasPendingInvalidations()) {
                // External render requests (e.g. lazy/pager sessionUpdater) must recompose root even without runtime state invalidation signals.
                composer.requestRootRecompose()
            }
            platform.diagnostics.trace("VC.Compose") {
                FocusManagerContext.withFocusManager(focusManager) {
                    LocalContext.provide(LocalRenderDiagnosticParent, diagnosticParent) {
                        LocalContext.provide(LocalOverlayHost.holder, overlayHost) {
                            OverlayRequestContext.withStore(overlayRequestStore) {
                                ObservedPropertyContext.withAttempt(observedPropertyAttempt) {
                                    ComposerContext.withComposer(
                                        composer = composer,
                                        coroutineContext = compositionCoroutineScope.coroutineContext,
                                    ) {
                                        preparedComposition = prepareRootForFrame(
                                            timingCapture = timingCapture,
                                            collectDiagnostics =
                                                diagnosticLevel == RenderFrameDiagnosticLevel.Tree,
                                        ) {
                                            if (
                                                initialInspectionPolicy ==
                                                RenderSessionInspectionPolicy.TrackSessionAndCaptureSources
                                            ) {
                                                UiNodeTooling.withSourceCandidateCapture(
                                                    onSourceCandidatesCaptured = { candidates ->
                                                        capturedSourceCandidates = candidates
                                                    },
                                                ) {
                                                    observedPropertyAttempt.retainTree(
                                                        buildVNodeTree(content),
                                                    )
                                                }
                                            } else {
                                                observedPropertyAttempt.retainTree(
                                                    buildVNodeTree(content),
                                                )
                                            }
                                        }
                                        tree = checkNotNull(preparedComposition).value
                                        }
                                }
                            }
                        }
                    }
                }
            }
            failurePhase = RenderFailurePhase.ViewTreeRender
            platform.diagnostics.trace("VC.RenderTree") {
                if (timingCapture?.capturesRendererTiming == true) {
                    platform.renderEngine.renderIntoWithTiming(
                        container = container,
                        previousMountedNodes = mountedNodes,
                        nodes = tree,
                        diagnosticLevel = diagnosticLevel,
                        timingCollector = timingCapture,
                    )
                } else {
                    platform.renderEngine.renderInto(
                        container = container,
                        previousMountedNodes = mountedNodes,
                        nodes = tree,
                        diagnosticLevel = diagnosticLevel,
                    )
                }
            }
        } catch (error: Exception) {
            observedPropertyAttempt.abort()
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
            completeFrame(
                report = RenderFrameReport(
                frameId = frameId,
                status = RenderFrameStatus.RolledBack,
                failures = frameFailures.toList(),
                ),
            )
            return null
        } finally {
            timingCapture?.completeFrame()
        }

        mountedNodes = frame.mountedNodes
        mountedNodeInspectionState?.mountedNodes = frame.mountedNodes
        observedPropertyTargets.clear()
        observedPropertyTargets.putAll(frame.observedPropertyTargets)
        adoptedUncommittedTree = false
        return PreparedRenderFrame(
            frameId = frameId,
            frameFailures = frameFailures,
            composition = checkNotNull(preparedComposition),
            tree = tree,
            sourceCandidates = capturedSourceCandidates,
            frame = frame,
            observedPropertyAttempt = observedPropertyAttempt,
        )
    }

    /** Reads and applies one coalesced exact-target property transaction. */
    private fun renderObservedProperties() {
        if (disposalRequested || disposed) return
        val frameId = nextFrameId.incrementAndGet()
        val timingCapture = activeTimingCapture?.takeIf { capture ->
            capture.beginFrame(frameId)
        }
        try {
            renderObservedPropertiesFrame(
                frameId = frameId,
                timingCapture = timingCapture,
            )
        } finally {
            timingCapture?.completeFrame()
        }
    }

    private fun renderObservedPropertiesFrame(
        frameId: Long,
        timingCapture: ActiveRenderNodeTimingCapture?,
    ) {
        val frameFailures = mutableListOf<RenderFailure>()
        val transaction = try {
            platform.diagnostics.trace("VC.ObservedPropertyRead") {
                observedProperties.prepareDirty()
            }
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.ObservedPropertyPrepare,
                recovery = RenderFailureRecovery.FrameUnchanged,
                error = error,
                frameFailures = frameFailures,
            )
            completeFrame(
                report = RenderFrameReport(
                    frameId = frameId,
                    status = RenderFrameStatus.RolledBack,
                    failures = frameFailures.toList(),
                ),
            )
            return
        } ?: return

        val changes = transaction.changes
        if (changes.isEmpty()) {
            committedFrameId = frameId
            try {
                transaction.commit()
            } catch (error: Exception) {
                reportFailure(
                    frameId = frameId,
                    phase = RenderFailurePhase.ObservedPropertyCommit,
                    recovery = RenderFailureRecovery.FrameUnchanged,
                    error = error,
                    frameFailures = frameFailures,
                )
            }
            completeFrame(
                report = RenderFrameReport(
                    frameId = frameId,
                    status = RenderFrameStatus.Committed,
                    failures = frameFailures.toList(),
                ),
                stats = RenderStats(),
            )
            return
        }

        val corePatches = try {
            changes.map { change ->
                val target = observedPropertyTargets[change.id]
                    ?: error("Observed property ${change.id} has no committed renderer target.")
                check(target.node.observedPropertyId == change.id) {
                    "Observed property ${change.id} target identity changed."
                }
                check(target.node.spec == change.previous) {
                    "Observed property ${change.id} target no longer has its committed NodeSpec."
                }
                val next = UiNodeTooling.inheritCopy(
                    target = target.node.copy(spec = change.next),
                    source = target.node,
                )
                CoreObservedPropertyPatch(
                    id = change.id,
                    target = target,
                    previous = target.node,
                    next = next,
                )
            }
        } catch (error: Exception) {
            transaction.abort()
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.ObservedPropertyPrepare,
                recovery = RenderFailureRecovery.FrameUnchanged,
                error = error,
                frameFailures = frameFailures,
            )
            completeFrame(
                report = RenderFrameReport(
                    frameId = frameId,
                    status = RenderFrameStatus.RolledBack,
                    failures = frameFailures.toList(),
                ),
            )
            return
        }

        val diagnosticLevel = activeDiagnostics?.collection?.frameLevel
            ?: RenderFrameDiagnosticLevel.None
        val frame = try {
            platform.diagnostics.trace("VC.ObservedPropertyRender") {
                if (timingCapture?.capturesRendererTiming == true) {
                    platform.renderEngine.patchObservedPropertiesWithTiming(
                        container = container,
                        mountedNodes = mountedNodes,
                        patches = corePatches,
                        diagnosticLevel = diagnosticLevel,
                        timingCollector = timingCapture,
                    )
                } else {
                    platform.renderEngine.patchObservedProperties(
                        container = container,
                        mountedNodes = mountedNodes,
                        patches = corePatches,
                        diagnosticLevel = diagnosticLevel,
                    )
                }
            }
        } catch (error: Exception) {
            transaction.abort()
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.ObservedPropertyRender,
                recovery = RenderFailureRecovery.PreviousFrameRestored,
                error = error,
                frameFailures = frameFailures,
            )
            completeFrame(
                report = RenderFrameReport(
                    frameId = frameId,
                    status = RenderFrameStatus.RolledBack,
                    failures = frameFailures.toList(),
                ),
            )
            return
        }

        corePatches.forEach { patch ->
            patch.target.advance(
                previous = patch.previous,
                next = patch.next,
            )
        }
        committedFrameId = frameId
        try {
            transaction.commit()
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.ObservedPropertyCommit,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = error,
                frameFailures = frameFailures,
            )
        }
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
        completeFrame(
            report = RenderFrameReport(
                frameId = frameId,
                status = RenderFrameStatus.Committed,
                failures = frameFailures.toList(),
            ),
            stats = frame.renderStats,
            tree = frame.renderResult,
        )
    }

    /** Crosses the single composition/native/effect commit boundary for a prepared frame. */
    private fun commitFrame(prepared: PreparedRenderFrame) {
        val frameId = prepared.frameId
        val frameFailures = prepared.frameFailures
        val composition = prepared.composition
        val tree = prepared.tree
        val frame = prepared.frame
        ensureDiagnosticsStarted()

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
        try {
            prepared.observedPropertyAttempt.commit()
        } catch (error: Exception) {
            reportFailure(
                frameId = frameId,
                phase = RenderFailurePhase.ObservedPropertyCommit,
                recovery = RenderFailureRecovery.FrameCommitted,
                error = error,
                frameFailures = frameFailures,
            )
        }
        if (
            !inspectionRegistrationAttempted &&
            resolvedInspectionPolicy != null &&
            resolvedInspectionPolicy != RenderSessionInspectionPolicy.Ignore
        ) {
            registerInspectionSession(prepared.sourceCandidates)
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
        completeFrame(
            report = RenderFrameReport(
                frameId = frameId,
                status = RenderFrameStatus.Committed,
                failures = frameFailures.toList(),
            ),
            stats = frame.renderStats,
            tree = frame.renderResult?.copy(
                composition = composition.diagnostics,
            ),
        )
    }

    /** Aborts a speculative composition while retaining its native tree as the next diff input. */
    private fun abortPreparedFrame(prepared: PreparedRenderFrame) {
        prepared.observedPropertyAttempt.abort()
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
        mountedNodeInspectionState?.ended = true
        mountedNodeInspectionState?.mountedNodes = emptyList()
        timingInspectionState.ended = true
        timingInspectionState.startCapture = null
        activeTimingCapture?.endSession()
        activeTimingCapture = null
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
        disposeOperation {
            observedProperties.dispose()
        }
        val releaseOwner = logicalOwnerRelease
        logicalOwnerRelease = null
        releaseOwner?.let { operation ->
            disposeOperation(operation)
        }
        inspectionRegistration?.let { registration ->
            disposeOperation { registration.dispose() }
            inspectionRegistration = null
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
        observedPropertyTargets.clear()
        adoptedUncommittedTree = false
        endDiagnostics()
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
        mountedNodeInspectionState?.mountedNodes = emptyList()
        adoptedUncommittedTree = false
    }

    private inline fun runInspectionToolingOperation(
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

    private fun registerInspectionSession(
        sourceCandidates: List<List<com.viewcompose.ui.tooling.UiSourceCallSite>>,
    ) {
        inspectionRegistrationAttempted = true
        runInspectionToolingOperation("register inspection session") {
            inspectionRegistration = inspectionTooling?.register(
                container = container,
                context = sourceContext,
                sourceCandidates = sourceCandidates,
                nodeInspection = checkNotNull(nodeInspection),
                diagnosticInspection = checkNotNull(diagnosticInspection),
                timingInspection = timingInspection,
            )
            if (!renderingActive) {
                inspectionRegistration?.setRenderingActive(false)
            }
        }
    }

    private fun inspectionPolicy(): RenderSessionInspectionPolicy {
        resolvedInspectionPolicy?.let { policy -> return policy }
        val tooling = inspectionTooling
        val policy = if (tooling == null) {
            RenderSessionInspectionPolicy.Ignore
        } else try {
            tooling.inspectionPolicy(
                container = container,
                context = sourceContext,
            )
        } catch (error: Exception) {
            platform.diagnostics.error(
                debugTag,
                "Render-session tooling could not evaluate its inspection policy.",
                error,
            )
            RenderSessionInspectionPolicy.Ignore
        }
        if (policy != RenderSessionInspectionPolicy.Ignore) {
            val state = RenderSessionMountedNodeState()
            mountedNodeInspectionState = state
            nodeInspection = DefaultRenderSessionNodeInspection(
                state = state,
                renderEngine = platform.renderEngine,
            )
            diagnosticInspection = DefaultRenderSessionDiagnosticInspection(
                owner = this,
                context = sourceContext,
            )
        }
        resolvedInspectionPolicy = policy
        return policy
    }

    internal fun diagnosticInspectionSnapshot(): RenderSessionDiagnosticSnapshot {
        val frame = lastFrameReport
        val retainedFailures = frame?.failures.orEmpty().take(MAX_INSPECTED_FRAME_FAILURES)
        return RenderSessionDiagnosticSnapshot(
            sessionId = traceId,
            parentSessionId = parentSessionId,
            role = role,
            renderingActive = renderingActive,
            committedFrameId = committedFrameId,
            latestFrame = frame?.let { report ->
                RenderSessionInspectedFrame(
                    frameId = report.frameId,
                    status = report.status,
                    failures = retainedFailures.map(RenderFailure::toInspectionSummary),
                    droppedFailures = (report.failures.size - retainedFailures.size).coerceAtLeast(0),
                )
            },
            latestFailure = lastRenderFailure?.toInspectionSummary(),
            ended = disposed,
        )
    }

    private fun ensureDiagnosticsStarted() {
        if (
            diagnosticsStarted || diagnosticsEnded ||
            activeDiagnostics?.collection?.lifecycle != true
        ) {
            return
        }
        diagnosticsStarted = true
        emitDiagnosticEvent { context -> RenderSessionStarted(context) }
    }

    private fun completeFrame(
        report: RenderFrameReport,
        stats: RenderStats? = null,
        tree: RenderTreeResult? = null,
    ) {
        lastFrameReport = report
        ensureDiagnosticsStarted()
        val level = activeDiagnostics?.collection?.frameLevel
            ?: return
        val committed = report.status == RenderFrameStatus.Committed
        emitDiagnosticEvent(frameId = report.frameId) { context ->
            RenderFrameCompleted(
                context = context,
                report = report,
                stats = if (committed && level != RenderFrameDiagnosticLevel.None) stats else null,
                tree = if (committed && level == RenderFrameDiagnosticLevel.Tree) tree else null,
            )
        }
    }

    private fun endDiagnostics() {
        if (
            diagnosticsEnded || !diagnosticsStarted ||
            activeDiagnostics?.collection?.lifecycle != true
        ) {
            return
        }
        diagnosticsEnded = true
        emitDiagnosticEvent { context -> RenderSessionEnded(context) }
    }

    private inline fun emitDiagnosticEvent(
        frameId: Long? = null,
        event: (RenderDiagnosticContext) -> RenderDiagnosticEvent,
    ) {
        val configuredDiagnostics = activeDiagnostics ?: return
        if (diagnosticsSinkDisabled) return
        val context = RenderDiagnosticContext(
            sessionId = traceId,
            parentSessionId = parentSessionId,
            role = role,
            frameId = frameId,
            eventSequence = ++nextDiagnosticEventSequence,
            monotonicTimestampNanos = platform.diagnostics.monotonicTimeNanos(),
        )
        deliveringDiagnostics = true
        try {
            configuredDiagnostics.sink.onEvent(event(context))
        } catch (error: Throwable) {
            diagnosticsSinkDisabled = true
            val failure = RenderFailure(
                frameId = frameId,
                phase = RenderFailurePhase.DiagnosticsSink,
                recovery = when {
                    disposed -> RenderFailureRecovery.SessionDisposed
                    frameId != null && committedFrameId == frameId -> {
                        RenderFailureRecovery.FrameCommitted
                    }
                    else -> RenderFailureRecovery.FrameUnchanged
                },
                cause = error,
            )
            lastRenderFailure = failure
            platform.diagnostics.error(
                debugTag,
                "Render diagnostics sink failed and was disabled for session ${traceId.value}.",
                error,
            )
        } finally {
            deliveringDiagnostics = false
        }
    }

    private fun checkNotDeliveringDiagnostics() {
        check(!deliveringDiagnostics) {
            "A RenderDiagnosticsSink cannot re-enter its emitting RenderSession."
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
     * Records a failure, updates the latest failure state, and publishes it when selected.
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
        ensureDiagnosticsStarted()
        if (activeDiagnostics?.collection?.failures == true) {
            emitDiagnosticEvent(frameId = frameId) { context ->
                RenderFailureObserved(
                    context = context,
                    failure = failure,
                )
            }
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
        val observedPropertyAttempt: ObservedPropertyFullAttempt,
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
