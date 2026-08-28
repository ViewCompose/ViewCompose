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
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel

/** Opens the one correlated running-device diagnostics inspector. */
class InspectDeviceDiagnosticsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val messages = project?.deviceDslMessages()
        event.presentation.isEnabled = project != null
        event.presentation.isVisible = project != null
        if (messages != null) {
            event.presentation.text = messages.text("action.inspectDeviceDiagnostics")
            event.presentation.description = messages.text(
                "action.inspectDeviceDiagnostics.description",
            )
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
                chooseDevice(project, messages, devices)?.let { device ->
                    loadDeviceDiagnostics(project, messages, device)
                }
            }

            override fun onThrowable(error: Throwable) {
                showDeviceDslFailure(project, messages, error)
            }
        }.queue()
    }
}

private fun loadDeviceDiagnostics(
    project: Project,
    messages: PreviewUiMessages,
    device: StudioAndroidDevice,
) {
    object : Task.Backgroundable(
        project,
        messages.text("deviceDsl.progress.diagnostics"),
        true,
    ) {
        private var snapshot: ResolvedDeviceDiagnosticsSnapshot? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.checkCanceled()
            val report = readDeviceDslSourceReport(device).also { currentReport ->
                if (currentReport.sessions.isEmpty()) {
                    throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoVisibleDsl)
                }
            }
            snapshot = report.resolveSources(project)
        }

        override fun onSuccess() {
            if (project.isDisposed) return
            DeviceDiagnosticsInspectorDialog(
                project = project,
                messages = messages,
                device = device,
                initialSnapshot = checkNotNull(snapshot),
            ).show()
        }

        override fun onThrowable(error: Throwable) {
            showDeviceDslFailure(project, messages, error)
        }
    }.queue()
}

internal object DeviceDiagnosticsAutomationRoles {
    const val Dialog = "viewcompose.deviceDiagnostics.dialog"
    const val SessionList = "viewcompose.deviceDiagnostics.sessions"
    const val SessionSummary = "viewcompose.deviceDiagnostics.sessionSummary"
    const val SourceList = "viewcompose.deviceDiagnostics.sources"
    const val NodeList = "viewcompose.deviceDiagnostics.nodes"
    const val TimingSummary = "viewcompose.deviceDiagnostics.timingSummary"
    const val TimingRecords = "viewcompose.deviceDiagnostics.timingRecords"
    const val Refresh = "viewcompose.deviceDiagnostics.refresh"
    const val OpenSessionSource = "viewcompose.deviceDiagnostics.openSessionSource"
    const val LoadNodes = "viewcompose.deviceDiagnostics.loadNodes"
    const val OpenNodeSource = "viewcompose.deviceDiagnostics.openNodeSource"
    const val HighlightNode = "viewcompose.deviceDiagnostics.highlightNode"
    const val ClearHighlight = "viewcompose.deviceDiagnostics.clearHighlight"
    const val CaptureTiming = "viewcompose.deviceDiagnostics.captureTiming"
    const val CaptureNextLazyItem = "viewcompose.deviceDiagnostics.captureNextLazyItem"
    const val OpenTimingSource = "viewcompose.deviceDiagnostics.openTimingSource"
    const val Status = "viewcompose.deviceDiagnostics.status"
}

