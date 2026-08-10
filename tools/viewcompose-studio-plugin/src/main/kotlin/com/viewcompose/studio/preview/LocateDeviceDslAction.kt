package com.viewcompose.studio.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class LocateDeviceDslAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val messages = project?.deviceDslMessages()
        event.presentation.isEnabled = project != null
        event.presentation.isVisible = project != null
        if (messages != null) {
            event.presentation.text = messages.text("action.locateDeviceDsl")
            event.presentation.description = messages.text("action.locateDeviceDsl.description")
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messages = project.deviceDslMessages()
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
                val selected = chooseDevice(project, messages, devices)
                if (selected != null) {
                    locateSource(project, messages, selected)
                }
            }

            override fun onThrowable(error: Throwable) {
                showDeviceDslFailure(project, messages, error)
            }
        }.queue()
    }
}

private fun chooseDevice(
    project: Project,
    messages: PreviewUiMessages,
    devices: List<StudioAndroidDevice>,
): StudioAndroidDevice? {
    if (devices.isEmpty()) {
        showDeviceDslFailure(
            project,
            messages,
            DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoDevice),
        )
        return null
    }
    if (devices.size == 1) return devices.single()
    return DeviceDslChoiceDialog(
        project = project,
        dialogTitle = messages.text("deviceDsl.deviceDialog.title"),
        description = messages.text("deviceDsl.deviceDialog.description"),
        choices = devices.map { device ->
            DeviceDslChoice(device, device.presentableChoice(messages))
        },
    ).selectedChoice()
}

private fun locateSource(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.source"),
        true,
    ) {
        private var sources: List<ResolvedDeviceDslSource> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            val sessions = readDeviceDslSourceReport(device).visibleSourceSessions()
            if (sessions.isEmpty()) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoVisibleDsl)
            }
            val resolver = StudioPreviewSourceResolver(project)
            sources = sessions
                .flatMap { session ->
                    indicator.checkCanceled()
                    resolver.resolveCandidates(session.sourceCandidates)
                }
                .map(::ResolvedDeviceDslSource)
                .distinctBy { source ->
                    source.location.filePath to source.location.line
                }
            if (sources.isEmpty()) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.SourceMissing)
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            chooseSource(project, messages, sources)?.let(project::navigateToSource)
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

private fun chooseSource(
    project: Project,
    messages: PreviewUiMessages,
    sources: List<ResolvedDeviceDslSource>,
): StudioPreviewSourceLocation? {
    if (sources.size == 1) return sources.single().location
    val projectRoot = project.basePath?.let(Path::of)
    val choices = sources.map { source ->
        val location = source.location
        DeviceDslChoice(
            value = location,
            label = buildString {
                append(presentableProjectPath(projectRoot, location.filePath))
                append(':')
                append(location.line)
                location.symbolName?.takeIf(String::isNotBlank)?.let { symbol ->
                    append(" — ")
                    append(symbol)
                }
            },
        )
    }
    return DeviceDslChoiceDialog(
        project = project,
        dialogTitle = messages.text("deviceDsl.sourceDialog.title"),
        description = messages.text("deviceDsl.sourceDialog.description"),
        choices = choices,
    ).selectedChoice()
}

private fun StudioAndroidDevice.presentableChoice(messages: PreviewUiMessages): String {
    val deviceKind = messages.text(
        if (emulator) "deviceDsl.device.emulator" else "deviceDsl.device.physical",
    )
    return buildString {
        append(displayName)
        append(" — ")
        append(deviceKind)
        androidVersion?.let { version ->
            append(" · Android ")
            append(version)
        }
        append(" · ")
        append(serialNumber)
    }
}

private fun showDeviceDslFailure(
    project: Project,
    messages: PreviewUiMessages,
    error: Throwable,
) {
    if (project.isDisposed) return
    val failure = error as? DeviceDslLocateFailure
    val messageKey = when (failure?.reason) {
        DeviceDslLocateFailureReason.NoDevice -> "deviceDsl.failure.noDevice"
        DeviceDslLocateFailureReason.ForegroundAppMissing -> "deviceDsl.failure.foregroundApp"
        DeviceDslLocateFailureReason.ReportUnavailable -> "deviceDsl.failure.notDebuggable"
        DeviceDslLocateFailureReason.StaleReport -> "deviceDsl.failure.staleReport"
        DeviceDslLocateFailureReason.NoVisibleDsl -> "deviceDsl.failure.noVisibleDsl"
        DeviceDslLocateFailureReason.SourceMissing -> "deviceDsl.failure.sourceMissing"
        null -> null
    }
    val details = messageKey?.let(messages::text) ?: messages.text(
        "deviceDsl.failure.adb",
        error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName,
    )
    Messages.showWarningDialog(
        project,
        details,
        messages.text("deviceDsl.failure.title"),
    )
}

private fun Project.deviceDslMessages(): PreviewUiMessages {
    val language = ViewComposePreviewSettings.forProject(this).language
    return PreviewUiMessages.forLanguage(language)
}

private data class ResolvedDeviceDslSource(
    val location: StudioPreviewSourceLocation,
)

private data class DeviceDslChoice<T>(
    val value: T,
    val label: String,
) {
    override fun toString(): String = label
}

private class DeviceDslChoiceDialog<T>(
    project: Project,
    dialogTitle: String,
    private val description: String,
    private val choices: List<DeviceDslChoice<T>>,
) : DialogWrapper(project) {
    private val choiceList = JBList(choices).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        selectedIndex = 0
        visibleRowCount = choices.size.coerceIn(2, 8)
    }

    init {
        title = dialogTitle
        init()
    }

    fun selectedChoice(): T? {
        if (!showAndGet()) return null
        return choiceList.selectedValue?.value
    }

    override fun createCenterPanel(): JComponent {
        return javax.swing.JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 0)
            add(JBLabel(description), BorderLayout.NORTH)
            add(
                JBScrollPane(choiceList).apply {
                    border = JBUI.Borders.emptyTop(8)
                    preferredSize = Dimension(JBUI.scale(520), JBUI.scale(140))
                },
                BorderLayout.CENTER,
            )
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = choiceList
}
