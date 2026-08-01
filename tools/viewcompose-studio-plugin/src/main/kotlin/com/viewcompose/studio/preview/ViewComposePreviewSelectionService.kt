package com.viewcompose.studio.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Alarm
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.kotlin.psi.KtCallExpression

@Service(Service.Level.PROJECT)
internal class ViewComposePreviewSelectionService(
    private val project: Project,
) : Disposable {
    private val currentState = AtomicReference<ViewComposePreviewPanelState>(
        ViewComposePreviewPanelState.Empty,
    )
    private val requestGeneration = AtomicLong(0)
    private val editorFollowGeneration = AtomicLong(0)
    private val activeIndicator = AtomicReference<ProgressIndicator?>()
    private val activeRequest = AtomicReference<ActivePreviewRequest?>()
    private val galleryPriorityOrder = AtomicReference<PreviewGalleryPriorityOrder?>()
    private val automaticRefreshGate =
        PreviewAutomaticRefreshGate<AutomaticPreviewRefreshRequest> { pending, latest ->
            when {
                pending.request.selection != latest.request.selection -> latest
                pending.refreshPolicy == PreviewRefreshPolicy.SavedInput -> pending.copy(
                    request = latest.request,
                )
                else -> latest
            }
        }
    private val pendingSavedRefreshPolicy = AtomicReference<PreviewRefreshPolicy?>()
    private val savedInputRefreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val editorFollowAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val galleryDiscoveryRetryAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val settings = ViewComposePreviewSettings.forProject(project)
    private val logger = Logger.getInstance(ViewComposePreviewSelectionService::class.java)
    private val cacheRoot = project.basePath
        ?.let { previewCacheRoot(Path.of(PathManager.getSystemPath())) }
    private val diskCache = cacheRoot?.resolve("detail")?.let(::PreviewDiskCache)
    private val galleryCache = cacheRoot?.resolve("gallery")?.let(::PreviewGalleryDiskCache)
    private val gradleExecutor = project.basePath
        ?.let(Path::of)
        ?.let(::PersistentPreviewGradleExecutor)

    @Volatile
    private var attachedPanel: ViewComposePreviewToolWindowPanel? = null

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (!settings.autoRefreshOnSave) return
                    val request = activeRequest.get() ?: return
                    val inputScope = currentState.get()
                        .previousSuccessOrNull()
                        ?.takeIf { result -> result.selection == request.selection }
                        ?.inputScope
                    if (
                        !savedPreviewInputMatches(
                            projectRoot = project.basePath?.let(Path::of),
                            selection = request.selection,
                            changedPaths = events.map(VFileEvent::getPath),
                            inputScope = inputScope,
                        )
                    ) {
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        val shouldRefresh = shouldRunAutomaticPreviewRefresh(
                            projectDisposed = project.isDisposed,
                            activeRequestMatches = request == activeRequest.get(),
                            toolWindowVisible = isPreviewToolWindowVisible(),
                        )
                        if (shouldRefresh) {
                            scheduleSavedInputRefresh(
                                request = request,
                                refreshPolicy = if (
                                    savedPreviewFastRefreshEligible(
                                        selection = request.selection,
                                        changedPaths = events.map(VFileEvent::getPath),
                                    )
                                ) {
                                    PreviewRefreshPolicy.SavedSourceInput
                                } else {
                                    PreviewRefreshPolicy.SavedInput
                                },
                            )
                        }
                    }
                }
            },
        )
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    scheduleEditorFollow()
                }
            },
        )
        EditorFactory.getInstance().eventMulticaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (event.editor.project != project) return
                    scheduleEditorFollow(event.editor)
                }
            },
            this,
        )
    }

    fun attach(panel: ViewComposePreviewToolWindowPanel) {
        attachedPanel = panel
        panel.showState(currentState.get())
        if (currentState.get() == ViewComposePreviewPanelState.Empty) {
            showGallery()
        }
        scheduleEditorFollow()
    }

    fun detach(panel: ViewComposePreviewToolWindowPanel) {
        if (attachedPanel === panel) {
            attachedPanel = null
        }
    }

    fun selectAndShow(selection: PreviewSourceSelection) {
        render(selection = selection, requestedVariantId = null)
        ToolWindowManager.getInstance(project).invokeLater {
            ToolWindowManager.getInstance(project)
                .getToolWindow(VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID)
                ?.show()
        }
    }

    fun selectVariant(variantId: String) {
        val rendered = currentState.get() as? ViewComposePreviewPanelState.Rendered ?: return
        val result = rendered.result
        if (variantId == result.selectedVariantId) return
        if (result.variants.none { variant -> variant.id == variantId }) return
        render(
            selection = result.selection,
            requestedVariantId = variantId,
            refreshPolicy = PreviewRefreshPolicy.Variant,
        )
    }

    fun refreshCurrent() {
        val request = activeRequest.get()
        if (request == null) {
            if (
                currentState.get() is ViewComposePreviewPanelState.Gallery ||
                currentState.get() is ViewComposePreviewPanelState.GalleryLoading ||
                currentState.get() is ViewComposePreviewPanelState.GalleryFailed
            ) {
                showGallery(forceRerender = true)
            }
            return
        }
        render(
            selection = request.selection,
            requestedVariantId = request.variantId,
            refreshPolicy = PreviewRefreshPolicy.Manual,
        )
    }

    fun fullRefreshCurrent() {
        val request = activeRequest.get() ?: return
        render(
            selection = request.selection,
            requestedVariantId = request.variantId,
            refreshPolicy = PreviewRefreshPolicy.Full,
        )
    }

    fun hasSelectedPreview(): Boolean = activeRequest.get() != null

    fun hasActivePreview(): Boolean {
        return activeRequest.get() != null ||
            currentState.get() is ViewComposePreviewPanelState.Gallery ||
            currentState.get() is ViewComposePreviewPanelState.GalleryLoading ||
            currentState.get() is ViewComposePreviewPanelState.GalleryFailed
    }

    fun showGalleryAndShow() {
        showGallery()
        ToolWindowManager.getInstance(project).invokeLater {
            ToolWindowManager.getInstance(project)
                .getToolWindow(VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID)
                ?.show()
        }
    }

    fun followSelectedEditor() {
        scheduleEditorFollow()
    }

    fun prioritizeGallery(selections: List<PreviewSourceSelection>) {
        galleryPriorityOrder.get()?.prioritize(selections)
    }

    private fun render(
        selection: PreviewSourceSelection,
        requestedVariantId: String?,
        refreshPolicy: PreviewRefreshPolicy = PreviewRefreshPolicy.Open,
    ) {
        val nextRequest = ActivePreviewRequest(
            selection = selection,
            variantId = requestedVariantId,
        )
        if (
            refreshPolicy.automatic &&
            automaticRefreshGate.deferIfActive(
                AutomaticPreviewRefreshRequest(nextRequest, refreshPolicy),
            )
        ) {
            return
        }
        galleryDiscoveryRetryAlarm.cancelAllRequests()
        galleryPriorityOrder.set(null)
        activeRequest.set(nextRequest)
        val generation = requestGeneration.incrementAndGet()
        if (refreshPolicy.automatic) {
            automaticRefreshGate.markActive(generation)
        } else {
            automaticRefreshGate.supersede(generation)
        }
        activeIndicator.getAndSet(null)?.cancel()
        val previousResult = currentState.get()
            .previousSuccessOrNull()
            ?.takeIf { result -> result.selection == selection }
        publish(
            generation = generation,
            state = ViewComposePreviewPanelState.Loading(
                selection = selection,
                message = "Preparing static preview…",
                previousResult = previousResult,
            ),
        )
        object : Task.Backgroundable(
            project,
            "Render ViewCompose Preview",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                if (generation != requestGeneration.get()) return
                activeIndicator.set(indicator)
                if (generation != requestGeneration.get()) {
                    indicator.cancel()
                    return
                }
                try {
                    val root = project.basePath?.let(Path::of)
                    val cacheReadStartedAtNanos = System.nanoTime()
                    val cached = if (refreshPolicy.useStudioCache) {
                        diskCache?.read(selection, requestedVariantId)?.let { result ->
                            result.copy(
                                performanceTrace = result.performanceTrace.plus(
                                    phase = "studio-cache-read",
                                    durationMillis = elapsedMillis(cacheReadStartedAtNanos),
                                ),
                            )
                        }
                    } else {
                        null
                    }
                    val outcome = if (cached != null) {
                        cached
                    } else if (root == null) {
                        PreviewRenderOutcome.Failure(
                            selection = selection,
                            title = "Preview project is unavailable",
                            diagnostics = emptyList(),
                            details = "Android Studio did not provide a project base directory.",
                        )
                    } else {
                        val coordinator = renderCoordinator(root)
                        val progress: (String) -> Unit = { message ->
                            publish(
                                generation = generation,
                                state = ViewComposePreviewPanelState.Loading(
                                    selection = selection,
                                    message = message,
                                    previousResult = previousResult,
                                ),
                            )
                        }
                        val knownOutcome = previousResult
                            ?.takeIf { refreshPolicy.useKnownDescriptor }
                            ?.let { result ->
                                coordinator.renderKnownDebug(
                                    selection = selection,
                                    descriptorId = result.descriptorId,
                                    requestedVariantId = requestedVariantId
                                        ?: result.selectedVariantId,
                                    fastRefresh = refreshPolicy.fastGradleRefresh,
                                    forceRerender = refreshPolicy.forceGradleRerender,
                                    forceFullRebuild = refreshPolicy.forceFullRebuild,
                                    indicator = indicator,
                                    onProgress = progress,
                                )
                            }
                        knownOutcome ?: coordinator.render(
                            selection = selection,
                            requestedVariantId = requestedVariantId,
                            forceRerender = refreshPolicy.forceGradleRerender,
                            forceFullRebuild = refreshPolicy.forceFullRebuild,
                            indicator = indicator,
                            onProgress = progress,
                        )
                    }
                    if (outcome is PreviewRenderOutcome.Success &&
                        generation == requestGeneration.get()
                    ) {
                        logPerformance(outcome)
                        if (cached == null) {
                            runCatching { diskCache?.write(outcome) }
                        }
                        activeRequest.set(
                            ActivePreviewRequest(
                                selection = outcome.selection,
                                variantId = outcome.selectedVariantId,
                            ),
                        )
                    }
                    publish(
                        generation = generation,
                        state = when (outcome) {
                            is PreviewRenderOutcome.Success ->
                                ViewComposePreviewPanelState.Rendered(outcome)

                            is PreviewRenderOutcome.Failure ->
                                ViewComposePreviewPanelState.Failed(outcome)
                        },
                    )
                } catch (cancelled: ProcessCanceledException) {
                    throw cancelled
                } finally {
                    activeIndicator.compareAndSet(indicator, null)
                    finishRender(generation)
                }
            }

            override fun onCancel() {
                publish(
                    generation = generation,
                    state = ViewComposePreviewPanelState.Failed(
                        PreviewRenderOutcome.Failure(
                            selection = selection,
                            title = "Preview render cancelled",
                            diagnostics = emptyList(),
                        ),
                    ),
                )
                finishRender(generation)
            }
        }.queue()
    }

    private fun finishRender(generation: Long) {
        val pending = automaticRefreshGate.complete(generation) ?: return
        ApplicationManager.getApplication().invokeLater {
            val current = activeRequest.get()
            val shouldRefresh = shouldRunAutomaticPreviewRefresh(
                projectDisposed = project.isDisposed,
                activeRequestMatches = current != null &&
                    pending.request.selection == current.selection,
                toolWindowVisible = isPreviewToolWindowVisible(),
            )
            if (shouldRefresh) {
                checkNotNull(current)
                render(
                    selection = pending.request.selection,
                    requestedVariantId = pending.request.variantId ?: current.variantId,
                    refreshPolicy = pending.refreshPolicy,
                )
            }
        }
    }

    private fun showGallery(
        forceRerender: Boolean = false,
        discoveryAttempt: Int = 0,
    ) {
        galleryDiscoveryRetryAlarm.cancelAllRequests()
        galleryPriorityOrder.set(null)
        automaticRefreshGate.clear()
        pendingSavedRefreshPolicy.set(null)
        activeRequest.set(null)
        val generation = requestGeneration.incrementAndGet()
        activeIndicator.getAndSet(null)?.cancel()
        val previous = (currentState.get() as? ViewComposePreviewPanelState.Gallery)?.result
        publish(
            generation = generation,
            state = ViewComposePreviewPanelState.GalleryLoading(
                message = "Discovering project previews…",
                previousResult = previous,
            ),
        )
        object : Task.Backgroundable(
            project,
            "Load ViewCompose Preview Gallery",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                if (generation != requestGeneration.get()) return
                activeIndicator.set(indicator)
                var ownedPriorityOrder: PreviewGalleryPriorityOrder? = null
                try {
                    val selections = ViewComposePreviewProjectScanner(project).scan()
                    galleryDiscoveryRetryDelayMillis(discoveryAttempt)?.let { retryDelay ->
                        if (selections.isEmpty()) {
                            publish(
                                generation = generation,
                                state = ViewComposePreviewPanelState.GalleryLoading(
                                    message = "Waiting for project indexes…",
                                    previousResult = previous,
                                ),
                            )
                            scheduleGalleryDiscoveryRetry(
                                generation = generation,
                                forceRerender = forceRerender,
                                nextAttempt = discoveryAttempt + 1,
                                delayMillis = retryDelay,
                            )
                            return
                        }
                    }
                    val cachedItems = if (forceRerender) {
                        emptyList()
                    } else {
                        galleryCache?.readAll(selections).orEmpty()
                    }
                    val cachedBySelection = cachedItems.groupBy(PreviewGalleryItem::selection)
                    val missing = selections.filterNot(cachedBySelection::containsKey)
                    val priorityOrder = PreviewGalleryPriorityOrder(missing)
                    ownedPriorityOrder = priorityOrder
                    if (generation == requestGeneration.get()) {
                        galleryPriorityOrder.set(priorityOrder)
                    }
                    fun ordered(items: Collection<PreviewGalleryItem>): List<PreviewGalleryItem> {
                        return items.sortedWith(
                            compareBy(
                                { item -> selections.indexOf(item.selection) },
                                PreviewGalleryItem::variantIndex,
                                PreviewGalleryItem::variantId,
                            ),
                        )
                    }
                    val pendingSelections = missing.toMutableSet()
                    publish(
                        generation = generation,
                        state = ViewComposePreviewPanelState.GalleryLoading(
                            message = "Rendering ${missing.size} uncached previews…",
                            previousResult = PreviewGalleryResult(
                                items = ordered(cachedItems),
                                failures = emptyList(),
                                pendingSelections = pendingSelections.toList(),
                            ),
                        ),
                    )
                    val root = project.basePath?.let(Path::of)
                    val renderedGalleryItems = mutableListOf<PreviewGalleryItem>()
                    val failures = mutableListOf<PreviewRenderOutcome.Failure>()
                    fun consume(outcome: PreviewRenderOutcome) {
                        when (outcome) {
                            is PreviewRenderOutcome.Success -> {
                                logPerformance(outcome)
                                pendingSelections.remove(outcome.selection)
                                val item = runCatching {
                                    galleryCache?.write(outcome)
                                }.getOrNull() ?: outcome.toBoundedGalleryItem()
                                renderedGalleryItems += item
                            }
                            is PreviewRenderOutcome.Failure -> {
                                pendingSelections.remove(outcome.selection)
                                failures += outcome
                            }
                        }
                    }
                    if (root == null) {
                        missing.forEach { selection ->
                            consume(
                                PreviewRenderOutcome.Failure(
                                    selection = selection,
                                    title = "Preview project is unavailable",
                                    diagnostics = emptyList(),
                                ),
                            )
                        }
                    } else {
                        renderCoordinator(root).renderAllEach(
                            selections = missing,
                            forceRerender = forceRerender,
                            indicator = indicator,
                            onProgress = { message ->
                                publish(
                                    generation = generation,
                                    state = ViewComposePreviewPanelState.GalleryLoading(
                                        message = message,
                                        previousResult = PreviewGalleryResult(
                                            items = ordered(cachedItems + renderedGalleryItems),
                                            failures = failures.toList(),
                                            pendingSelections = pendingSelections.toList(),
                                        ),
                                    ),
                                )
                            },
                            batchStrategy = PreviewGalleryBatchStrategy(
                                firstBatchSelectionCount = GALLERY_INITIAL_RENDER_COUNT,
                                priorityOrder = priorityOrder,
                                batchCompleted = {
                                    publish(
                                        generation = generation,
                                        state = ViewComposePreviewPanelState.GalleryLoading(
                                            message = "Rendering ${pendingSelections.size} " +
                                                "remaining previews…",
                                            previousResult = PreviewGalleryResult(
                                                items = ordered(
                                                    cachedItems + renderedGalleryItems,
                                                ),
                                                failures = failures.toList(),
                                                pendingSelections = pendingSelections.toList(),
                                            ),
                                        ),
                                    )
                                },
                            ),
                            onOutcome = ::consume,
                        )
                    }
                    galleryCache?.prune()
                    val items = ordered(cachedItems + renderedGalleryItems)
                    publish(
                        generation = generation,
                        state = ViewComposePreviewPanelState.Gallery(
                            PreviewGalleryResult(
                                items = items,
                                failures = failures.toList(),
                                pendingSelections = emptyList(),
                            ),
                        ),
                    )
                } catch (cancelled: ProcessCanceledException) {
                    throw cancelled
                } catch (error: Throwable) {
                    publish(
                        generation = generation,
                        state = ViewComposePreviewPanelState.GalleryFailed(
                            error.message ?: error::class.java.simpleName,
                        ),
                    )
                } finally {
                    ownedPriorityOrder?.let { order ->
                        galleryPriorityOrder.compareAndSet(order, null)
                    }
                    activeIndicator.compareAndSet(indicator, null)
                }
            }

            override fun onCancel() {
                publish(
                    generation = generation,
                    state = previous
                        ?.let(ViewComposePreviewPanelState::Gallery)
                        ?: ViewComposePreviewPanelState.GalleryFailed(
                            "Preview gallery loading was cancelled.",
                        ),
                )
            }
        }.queue()
    }

    private fun scheduleGalleryDiscoveryRetry(
        generation: Long,
        forceRerender: Boolean,
        nextAttempt: Int,
        delayMillis: Int,
    ) {
        galleryDiscoveryRetryAlarm.addRequest(
            {
                if (project.isDisposed || generation != requestGeneration.get()) {
                    return@addRequest
                }
                DumbService.getInstance(project).runWhenSmart {
                    if (!project.isDisposed && generation == requestGeneration.get()) {
                        showGallery(
                            forceRerender = forceRerender,
                            discoveryAttempt = nextAttempt,
                        )
                    }
                }
            },
            delayMillis,
        )
    }

    private fun scheduleSavedInputRefresh(
        request: ActivePreviewRequest,
        refreshPolicy: PreviewRefreshPolicy,
    ) {
        require(refreshPolicy.automatic)
        pendingSavedRefreshPolicy.updateAndGet { pending ->
            when {
                pending == PreviewRefreshPolicy.SavedInput -> pending
                refreshPolicy == PreviewRefreshPolicy.SavedInput -> refreshPolicy
                else -> refreshPolicy
            }
        }
        savedInputRefreshAlarm.cancelAllRequests()
        savedInputRefreshAlarm.addRequest(
            {
                val pendingPolicy = pendingSavedRefreshPolicy.getAndSet(null)
                    ?: refreshPolicy
                val shouldRefresh = shouldRunAutomaticPreviewRefresh(
                    projectDisposed = project.isDisposed,
                    activeRequestMatches = request == activeRequest.get(),
                    toolWindowVisible = isPreviewToolWindowVisible(),
                )
                if (shouldRefresh) {
                    render(
                        selection = request.selection,
                        requestedVariantId = request.variantId,
                        refreshPolicy = pendingPolicy,
                    )
                }
            },
            SAVED_INPUT_REFRESH_DELAY_MILLIS,
        )
    }

    private fun scheduleEditorFollow(editorHint: Editor? = null) {
        editorFollowAlarm.cancelAllRequests()
        val generation = editorFollowGeneration.incrementAndGet()
        if (!settings.followEditor) return
        editorFollowAlarm.addRequest(
            {
                if (project.isDisposed || !isPreviewToolWindowVisible()) {
                    return@addRequest
                }
                DumbService.getInstance(project).runWhenSmart {
                    ApplicationManager.getApplication().invokeLater {
                        if (
                            project.isDisposed ||
                            generation != editorFollowGeneration.get() ||
                            !isPreviewToolWindowVisible()
                        ) {
                            return@invokeLater
                        }
                        val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            ?: editorHint
                            ?: return@invokeLater
                        if (editor.project != project || editor.isDisposed) return@invokeLater
                        val documentManager = PsiDocumentManager.getInstance(project)
                        documentManager.commitDocument(editor.document)
                        val followTarget = ApplicationManager.getApplication()
                            .runReadAction<EditorFollowTarget?> {
                                val file = documentManager.getPsiFile(editor.document)
                                    ?: return@runReadAction null
                                val caretOffset = editor.caretModel.offset
                                val lineCandidates = linkedSetOf(
                                    editor.document.getLineNumber(caretOffset) + 1,
                                )
                                if (file.textLength > 0) {
                                    val elementOffset = caretOffset.coerceIn(0, file.textLength - 1)
                                    generateSequence(file.findElementAt(elementOffset)) { element ->
                                        element.parent
                                    }
                                        .filterIsInstance<KtCallExpression>()
                                        .firstOrNull()
                                        ?.textRange
                                        ?.startOffset
                                        ?.let { callOffset ->
                                            lineCandidates +=
                                                editor.document.getLineNumber(callOffset) + 1
                                        }
                                }
                                EditorFollowTarget(
                                    selection = file.previewSelectionNearestToOffset(caretOffset),
                                    sourceLocation = file.virtualFile?.path?.let { filePath ->
                                        EditorSourceLocation(
                                            filePath = filePath,
                                            lineCandidates = lineCandidates.toList(),
                                        )
                                    },
                                )
                            }
                            ?: return@invokeLater
                        followTarget.sourceLocation?.let { source ->
                            attachedPanel?.selectSourceLocation(
                                filePath = source.filePath,
                                lineCandidates = source.lineCandidates,
                            )
                        }
                        val selection = followTarget.selection
                            ?: return@invokeLater
                        val current = activeRequest.get()
                        if (current?.selection == selection) return@invokeLater
                        render(selection = selection, requestedVariantId = null)
                    }
                }
            },
            EDITOR_FOLLOW_DELAY_MILLIS,
        )
    }

    private fun isPreviewToolWindowVisible(): Boolean {
        return ToolWindowManager.getInstance(project)
            .getToolWindow(VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID)
            ?.isVisible == true
    }

    private fun renderCoordinator(root: Path): ViewComposePreviewRenderCoordinator {
        val executor = gradleExecutor
        return if (executor == null) {
            ViewComposePreviewRenderCoordinator(root)
        } else {
            ViewComposePreviewRenderCoordinator(root, executor)
        }
    }

    private fun logPerformance(result: PreviewRenderOutcome.Success) {
        val phases = result.performanceTrace.phases.joinToString(",") { phase ->
            "${phase.phase}=${phase.durationMillis}${if (phase.shared) "s" else ""}"
        }
        logger.info(
            "ViewCompose preview performance: descriptor=${result.descriptorId}, " +
                "variant=${result.selectedVariantId}, cache=${result.cacheHit}, phases=[$phases]",
        )
    }

    override fun dispose() {
        requestGeneration.incrementAndGet()
        automaticRefreshGate.clear()
        pendingSavedRefreshPolicy.set(null)
        activeIndicator.getAndSet(null)?.cancel()
        activeRequest.set(null)
        gradleExecutor?.close()
        attachedPanel = null
        currentState.getAndSet(ViewComposePreviewPanelState.Empty).releaseImages()
    }

    private fun publish(
        generation: Long,
        state: ViewComposePreviewPanelState,
    ) {
        if (generation != requestGeneration.get()) return
        currentState.set(state)
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && generation == requestGeneration.get()) {
                attachedPanel?.showState(state)
            }
        }
    }
}

