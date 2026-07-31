package com.viewcompose.studio.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.nio.file.Path

internal const val VIEWCOMPOSE_PREVIEW_TOOL_WINDOW_ID = "ViewCompose Preview"

class ViewComposePreviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean {
        return project.viewComposeDetection().isViewComposeProject
    }

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val selectionService = project.service<ViewComposePreviewSelectionService>()
        val projectRoot = project.basePath?.let(Path::of)
        val settings = ViewComposePreviewSettings.forProject(project)
        val panel = ViewComposePreviewToolWindowPanel(
            detection = project.viewComposeDetection(),
            projectRoot = projectRoot,
            initialLanguage = settings.language,
            onVariantSelected = selectionService::selectVariant,
            onNavigateToSource = { source -> project.navigateToSource(source) },
        )
        fun refreshOptionsActions() {
            toolWindow.setAdditionalGearActions(
                createPreviewOptionsActionGroup(settings.language) { language ->
                    if (settings.language == language) return@createPreviewOptionsActionGroup
                    settings.language = language
                    panel.setLanguage(language)
                    refreshOptionsActions()
                },
            )
        }
        refreshOptionsActions()
        selectionService.attach(panel)
        val content = ContentFactory.getInstance().createContent(
            panel,
            null,
            false,
        )
        content.setDisposer(
            Disposable {
                selectionService.detach(panel)
            },
        )
        content.preferredFocusableComponent = panel.preferredFocusComponent
        toolWindow.contentManager.addContent(content)
    }
}

private fun Project.navigateToSource(source: StudioPreviewSourceLocation) {
    val path = runCatching { Path.of(source.filePath).toAbsolutePath().normalize() }
        .getOrNull()
        ?: return
    val file = VfsUtil.findFile(path, true) ?: return
    OpenFileDescriptor(
        this,
        file,
        (source.line - 1).coerceAtLeast(0),
        (source.column - 1).coerceAtLeast(0),
    ).navigate(true)
}

private fun Project.viewComposeDetection(): ViewComposeProjectDetection {
    getUserData(VIEWCOMPOSE_DETECTION_KEY)?.let { detection -> return detection }
    val root = basePath?.let(Path::of)
    return ViewComposeProjectDetector().detect(root).also { detection ->
        putUserData(VIEWCOMPOSE_DETECTION_KEY, detection)
    }
}

private val VIEWCOMPOSE_DETECTION_KEY =
    Key.create<ViewComposeProjectDetection>("viewcompose.preview.projectDetection")