internal class DeviceDiagnosticsInspectorDialog(
    private val project: Project,
    private val messages: PreviewUiMessages,
    private val device: StudioAndroidDevice,
    initialSnapshot: ResolvedDeviceDiagnosticsSnapshot,
) : DialogWrapper(project, true) {
    private var report = initialSnapshot.report
    private var sessionSources = initialSnapshot.sessionSources
    private var requestInFlight = false

    private val sessionList = JBList<DeviceDiagnosticsSessionRow>().apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 12
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus,
                )
                text = (value as? DeviceDiagnosticsSessionRow)?.label(messages).orEmpty()
                return component
            }
        }
    }.withAutomationRole(DeviceDiagnosticsAutomationRoles.SessionList)
    private val summary = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 8
    }.withAutomationRole(DeviceDiagnosticsAutomationRoles.SessionSummary)
    private val sourceList = JBList<StudioPreviewSourceLocation>().apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = sourceLocationRenderer()
    }.withAutomationRole(DeviceDiagnosticsAutomationRoles.SourceList)
    private val nodeList = JBList<ResolvedDeviceDiagnosticsNode>().apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus,
                )
                text = (value as? ResolvedDeviceDiagnosticsNode)?.inspectorLabel().orEmpty()
                return component
            }
        }
    }.withAutomationRole(DeviceDiagnosticsAutomationRoles.NodeList)
    private val timingSummary = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 5
    }.withAutomationRole(DeviceDiagnosticsAutomationRoles.TimingSummary)
    private val timingRecords = JBList<ResolvedDeviceDiagnosticsTimingRecord>().apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val component = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus,
                )
                text = (value as? ResolvedDeviceDiagnosticsTimingRecord)
                    ?.inspectorLabel(messages)
                    .orEmpty()
                return component
            }
        }
    }.withAutomationRole(DeviceDiagnosticsAutomationRoles.TimingRecords)
    private val status = JBLabel().withAutomationRole(DeviceDiagnosticsAutomationRoles.Status)

    private val refreshButton = actionButton(
        "deviceDsl.inspector.refresh",
        DeviceDiagnosticsAutomationRoles.Refresh,
        ::refresh,
    )
    private val openSessionSourceButton = actionButton(
        "deviceDsl.inspector.openSource",
        DeviceDiagnosticsAutomationRoles.OpenSessionSource,
        ::openSessionSource,
    )
    private val loadNodesButton = actionButton(
        "deviceDsl.inspector.loadNodes",
        DeviceDiagnosticsAutomationRoles.LoadNodes,
        ::loadNodes,
    )
    private val openNodeSourceButton = actionButton(
        "deviceDsl.inspector.openNodeSource",
        DeviceDiagnosticsAutomationRoles.OpenNodeSource,
        ::openNodeSource,
    )
    private val highlightButton = actionButton(
        "deviceDsl.inspector.highlight",
        DeviceDiagnosticsAutomationRoles.HighlightNode,
        ::highlightNode,
    )
    private val clearButton = actionButton(
        "deviceDsl.inspector.clear",
        DeviceDiagnosticsAutomationRoles.ClearHighlight,
        ::clearHighlight,
    )
    private val captureTimingButton = actionButton(
        "deviceDsl.inspector.captureTiming",
        DeviceDiagnosticsAutomationRoles.CaptureTiming,
        ::captureTiming,
    )
    private val captureNextLazyItemButton = actionButton(
        "deviceDsl.inspector.captureNextLazyItem",
        DeviceDiagnosticsAutomationRoles.CaptureNextLazyItem,
        ::captureNextLazyItemTiming,
    )
    private val openTimingSourceButton = actionButton(
        "deviceDsl.inspector.openTimingSource",
        DeviceDiagnosticsAutomationRoles.OpenTimingSource,
        ::openTimingSource,
    )

    init {
        title = messages.text("deviceDsl.inspector.title")
        setModal(false)
        init()
        rootPane.withAutomationRole(DeviceDiagnosticsAutomationRoles.Dialog)
        cancelAction.putValue(Action.NAME, messages.text("deviceDsl.inspector.close"))
        sessionList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) updateSelectedSession()
        }
        sourceList.addListSelectionListener { updateActionState() }
        nodeList.addListSelectionListener { updateActionState() }
        timingRecords.addListSelectionListener { updateActionState() }
        replaceSnapshot(
            initialSnapshot,
            selectedSessionId = preferredSessionId(initialSnapshot.report),
        )
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    override fun createCenterPanel(): JComponent {
        val header = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.emptyBottom(8)
            add(
                JBLabel(
                    messages.text(
                        "deviceDsl.inspector.device",
                        device.displayName,
                        report.packageName,
                        report.processId,
                    ),
                ),
                BorderLayout.CENTER,
            )
            add(refreshButton, BorderLayout.EAST)
        }
        val details = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(JBScrollPane(summary), BorderLayout.NORTH)
            add(buildTabs(), BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }
        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(sessionList),
            details,
        ).apply {
            resizeWeight = 0.31
            dividerLocation = JBUI.scale(290)
            border = JBUI.Borders.empty()
        }
        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(JBUI.scale(980), JBUI.scale(640))
            border = JBUI.Borders.empty(8)
            add(header, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = sessionList

    private fun buildTabs(): JComponent {
        return JBTabbedPane().apply {
            addTab(
                messages.text("deviceDsl.inspector.tab.sources"),
                listTab(sourceList, openSessionSourceButton),
            )
            addTab(
                messages.text("deviceDsl.inspector.tab.nodes"),
                listTab(
                    nodeList,
                    loadNodesButton,
                    openNodeSourceButton,
                    highlightButton,
                    clearButton,
                ),
            )
            addTab(
                messages.text("deviceDsl.inspector.tab.timing"),
                JPanel(BorderLayout(0, JBUI.scale(6))).apply {
                    add(JBScrollPane(timingSummary), BorderLayout.NORTH)
                    add(JBScrollPane(timingRecords), BorderLayout.CENTER)
                    add(
                        buttonRow(
                            captureTimingButton,
                            captureNextLazyItemButton,
                            openTimingSourceButton,
                        ),
                        BorderLayout.SOUTH,
                    )
                },
            )
        }
    }

    private fun listTab(list: JComponent, vararg buttons: JButton): JComponent {
        return JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(buttonRow(*buttons), BorderLayout.SOUTH)
        }
    }

    private fun buttonRow(vararg buttons: JButton): JComponent {
        return JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(6), 0)).apply {
            buttons.forEach(::add)
        }
    }

    private fun actionButton(
        textKey: String,
        role: String,
        action: () -> Unit,
    ): JButton {
        return JButton(messages.text(textKey)).apply {
            withAutomationRole(role)
            addActionListener { action() }
        }
    }

    private fun refresh() {
        val selectedId = selectedSession()?.sessionId
        runDeviceTask(
            messages.text("deviceDsl.progress.diagnostics"),
            producer = {
                readDeviceDslSourceReport(device).also { refreshed ->
                    if (refreshed.sessions.isEmpty()) {
                        throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoVisibleDsl)
                    }
                }.resolveSources(project)
            },
        ) { refreshed ->
            if (refreshed.report.sessions.isEmpty()) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoVisibleDsl)
            }
            replaceSnapshot(refreshed, selectedId)
            showStatus("deviceDsl.inspector.status.refreshed")
        }
    }

    private fun replaceSnapshot(
        refreshed: ResolvedDeviceDiagnosticsSnapshot,
        selectedSessionId: Long?,
    ) {
        report = refreshed.report
        sessionSources = refreshed.sessionSources
        val rows = deviceDiagnosticsSessionRows(refreshed.report.sessions)
        sessionList.replaceItems(rows)
        val selectedIndex = rows.indexOfFirst { row -> row.sessionId == selectedSessionId }
            .takeIf { index -> index >= 0 }
            ?: 0
        if (rows.isNotEmpty()) sessionList.selectedIndex = selectedIndex
        updateSelectedSession()
    }

    private fun updateSelectedSession() {
        val session = selectedSession()
        summary.text = session?.diagnosticSummary(messages).orEmpty()
        summary.caretPosition = 0
        sourceList.replaceItems(session?.let { sessionSources[it.sessionId] }.orEmpty())
        if (sourceList.model.size > 0) sourceList.selectedIndex = 0
        nodeList.replaceItems(emptyList())
        timingSummary.text = messages.text("deviceDsl.inspector.timing.empty")
        timingRecords.replaceItems(emptyList())
        updateActionState()
    }

    private fun updateActionState() {
        val session = selectedSession()
        refreshButton.isEnabled = !requestInFlight
        openSessionSourceButton.isEnabled = !requestInFlight && sourceList.selectedValue != null
        loadNodesButton.isEnabled = !requestInFlight &&
            session?.nodeInspectionSupported == true &&
            session.nodeInspectionEnded.not()
        openNodeSourceButton.isEnabled = !requestInFlight &&
            nodeList.selectedValue?.source != null
        highlightButton.isEnabled = !requestInFlight &&
            nodeList.selectedValue?.node?.synthetic == false
        clearButton.isEnabled = !requestInFlight
        captureTimingButton.isEnabled = !requestInFlight &&
            session != null && session.diagnostics?.ended != true
        captureNextLazyItemButton.isEnabled = !requestInFlight &&
            session?.role == StudioRenderSessionRole.Host && session.diagnostics?.ended != true
        openTimingSourceButton.isEnabled = !requestInFlight &&
            timingRecords.selectedValue?.source != null
    }

    private fun openSessionSource() {
        sourceList.selectedValue?.let(project::navigateToSource)
    }

    private fun openNodeSource() {
        nodeList.selectedValue?.source?.let(project::navigateToSource)
    }

    private fun openTimingSource() {
        timingRecords.selectedValue?.source?.let(project::navigateToSource)
    }

    private fun loadNodes() {
        val sessionId = selectedSession()?.sessionId ?: return
        runDeviceTask(
            messages.text("deviceDsl.progress.nodes"),
            producer = {
                val nodeReport = readDeviceDslNodeReport(device, sessionId)
                val session = nodeReport.sessions.singleOrNull { candidate ->
                    candidate.sessionId == sessionId
                } ?: throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoInspectableNode)
                if (!session.nodeInspectionSupported || session.nodeInspectionEnded) {
                    throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoInspectableNode)
                }
                val nodes = session.nodes.filterNot(StudioDeviceDslNode::synthetic).also { nodes ->
                    if (nodes.isEmpty()) {
                        throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.NoInspectableNode)
                    }
                }
                nodes.resolveSources(project)
            },
        ) { nodes ->
            if (selectedSession()?.sessionId != sessionId) {
                showStatus("deviceDsl.inspector.status.selectionChanged")
                return@runDeviceTask
            }
            nodeList.replaceItems(nodes)
            nodeList.selectedIndex = 0
            showStatus("deviceDsl.inspector.status.nodes", nodes.size)
            updateActionState()
        }
    }

    private fun highlightNode() {
        val session = selectedSession() ?: return
        val node = nodeList.selectedValue?.node ?: return
        runDeviceTask(
            messages.text("deviceDsl.progress.highlight"),
            producer = { selectDeviceDslNode(device, session.sessionId, node.token) },
        ) { result ->
            if (result.state !in setOf(
                    StudioDeviceDslHighlightState.Selected,
                    StudioDeviceDslHighlightState.Clipped,
                )
            ) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.HighlightRejected)
            }
            if (selectedSession()?.sessionId != session.sessionId) {
                clearHighlight()
                return@runDeviceTask
            }
            showStatus("deviceDsl.inspector.status.highlight", result.state.wireValue)
            if (result.state == StudioDeviceDslHighlightState.Clipped) {
                Messages.showInfoMessage(
                    project,
                    messages.text("deviceDsl.highlight.clipped"),
                    messages.text("deviceDsl.highlight.title"),
                )
            }
        }
    }

    private fun clearHighlight() {
        runDeviceTask(
            messages.text("deviceDsl.progress.clearHighlight"),
            producer = { clearDeviceDslHighlight(device) },
        ) { result ->
            if (result.state != StudioDeviceDslHighlightState.Cleared) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.HighlightRejected)
            }
            showStatus("deviceDsl.inspector.status.cleared")
        }
    }

    private fun captureTiming() {
        captureTiming(futureLazyItem = false)
    }

    private fun captureNextLazyItemTiming() {
        captureTiming(futureLazyItem = true)
    }

    private fun captureTiming(futureLazyItem: Boolean) {
        val sessionId = selectedSession()?.sessionId ?: return
        val decision = Messages.showOkCancelDialog(
            project,
            messages.text(
                if (futureLazyItem) {
                    "deviceDsl.timing.futureLazyItemPrompt"
                } else {
                    "deviceDsl.timing.workloadPrompt"
                },
            ),
            messages.text("deviceDsl.timing.title"),
            messages.text("deviceDsl.timing.start"),
            messages.text("deviceDsl.cancel"),
            Messages.getInformationIcon(),
        )
        if (decision != Messages.OK) return
        runDeviceTask(
            messages.text("deviceDsl.progress.timing"),
            producer = {
                val snapshot = if (futureLazyItem) {
                    readFutureLazyItemTimingReport(device, sessionId)
                } else {
                    readDeviceDslTimingReport(device, sessionId)
                }
                ResolvedDeviceDiagnosticsTimingSnapshot(
                    snapshot = snapshot,
                    records = snapshot.result
                        ?.additiveRecords()
                        .orEmpty()
                        .resolveTimingSources(project),
                )
            },
        ) { resolved ->
            val snapshot = resolved.snapshot
            val result = snapshot.result
            if (
                snapshot.startStatus != StudioDeviceDslTimingStartStatus.Started ||
                result?.complete != true
            ) {
                throw DeviceDslLocateFailure(DeviceDslLocateFailureReason.TimingRejected)
            }
            if (selectedSession()?.sessionId != sessionId) {
                showStatus("deviceDsl.inspector.status.selectionChanged")
                return@runDeviceTask
            }
            timingSummary.text = snapshot.toTopCostText(messages, limit = 0)
            timingSummary.caretPosition = 0
            val records = resolved.records
            timingRecords.replaceItems(records)
            if (records.isNotEmpty()) timingRecords.selectedIndex = 0
            showStatus("deviceDsl.inspector.status.timing", result.completedFrames, records.size)
            updateActionState()
        }
    }

    private fun selectedSession(): StudioDeviceDslSourceSession? = sessionList.selectedValue?.session

    private fun showStatus(key: String, vararg values: Any) {
        status.text = messages.text(key, *values)
    }

    private fun <T : Any> runDeviceTask(
        progressTitle: String,
        producer: () -> T,
        consumer: (T) -> Unit,
    ) {
        if (requestInFlight) return
        requestInFlight = true
        status.text = progressTitle
        updateActionState()
        object : Task.Backgroundable(project, progressTitle, true) {
            private var result: T? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.checkCanceled()
                result = producer()
            }

            override fun onSuccess() {
                if (project.isDisposed || isDisposed) return
                requestInFlight = false
                updateActionState()
                runCatching { consumer(checkNotNull(result)) }
                    .onFailure { error -> showDeviceDslFailure(project, messages, error) }
            }

            override fun onThrowable(error: Throwable) {
                requestInFlight = false
                updateActionState()
                showDeviceDslFailure(project, messages, error)
            }
        }.queue()
    }
}

