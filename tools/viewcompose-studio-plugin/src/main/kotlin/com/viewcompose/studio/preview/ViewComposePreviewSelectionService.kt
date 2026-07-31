package com.viewcompose.studio.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

    @Volatile
    private var attachedPanel: ViewComposePreviewToolWindowPanel? = null

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val request = activeRequest.get() ?: return
                    if (!savedSourceMatches(request.selection, events.map { event -> event.path })) {
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && request == activeRequest.get()) {
                            render(
                                selection = request.selection,
                                requestedVariantId = request.variantId,
                            )
                        }
                    }
                }
            },
        )
    }

    fun attach(panel: ViewComposePreviewToolWindowPanel) {
        attachedPanel = panel
        panel.showState(currentState.get())
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

    private fun render(
        selection: PreviewSourceSelection,
        requestedVariantId: String?,
    ) {
        activeRequest.set(
            ActivePreviewRequest(
                selection = selection,
                variantId = requestedVariantId,
            ),
        )
        val generation = requestGeneration.incrementAndGet()
        activeIndicator.getAndSet(null)?.cancel()
        publish(
            generation = generation,
            state = ViewComposePreviewPanelState.Loading(
                selection = selection,
                message = "Preparing static preview…",
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
                    val outcome = if (root == null) {
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
                            indicator = indicator,
                            onProgress = { message ->
                                publish(
                                    generation = generation,
                                    state = ViewComposePreviewPanelState.Loading(
                                        selection = selection,
                                        message = message,
                                    ),
                                )
                            },
                        )
                    }
                    if (outcome is PreviewRenderOutcome.Success &&
                        generation == requestGeneration.get()
                    ) {
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

internal fun savedSourceMatches(
    selection: PreviewSourceSelection,
    changedPaths: List<String>,
): Boolean {
    val selectedPath = selection.filePath.normalizedPathOrNull() ?: return false
    return changedPaths.any { changedPath ->
        changedPath.normalizedPathOrNull() == selectedPath
    }
}

private fun String.normalizedPathOrNull(): Path? {
    return runCatching { Path.of(this).toAbsolutePath().normalize() }.getOrNull()
}

private data class ActivePreviewRequest(
    val selection: PreviewSourceSelection,
    val variantId: String?,
)