internal enum class PreviewRefreshPolicy(
    val useStudioCache: Boolean,
    val forceGradleRerender: Boolean,
    val useKnownDescriptor: Boolean,
    val forceFullRebuild: Boolean = false,
    val automatic: Boolean = false,
    val fastGradleRefresh: Boolean = false,
) {
    Open(
        useStudioCache = true,
        forceGradleRerender = false,
        useKnownDescriptor = false,
    ),
    Variant(
        useStudioCache = true,
        forceGradleRerender = false,
        useKnownDescriptor = true,
    ),
    SavedInput(
        useStudioCache = false,
        forceGradleRerender = false,
        useKnownDescriptor = true,
        automatic = true,
    ),
    SavedSourceInput(
        useStudioCache = false,
        forceGradleRerender = false,
        useKnownDescriptor = true,
        automatic = true,
        fastGradleRefresh = true,
    ),
    Manual(
        useStudioCache = false,
        forceGradleRerender = true,
        useKnownDescriptor = true,
    ),
    Full(
        useStudioCache = false,
        forceGradleRerender = true,
        useKnownDescriptor = true,
        forceFullRebuild = true,
    ),
}

private fun ViewComposePreviewPanelState.previousSuccessOrNull(): PreviewRenderOutcome.Success? {
    return when (this) {
        is ViewComposePreviewPanelState.Rendered -> result
        is ViewComposePreviewPanelState.Loading -> previousResult
        else -> null
    }
}

