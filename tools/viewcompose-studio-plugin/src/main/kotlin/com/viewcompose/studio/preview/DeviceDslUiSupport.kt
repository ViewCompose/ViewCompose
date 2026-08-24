package com.viewcompose.studio.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal fun chooseDevice(
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

internal fun showDeviceDslFailure(
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
        DeviceDslLocateFailureReason.NoInspectableNode -> "deviceDsl.failure.noInspectableNode"
        DeviceDslLocateFailureReason.HighlightRejected -> "deviceDsl.failure.highlightRejected"
        DeviceDslLocateFailureReason.TimingRejected -> "deviceDsl.failure.timingRejected"
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

internal fun Project.deviceDslMessages(): PreviewUiMessages {
    val language = ViewComposePreviewSettings.forProject(this).language
    return PreviewUiMessages.forLanguage(language)
}

internal data class DeviceDslChoice<T>(
    val value: T,
    val label: String,
) {
    override fun toString(): String = label
}

internal class DeviceDslChoiceDialog<T>(
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
