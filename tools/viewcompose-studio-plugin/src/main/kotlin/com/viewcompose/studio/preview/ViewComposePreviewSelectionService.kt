package com.viewcompose.studio.preview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
internal class ViewComposePreviewSelectionService(
    private val project: Project,
) {
    private val currentState = AtomicReference<ViewComposePreviewPanelState>(
        ViewComposePreviewPanelState.Empty,
    )
    private val requestGeneration = AtomicLong(0)
    private val activeIndicator = AtomicReference<ProgressIndicator?>()

    @Volatile
    private var attachedPanel: ViewComposePreviewToolWindowPanel? = null

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
        val generation = requestGeneration.incrementAndGet()
        activeIndicator.getAndSet(null)?.cancel()
        publish(
            generation = generation,
            state = ViewComposePreviewPanelState.Loading(
                selection = selection,
                message = "Preparing static preview…",
            ),
        )
        ToolWindowManager.getInstance(project).invokeLater {
            ToolWindowManager.getInstance(project)
                .getToolWindow(VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID)
                ?.show()
        }
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
