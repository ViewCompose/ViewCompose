package com.viewcompose.studio.preview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
internal class ViewComposePreviewSelectionService(
    private val project: Project,
) {
    private val selected = AtomicReference<PreviewSourceSelection?>()

    @Volatile
    private var attachedPanel: ViewComposePreviewToolWindowPanel? = null

    fun attach(panel: ViewComposePreviewToolWindowPanel) {
        attachedPanel = panel
        panel.showSelection(selected.get())
    }

    fun detach(panel: ViewComposePreviewToolWindowPanel) {
        if (attachedPanel === panel) {
            attachedPanel = null
        }
    }

    fun selectAndShow(selection: PreviewSourceSelection) {
        selected.set(selection)
        ToolWindowManager.getInstance(project).invokeLater {
            attachedPanel?.showSelection(selection)
            ToolWindowManager.getInstance(project)
                .getToolWindow(VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID)
                ?.show()
        }
    }
}