private fun preferredSessionId(report: StudioDeviceDslSourceReport): Long? {
    return report.visibleTimingSessions().firstOrNull()?.sessionId
        ?: report.sessions.firstOrNull()?.sessionId
}

private fun sourceLocationRenderer(): DefaultListCellRenderer {
    return object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(
                list,
                value,
                index,
                isSelected,
                cellHasFocus,
            )
            text = (value as? StudioPreviewSourceLocation)?.inspectorLabel().orEmpty()
            return component
        }
    }
}

internal data class ResolvedDeviceDiagnosticsSnapshot(
    val report: StudioDeviceDslSourceReport,
    val sessionSources: Map<Long, List<StudioPreviewSourceLocation>>,
)

private data class ResolvedDeviceDiagnosticsTimingSnapshot(
    val snapshot: StudioDeviceDslTimingSnapshot,
    val records: List<ResolvedDeviceDiagnosticsTimingRecord>,
)

private fun StudioDeviceDslSourceReport.resolveSources(
    project: Project,
): ResolvedDeviceDiagnosticsSnapshot {
    val resolver = StudioPreviewSourceResolver(project)
    return ResolvedDeviceDiagnosticsSnapshot(
        report = this,
        sessionSources = sessions.associate { session ->
            session.sessionId to resolver.resolveCandidates(session.sourceCandidates)
        },
    )
}

private fun List<StudioDeviceDslNode>.resolveSources(
    project: Project,
): List<ResolvedDeviceDiagnosticsNode> {
    val sources = StudioPreviewSourceResolver(project).resolveEach(map { node -> node.sourceCallSites })
    return mapIndexed { index, node ->
        ResolvedDeviceDiagnosticsNode(node = node, source = sources[index])
    }
}

private fun List<StudioDeviceDslTimingRecord>.resolveTimingSources(
    project: Project,
): List<ResolvedDeviceDiagnosticsTimingRecord> {
    val sources = StudioPreviewSourceResolver(project).resolveEach(map { record -> record.sourceCallSites })
    return mapIndexed { index, record ->
        ResolvedDeviceDiagnosticsTimingRecord(record = record, source = sources[index])
    }
}

private fun <T> JBList<T>.replaceItems(items: List<T>) {
    model = DefaultListModel<T>().also { next ->
        items.forEach(next::addElement)
    }
}

private fun <T : JComponent> T.withAutomationRole(role: String): T {
    name = role
    accessibleContext.accessibleName = role
    return this
}
