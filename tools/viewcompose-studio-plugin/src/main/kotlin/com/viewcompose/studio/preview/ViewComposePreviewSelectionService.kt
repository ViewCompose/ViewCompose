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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
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
    private val activeIndicator = AtomicReference<ProgressIndicator?>()
    private val activeRequest = AtomicReference<ActivePreviewRequest?>()
    private val savedInputRefreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val editorFollowAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val settings = ViewComposePreviewSettings.forProject(project)
    private val cacheRoot = project.basePath
        ?.let { previewCacheRoot(Path.of(PathManager.getSystemPath())) }
    private val diskCache = cacheRoot?.resolve("detail")?.let(::PreviewDiskCache)
    private val galleryCache = cacheRoot?.resolve("gallery")?.let(::PreviewGalleryDiskCache)

    @Volatile
    private var attachedPanel: ViewComposePreviewToolWindowPanel? = null

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (!settings.autoRefreshOnSave) return
                    val request = activeRequest.get() ?: return
                    if (
                        !savedPreviewInputMatches(
                            projectRoot = project.basePath?.let(Path::of),
                            selection = request.selection,
                            changedPaths = events.map(VFileEvent::getPath),
                        )
                    ) {
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && request == activeRequest.get()) {
                            scheduleSavedInputRefresh(request)
                        }
                    }
                }
            },
        )
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    scheduleEditorFollow(
                        FileEditorManager.getInstance(project).selectedTextEditor,
                    )
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
        scheduleEditorFollow(FileEditorManager.getInstance(project).selectedTextEditor)
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
            forceRerender = true,
        )
    }

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
        scheduleEditorFollow(FileEditorManager.getInstance(project).selectedTextEditor)
    }

    private fun render(
        selection: PreviewSourceSelection,
        requestedVariantId: String?,
        forceRerender: Boolean = false,
    ) {
        activeRequest.set(
            ActivePreviewRequest(
                selection = selection,
                variantId = requestedVariantId,
            ),
        )
        val generation = requestGeneration.incrementAndGet()
        activeIndicator.getAndSet(null)?.cancel()
        val previousResult = (currentState.get() as? ViewComposePreviewPanelState.Rendered)
            ?.result
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
                    val cached = if (!forceRerender) {
                        diskCache?.read(selection, requestedVariantId)
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
                        ViewComposePreviewRenderCoordinator(root).render(
                            selection = selection,
                            requestedVariantId = requestedVariantId,
                            forceRerender = forceRerender,
                            indicator = indicator,
                            onProgress = { message ->
                                publish(
                                    generation = generation,
                                    state = ViewComposePreviewPanelState.Loading(
                                        selection = selection,
                                        message = message,
                                        previousResult = previousResult,
                                    ),
                                )
                            },
                        )
                    }
                    if (outcome is PreviewRenderOutcome.Success &&
                        generation == requestGeneration.get()
                    ) {
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
            }
        }.queue()
    }

    private fun showGallery(forceRerender: Boolean = false) {
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
                try {
                    val selections = ViewComposePreviewProjectScanner(project).scan()
                    val cachedItems = if (forceRerender) {
                        emptyList()
                    } else {
                        galleryCache?.readAll(selections).orEmpty()
                    }
                    val cachedBySelection = cachedItems.groupBy(PreviewGalleryItem::selection)
                    val missing = selections.filterNot(cachedBySelection::containsKey)
                    fun ordered(items: Collection<PreviewGalleryItem>): List<PreviewGalleryItem> {
                        return items.sortedWith(
                            compareBy(
                                { item -> selections.indexOf(item.selection) },
                                PreviewGalleryItem::variantIndex,
                                PreviewGalleryItem::variantId,
                            ),
                        )
                    }
                    if (cachedItems.isNotEmpty()) {
                        publish(
                            generation = generation,
                            state = ViewComposePreviewPanelState.GalleryLoading(
                                message = "Rendering ${missing.size} uncached previews…",
                                previousResult = PreviewGalleryResult(
                                    items = ordered(cachedItems),
                                    failures = emptyList(),
                                ),
                            ),
                        )
                    }
                    val root = project.basePath?.let(Path::of)
                    val renderedGalleryItems = mutableListOf<PreviewGalleryItem>()
                    val failures = mutableListOf<PreviewRenderOutcome.Failure>()
                    fun consume(outcome: PreviewRenderOutcome) {
                        when (outcome) {
                            is PreviewRenderOutcome.Success -> {
                                val item = runCatching {
                                    galleryCache?.write(outcome)
                                }.getOrNull() ?: outcome.toBoundedGalleryItem()
                                renderedGalleryItems += item
                            }
                            is PreviewRenderOutcome.Failure -> failures += outcome
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
                        ViewComposePreviewRenderCoordinator(root).renderAllEach(
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
                                        ),
                                    ),
                                )
                            },
                            onOutcome = ::consume,
                        )
                    }
                    galleryCache?.prune()
                    val items = ordered(cachedItems + renderedGalleryItems)
                    publish(
                        generation = generation,
                        state = ViewComposePreviewPanelState.Gallery(
                            PreviewGalleryResult(items = items, failures = failures.toList()),
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

    private fun scheduleSavedInputRefresh(request: ActivePreviewRequest) {
        savedInputRefreshAlarm.cancelAllRequests()
        savedInputRefreshAlarm.addRequest(
            {
                if (!project.isDisposed && request == activeRequest.get()) {
                    render(
                        selection = request.selection,
                        requestedVariantId = request.variantId,
                        forceRerender = true,
                    )
                }
            },
            SAVED_INPUT_REFRESH_DELAY_MILLIS,
        )
    }

    private fun scheduleEditorFollow(editor: Editor?) {
        editorFollowAlarm.cancelAllRequests()
        if (!settings.followEditor) return
        if (editor == null || editor.project != project || editor.isDisposed) return
        editorFollowAlarm.addRequest(
            {
                if (
                    project.isDisposed ||
                    editor.isDisposed ||
                    !isPreviewToolWindowVisible()
                ) {
                    return@addRequest
                }
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
                            selection = file.previewSelectionAtOffset(caretOffset),
                            sourceLocation = file.virtualFile?.path?.let { filePath ->
                                EditorSourceLocation(
                                    filePath = filePath,
                                    lineCandidates = lineCandidates.toList(),
                                )
                            },
                        )
                    }
                    ?: return@addRequest
                followTarget.sourceLocation?.let { source ->
                    attachedPanel?.selectSourceLocation(
                        filePath = source.filePath,
                        lineCandidates = source.lineCandidates,
                    )
                }
                val selection = followTarget.selection
                    ?: return@addRequest
                val current = activeRequest.get()
                if (current?.selection == selection) return@addRequest
                render(selection = selection, requestedVariantId = null)
            },
            EDITOR_FOLLOW_DELAY_MILLIS,
        )
    }

    private fun isPreviewToolWindowVisible(): Boolean {
        return ToolWindowManager.getInstance(project)
            .getToolWindow(VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID)
            ?.isVisible == true
    }

    override fun dispose() {
        activeIndicator.getAndSet(null)?.cancel()
        attachedPanel = null
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

internal fun savedPreviewInputMatches(
    projectRoot: Path?,
    selection: PreviewSourceSelection,
    changedPaths: List<String>,
): Boolean {
    val selectedPath = selection.filePath.normalizedPathOrNull() ?: return false
    val normalizedRoot = projectRoot?.toAbsolutePath()?.normalize()
    return changedPaths.any { changedPath ->
        val path = changedPath.normalizedPathOrNull() ?: return@any false
        if (path == selectedPath) return@any true
        if (normalizedRoot == null || !path.startsWith(normalizedRoot)) return@any false
        val relativePath = normalizedRoot.relativize(path)
        if (relativePath.any { segment -> segment.toString() in IGNORED_INPUT_DIRECTORIES }) {
            return@any false
        }
        path.fileName
            ?.toString()
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase() in PREVIEW_INPUT_EXTENSIONS
    }
}

private fun String.normalizedPathOrNull(): Path? {
    return runCatching { Path.of(this).toAbsolutePath().normalize() }.getOrNull()
}

private data class ActivePreviewRequest(
    val selection: PreviewSourceSelection,
    val variantId: String?,
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
private val IGNORED_INPUT_DIRECTORIES = setOf(
    ".git",
    ".gradle",
    ".idea",
    "build",
    "out",
)
private val PREVIEW_INPUT_EXTENSIONS = setOf(
    "gradle",
    "gif",
    "java",
    "jpeg",
    "jpg",
    "json",
    "kt",
    "kts",
    "otf",
    "png",
    "properties",
    "svg",
    "toml",
    "ttf",
    "webp",
    "xml",
)
