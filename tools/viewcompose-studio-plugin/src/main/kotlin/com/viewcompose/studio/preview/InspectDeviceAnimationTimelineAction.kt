package com.viewcompose.studio.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent

class InspectDeviceAnimationTimelineAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val messages = project?.animationTimelineMessages()
        event.presentation.isEnabled = project != null
        event.presentation.isVisible = project != null
        if (messages != null) {
            event.presentation.text = messages.text("action.inspectAnimationTimeline")
            event.presentation.description =
                messages.text("action.inspectAnimationTimeline.description")
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val messages = project.animationTimelineMessages()
        object : Task.Backgroundable(
            project,
            messages.text("animationTimeline.progress.devices"),
            true,
        ) {
            private var devices: List<StudioAndroidDevice> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.checkCanceled()
                devices = DeviceDslAdbBridge(project).onlineDevices()
            }

            override fun onSuccess() {
                if (project.isDisposed) return
                if (devices.isEmpty()) {
                    showAnimationTimelineFailure(
                        project,
                        messages,
                        AnimationTimelineInspectFailure(
                            AnimationTimelineInspectFailureReason.NoDevice,
                        ),
                    )
                    return
                }
                val selected = chooseDevice(project, messages, devices)
                if (selected != null) discoverAnimations(project, messages, selected)
            }

            override fun onThrowable(error: Throwable) {
                showAnimationTimelineFailure(project, messages, error)
            }
        }.queue()
    }
}

private fun discoverAnimations(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
) {
    object : Task.Backgroundable(
        project,
        messages.text("animationTimeline.progress.discover"),
        true,
    ) {
        private var transitions: List<StudioAnimationTimeline> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            val report = readDeviceAnimationTimelineReport(
                device = device,
                mode = ANIMATION_TIMELINE_DISCOVER_MODE,
            )
            transitions = report.transitions
            if (transitions.isEmpty()) {
                throw AnimationTimelineInspectFailure(
                    AnimationTimelineInspectFailureReason.NoAnimation,
                )
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            chooseAnimation(project, messages, transitions)?.let { transition ->
                captureAnimation(project, messages, device, transition)
            }
        }

        override fun onThrowable(error: Throwable) {
            showAnimationTimelineFailure(project, messages, error)
        }
    }.queue()
}

private fun chooseAnimation(
    project: Project,
    messages: PreviewUiMessages,
    transitions: List<StudioAnimationTimeline>,
): StudioAnimationTimeline? {
    val choices = transitions.map { transition ->
        val sample = transition.samples.last()
        DeviceDslChoice(
            value = transition,
            label = buildString {
                append(transition.label.ifBlank { messages.text("animationTimeline.unnamed") })
                append(" — ")
                append(transition.identity)
                append(" · ")
                append(sample.currentState.presentableState())
                append(" → ")
                append(sample.targetState.presentableState())
            },
        )
    }
    if (choices.size == 1) return choices.single().value
    return DeviceDslChoiceDialog(
        project = project,
        dialogTitle = messages.text("animationTimeline.transitionDialog.title"),
        description = messages.text("animationTimeline.transitionDialog.description"),
        choices = choices,
    ).selectedChoice()
}

private fun captureAnimation(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
    transition: StudioAnimationTimeline,
) {
    object : Task.Backgroundable(
        project,
        messages.text("animationTimeline.progress.capture"),
        true,
    ) {
        private var captured: StudioAnimationTimeline? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            val report = readDeviceAnimationTimelineReport(
                device = device,
                mode = ANIMATION_TIMELINE_CAPTURE_MODE,
                transitionId = transition.identity,
            )
            when (report.status) {
                "success" -> captured = report.transitions.singleOrNull()
                    ?: throw AnimationTimelineInspectFailure(
                        AnimationTimelineInspectFailureReason.MissingTransition,
                    )
                "missing" -> throw AnimationTimelineInspectFailure(
                    AnimationTimelineInspectFailureReason.MissingTransition,
                )
                "busy" -> throw AnimationTimelineInspectFailure(
                    AnimationTimelineInspectFailureReason.Busy,
                )
                else -> throw AnimationTimelineInspectFailure(
                    AnimationTimelineInspectFailureReason.StaleReport,
                )
            }
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            AnimationTimelineReportDialog(
                project = project,
                messages = messages,
                timeline = checkNotNull(captured),
            ).show()
        }

        override fun onThrowable(error: Throwable) {
            showAnimationTimelineFailure(project, messages, error)
        }
    }.queue()
}

