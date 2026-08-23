package com.viewcompose.studio.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/** Highlights one currently mounted ViewCompose node on a connected debuggable device. */
class HighlightDeviceDslNodeAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val messages = project?.deviceDslMessages()
        event.presentation.isEnabled = project != null
        event.presentation.isVisible = project != null
        if (messages != null) {
            event.presentation.text = messages.text("action.highlightDeviceDslNode")
            event.presentation.description = messages.text("action.highlightDeviceDslNode.description")
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messages = project.deviceDslMessages()
        loadDevice(project, messages) { device -> loadHighlightSessions(project, messages, device) }
    }
}

/** Clears the one process-bounded ViewCompose node highlight on a connected device. */
class ClearDeviceDslHighlightAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val messages = project?.deviceDslMessages()
        event.presentation.isEnabled = project != null
        event.presentation.isVisible = project != null
        if (messages != null) {
            event.presentation.text = messages.text("action.clearDeviceDslHighlight")
            event.presentation.description = messages.text("action.clearDeviceDslHighlight.description")
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messages = project.deviceDslMessages()
        loadDevice(project, messages) { device ->
            object : Task.Backgroundable(
                project,
                messages.text("deviceDsl.progress.clearHighlight"),
                true,
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.checkCanceled()
                    val result = clearDeviceDslHighlight(device)
                    if (result.state != StudioDeviceDslHighlightState.Cleared) {
                        throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.HighlightRejected)
                    }
                }

                override fun onThrowable(error: Throwable) {
                    showDeviceDslFailure(project, messages, error)
                }
            }.queue()
        }
    }
}

private fun loadDevice(
    project: Project,
    messages: PreviewUiMessages,
    onSelected: (StudioAndroidDevice) -> Unit,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.devices"),
        true,
    ) {
        private var devices: List<StudioAndroidDevice> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            devices = DeviceDslAdbBridge(project).onlineDevices()
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            chooseDevice(project, messages, devices)?.let(onSelected)
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

private fun loadHighlightSessions(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.nodes"),
        true,
    ) {
        private var sessions: List<StudioDeviceDslSourceSession> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            sessions = readDeviceDslSourceReport(device).visibleSourceSessions()
            if (sessions.isEmpty()) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoVisibleDsl)
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            chooseHighlightSession(project, messages, sessions)?.let { session ->
                loadHighlightNodes(project, messages, device, session.sessionId)
            }
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

private fun chooseHighlightSession(
    project: Project,
    messages: PreviewUiMessages,
    sessions: List<StudioDeviceDslSourceSession>,
): StudioDeviceDslSourceSession? {
    if (sessions.size == 1) return sessions.single()
    return DeviceDslChoiceDialog(
        project = project,
        dialogTitle = messages.text("deviceDsl.sessionDialog.title"),
        description = messages.text("deviceDsl.sessionDialog.description"),
        choices = sessions.map { session ->
            DeviceDslChoice(
                value = session,
                label = "${session.role} · session ${session.sessionId}",
            )
        },
    ).selectedChoice()
}

private fun loadHighlightNodes(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
    sessionId: Long,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.nodes"),
        true,
    ) {
        private var nodes: List<StudioDeviceDslNode> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            val report = readDeviceDslNodeReport(device, sessionId)
            val session = report.sessions.singleOrNull()
                ?: throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoInspectableNode)
            if (!session.nodeInspectionSupported || session.nodeInspectionEnded) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoInspectableNode)
            }
            nodes = session.nodes.filterNot(StudioDeviceDslNode::synthetic)
            if (nodes.isEmpty()) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoInspectableNode)
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            chooseHighlightNode(project, messages, nodes)?.let { node ->
                selectHighlightNode(project, messages, device, sessionId, node.token)
            }
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

private fun chooseHighlightNode(
    project: Project,
    messages: PreviewUiMessages,
    nodes: List<StudioDeviceDslNode>,
): StudioDeviceDslNode? {
    return DeviceDslChoiceDialog(
        project = project,
        dialogTitle = messages.text("deviceDsl.nodeDialog.title"),
        description = messages.text("deviceDsl.nodeDialog.description"),
        choices = nodes.map { node ->
            val source = node.sourceCallSites.firstOrNull()
            val sourceSuffix = source?.let { " · ${it.fileName}:${it.lineNumber}" }.orEmpty()
            DeviceDslChoice(
                value = node,
                label = "  ".repeat(node.depth.coerceAtMost(12)) + node.type + sourceSuffix,
            )
        },
    ).selectedChoice()
}

private fun selectHighlightNode(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
    sessionId: Long,
    nodeToken: String,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.highlight"),
        true,
    ) {
        private var result: StudioDeviceDslHighlightResult? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            result = selectDeviceDslNode(device, sessionId, nodeToken)
            if (result?.state !in setOf(
                    StudioDeviceDslHighlightState.Selected,
                    StudioDeviceDslHighlightState.Clipped,
                )
            ) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.HighlightRejected)
            }
        }

        override fun onSuccess() {
            if (project.isDisposed || result?.state != StudioDeviceDslHighlightState.Clipped) return
            Messages.showInfoMessage(
                project,
                messages.text("deviceDsl.highlight.clipped"),
                messages.text("deviceDsl.highlight.title"),
            )
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}