private fun ViewComposePreviewPanelState.releaseImages() {
    when (this) {
        ViewComposePreviewPanelState.Empty,
        is ViewComposePreviewPanelState.Failed,
        is ViewComposePreviewPanelState.GalleryFailed,
        -> Unit

        is ViewComposePreviewPanelState.Loading -> previousResult?.image?.flush()
        is ViewComposePreviewPanelState.Rendered -> result.image.flush()
        is ViewComposePreviewPanelState.GalleryLoading -> {
            previousResult?.items?.forEach(PreviewGalleryItem::releaseThumbnail)
        }
        is ViewComposePreviewPanelState.Gallery -> {
            result.items.forEach(PreviewGalleryItem::releaseThumbnail)
        }
    }
}

internal fun galleryDiscoveryRetryDelayMillis(attempt: Int): Int? {
    require(attempt >= 0)
    return GALLERY_DISCOVERY_RETRY_DELAYS_MILLIS.getOrNull(attempt)
}

internal fun savedPreviewInputMatches(
    projectRoot: Path?,
    selection: PreviewSourceSelection,
    changedPaths: List<String>,
    inputScope: PreviewInputScope? = null,
): Boolean {
    val scope = inputScope ?: PreviewInputScope.forSelection(projectRoot, selection)
    return scope?.matches(selection, changedPaths)
        ?: changedPaths.any { changedPath ->
            changedPath.normalizedPathOrNull()?.toString() ==
                selection.filePath.normalizedPathOrNull()?.toString()
        }
}