private fun showAnimationTimelineFailure(
    project: Project,
    messages: PreviewUiMessages,
    error: Throwable,
) {
    if (project.isDisposed) return
    val failure = error as? AnimationTimelineInspectFailure
    val messageKey = when (failure?.reason) {
        AnimationTimelineInspectFailureReason.NoDevice ->
            "animationTimeline.failure.noDevice"
        AnimationTimelineInspectFailureReason.ForegroundAppMissing ->
            "animationTimeline.failure.foregroundApp"
        AnimationTimelineInspectFailureReason.ReportUnavailable ->
            "animationTimeline.failure.notDebuggable"
        AnimationTimelineInspectFailureReason.StaleReport ->
            "animationTimeline.failure.staleReport"
        AnimationTimelineInspectFailureReason.NoAnimation ->
            "animationTimeline.failure.noAnimation"
        AnimationTimelineInspectFailureReason.MissingTransition ->
            "animationTimeline.failure.missingTransition"
        AnimationTimelineInspectFailureReason.Busy ->
            "animationTimeline.failure.busy"
        null -> null
    }
    val details = messageKey?.let(messages::text) ?: messages.text(
        "animationTimeline.failure.adb",
        error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName,
    )
    Messages.showWarningDialog(
        project,
        details,
        messages.text("animationTimeline.failure.title"),
    )
}

private class AnimationTimelineReportDialog(
    project: Project,
    private val messages: PreviewUiMessages,
    private val timeline: StudioAnimationTimeline,
) : DialogWrapper(project) {
    init {
        title = messages.text("animationTimeline.report.title")
        setOKButtonText(messages.text("animationTimeline.report.close"))
        init()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val report = timeline.presentableReport(messages)
        return javax.swing.JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 0)
            add(
                JBLabel(messages.text("animationTimeline.report.readOnly")),
                BorderLayout.NORTH,
            )
            add(
                JBScrollPane(
                    JBTextArea(report).apply {
                        isEditable = false
                        lineWrap = false
                        font = JBUI.Fonts.create("Monospaced", font.size)
                        border = JBUI.Borders.empty(8)
                    },
                ).apply {
                    border = JBUI.Borders.emptyTop(8)
                    preferredSize = Dimension(JBUI.scale(780), JBUI.scale(520))
                },
                BorderLayout.CENTER,
            )
        }
    }
}

internal fun StudioAnimationTimeline.presentableReport(messages: PreviewUiMessages): String {
    val durationSet = samples.flatMap { sample -> sample.channels.map { it.durationNanos } }.toSet()
    val interrupted = samples.any { sample -> sample.runState == "interrupted" }
    return buildString {
        appendLine(messages.text("animationTimeline.report.observation"))
        appendLine(messages.text("animationTimeline.report.control"))
        appendLine()
        appendLine("${label.ifBlank { messages.text("animationTimeline.unnamed") }} · $identity")
        appendLine(messages.text("animationTimeline.report.samples", samples.size))
        appendLine(messages.text("animationTimeline.report.unequal", durationSet.size > 1))
        appendLine(messages.text("animationTimeline.report.interrupted", interrupted))
        samples.forEachIndexed { index, sample ->
            appendLine()
            appendLine(
                messages.text(
                    "animationTimeline.report.sample",
                    index + 1,
                    sample.segmentVersion,
                    sample.runState,
                    sample.playTimeNanos.toMillisText(),
                    sample.durationNanos.toMillisText(),
                ),
            )
            appendLine(
                "  ${sample.segmentInitialState.presentableState()} → " +
                    sample.segmentTargetState.presentableState(),
            )
            sample.channels.forEach { channel ->
                appendLine(
                    "  ${channel.name} [${channel.specFamily}] " +
                        "${channel.durationNanos.toMillisText()} ms · " +
                        "${channel.startValue.presentableValue(messages)} → " +
                        "${channel.currentValue.presentableValue(messages)} → " +
                        channel.targetValue.presentableValue(messages),
                )
                appendLine(
                    "    velocity=${channel.velocity.presentableValue(messages)} · " +
                        "finished=${channel.finished} · terminal=${channel.terminalCondition}",
                )
            }
        }
    }
}

private fun StudioAnimationTimelineState.presentableState(): String {
    return displayValue ?: "<$typeName>"
}

private fun StudioAnimationTimelineValue?.presentableValue(
    messages: PreviewUiMessages,
): String {
    this ?: return messages.text("animationTimeline.report.unsupported")
    val body = components.joinToString(prefix = "(", postfix = ")") { component ->
        "%.3f".format(java.util.Locale.ROOT, component)
    }
    return "$kind$body"
}

private fun Long.toMillisText(): String {
    return "%.3f".format(java.util.Locale.ROOT, toDouble() / 1_000_000.0)
}

private fun Project.animationTimelineMessages(): PreviewUiMessages {
    val language = ViewComposePreviewSettings.forProject(this).language
    return PreviewUiMessages.forLanguage(language)
}
