package com.viewcompose.studio.preview

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.nio.file.Path

class ViewComposePreviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean {
        return project.viewComposeDetection().isViewComposeProject
    }

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = ViewComposePreviewToolWindowPanel(
            detection = project.viewComposeDetection(),
        )
        val content = ContentFactory.getInstance().createContent(
            panel,
            null,
            false,
        )
        content.preferredFocusableComponent = panel.preferredFocusComponent
        toolWindow.contentManager.addContent(content)
    }
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