internal fun savedPreviewFastRefreshEligible(
    selection: PreviewSourceSelection,
    changedPaths: List<String>,
): Boolean {
    val selected = selection.filePath.normalizedPathOrNull() ?: return false
    if (selected.previewSourceExtension() !in FAST_REFRESH_SOURCE_EXTENSIONS) return false
    val changed = changedPaths.mapNotNull(String::normalizedPathOrNull)
    return changed.isNotEmpty() && changed.all { path -> path == selected }
}

internal fun shouldRunAutomaticPreviewRefresh(
    projectDisposed: Boolean,
    activeRequestMatches: Boolean,
    toolWindowVisible: Boolean,
): Boolean = !projectDisposed && activeRequestMatches && toolWindowVisible

private data class ActivePreviewRequest(
    val selection: PreviewSourceSelection,
    val variantId: String?,
)

private data class AutomaticPreviewRefreshRequest(
    val request: ActivePreviewRequest,
    val refreshPolicy: PreviewRefreshPolicy,
)

private data class EditorFollowTarget(
    val selection: PreviewSourceSelection?,
    val sourceLocation: EditorSourceLocation?,
)

private data class EditorSourceLocation(
    val filePath: String,
    val lineCandidates: List<Int>,
)

private const val EDITOR_FOLLOW_DELAY_MILLIS = 250
private const val SAVED_INPUT_REFRESH_DELAY_MILLIS = 400
private const val GALLERY_INITIAL_RENDER_COUNT = 6
private const val NANOS_PER_MILLISECOND = 1_000_000L
private val GALLERY_DISCOVERY_RETRY_DELAYS_MILLIS = intArrayOf(500, 1_000, 2_000)
private val FAST_REFRESH_SOURCE_EXTENSIONS = setOf("java", "kt")

private fun Path.previewSourceExtension(): String = fileName
    ?.toString()
    ?.substringAfterLast('.', missingDelimiterValue = "")
    ?.lowercase()
    .orEmpty()

private fun elapsedMillis(startedAtNanos: Long): Long {
    return ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
        .coerceAtLeast(0L)
}
