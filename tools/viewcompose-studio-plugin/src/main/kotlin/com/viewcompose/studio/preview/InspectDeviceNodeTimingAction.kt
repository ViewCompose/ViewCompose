package com.viewcompose.studio.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.util.Locale

/** Captures one bounded node-timing sample from a connected debuggable Android process. */
class InspectDeviceNodeTimingAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val messages = project?.deviceDslMessages()
        event.presentation.isEnabled = project != null
        event.presentation.isVisible = project != null
        if (messages != null) {
            event.presentation.text = messages.text("action.inspectNodeTiming")
            event.presentation.description = messages.text("action.inspectNodeTiming.description")
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messages = project.deviceDslMessages()
        loadTimingDevice(project, messages) { device ->
            loadTimingSessions(project, messages, device)
        }
    }
}

private fun loadTimingDevice(
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

private fun loadTimingSessions(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.timingSessions"),
        true,
    ) {
        private var sessions: List<StudioDeviceDslSourceSession> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            sessions = readDeviceDslSourceReport(device).visibleTimingSessions()
            if (sessions.isEmpty()) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoVisibleDsl)
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            chooseTimingSession(project, messages, sessions)?.let { session ->
                val decision = Messages.showOkCancelDialog(
                    project,
                    messages.text("deviceDsl.timing.workloadPrompt"),
                    messages.text("deviceDsl.timing.title"),
                    messages.text("deviceDsl.timing.start"),
                    messages.text("deviceDsl.cancel"),
                    Messages.getInformationIcon(),
                )
                if (decision == Messages.OK) {
                    captureNodeTiming(project, messages, device, session.sessionId)
                }
            }
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

private fun chooseTimingSession(
    project: Project,
    messages: PreviewUiMessages,
    sessions: List<StudioDeviceDslSourceSession>,
): StudioDeviceDslSourceSession? {
    if (sessions.size == 1) return sessions.single()
    return DeviceDslChoiceDialog(
        project = project,
        dialogTitle = messages.text("deviceDsl.timing.sessionDialog.title"),
        description = messages.text("deviceDsl.timing.sessionDialog.description"),
        choices = sessions.map { session ->
            DeviceDslChoice(
                value = session,
                label = "${session.role} · session ${session.sessionId}",
            )
        },
    ).selectedChoice()
}

private fun captureNodeTiming(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
    sessionId: Long,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.timing"),
        true,
    ) {
        private var timing: StudioDeviceDslTimingSnapshot? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            timing = readDeviceDslTimingReport(device, sessionId)
            val snapshot = checkNotNull(timing)
            if (
                snapshot.startStatus != StudioDeviceDslTimingStartStatus.Started ||
                snapshot.result?.complete != true
            ) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.TimingRejected)
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            val result = timing?.result ?: return
            Messages.showInfoMessage(
                project,
                result.toTopCostText(messages),
                messages.text("deviceDsl.timing.title"),
            )
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

internal fun StudioDeviceDslTimingResult.toTopCostText(
    messages: PreviewUiMessages,
    limit: Int = 20,
): String {
    val additiveRecords = records.filter { record ->
        record.inclusion == StudioDeviceDslTimingInclusion.Self ||
            record.inclusion == StudioDeviceDslTimingInclusion.Direct
    }
    val top = additiveRecords.sortedWith(
        compareByDescending<StudioDeviceDslTimingRecord>(StudioDeviceDslTimingRecord::durationNanos)
            .thenBy(StudioDeviceDslTimingRecord::frameId)
            .thenBy(StudioDeviceDslTimingRecord::nodeToken),
    ).take(limit)
    return buildString {
        append(messages.text("deviceDsl.timing.summary", completedFrames, records.size))
        append('\n')
        append(messages.text(
            "deviceDsl.timing.overhead",
            emptyPairOverheadNanos,
            attemptedClockReads,
            retainedClockReads,
        ))
        append('\n')
        append(messages.text(
            "deviceDsl.timing.drops",
            droppedTimedNodes,
            droppedRecords,
            droppedStrings,
        ))
        append('\n')
        append(messages.text(
            "deviceDsl.timing.terminal",
            endReason ?: "unknown",
            unsupportedDomains.joinToString().ifEmpty { "none" },
        ))
        if (top.isEmpty()) {
            append("\n\n")
            append(messages.text("deviceDsl.timing.empty"))
            return@buildString
        }
        append("\n\n")
        top.forEachIndexed { index, record ->
            if (index > 0) append('\n')
            val milliseconds = String.format(
                Locale.US,
                "%.3f",
                record.durationNanos / 1_000_000.0,
            )
            val node = record.nodeType ?: messages.text("deviceDsl.timing.scope")
            append(index + 1)
            append(". ")
            append(milliseconds)
            append(" ms · ")
            append(record.phase.wireValue)
            append('/')
            append(record.inclusion.wireValue)
            append(" · ")
            append(node)
            append(" · frame ")
            append(record.frameId)
            if (record.repetitions > 1L) {
                append(" · ×")
                append(record.repetitions)
            }
            record.sourceCallSites.firstOrNull()?.let { source ->
                append(" · ")
                append(source.fileName)
                append(':')
                append(source.lineNumber)
            }
        }
        if (truncated || recordsTruncated) {
            append("\n\n")
            append(messages.text("deviceDsl.timing.truncated"))
        }
    }
}
