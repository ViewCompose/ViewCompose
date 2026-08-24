package com.viewcompose.studio.preview

internal data class DeviceDiagnosticsSessionRow(
    val session: StudioDeviceDslSourceSession,
    val treeDepth: Int,
) {
    val sessionId: Long
        get() = session.sessionId
}

internal fun deviceDiagnosticsSessionRows(
    sessions: List<StudioDeviceDslSourceSession>,
): List<DeviceDiagnosticsSessionRow> {
    val sessionsById = sessions.associateBy(StudioDeviceDslSourceSession::sessionId)
    val children = sessions.groupBy(StudioDeviceDslSourceSession::parentSessionId)
    val emitted = LinkedHashSet<Long>()
    val rows = mutableListOf<DeviceDiagnosticsSessionRow>()

    fun emit(session: StudioDeviceDslSourceSession, depth: Int) {
        if (!emitted.add(session.sessionId)) return
        rows += DeviceDiagnosticsSessionRow(session, depth)
        children[session.sessionId]
            .orEmpty()
            .sortedBy(StudioDeviceDslSourceSession::sessionId)
            .forEach { child -> emit(child, depth + 1) }
    }

    sessions
        .filter { session ->
            session.parentSessionId == null || session.parentSessionId !in sessionsById
        }
        .sortedWith(
            compareBy<StudioDeviceDslSourceSession> { session -> session.parentSessionId != null }
                .thenBy(StudioDeviceDslSourceSession::sessionId),
        )
        .forEach { session -> emit(session, 0) }
    sessions.sortedBy(StudioDeviceDslSourceSession::sessionId).forEach { session ->
        emit(session, 0)
    }
    return rows
}

internal fun DeviceDiagnosticsSessionRow.label(messages: PreviewUiMessages): String {
    val stateKey = when {
        session.diagnostics?.ended == true || session.nodeInspectionEnded ->
            "deviceDsl.inspector.state.ended"
        session.renderingActive && session.attachedToWindow && session.shown ->
            "deviceDsl.inspector.state.visible"
        session.renderingActive -> "deviceDsl.inspector.state.active"
        else -> "deviceDsl.inspector.state.inactive"
    }
    return buildString {
        append("  ".repeat(treeDepth.coerceAtMost(12)))
        append(session.role)
        append(" · #")
        append(session.sessionId)
        append(" · ")
        append(messages.text(stateKey))
    }
}

internal fun StudioDeviceDslSourceSession.diagnosticSummary(
    messages: PreviewUiMessages,
): String {
    val snapshot = diagnostics
    return buildString {
        append(messages.text("deviceDsl.inspector.summary.identity", role, sessionId))
        append('\n')
        append(
            messages.text(
                "deviceDsl.inspector.summary.parent",
                parentSessionId?.toString() ?: messages.text("deviceDsl.inspector.none"),
            ),
        )
        append('\n')
        append(
            messages.text(
                "deviceDsl.inspector.summary.lifecycle",
                if (renderingActive) {
                    messages.text("deviceDsl.inspector.active")
                } else {
                    messages.text("deviceDsl.inspector.inactive")
                },
                if (attachedToWindow && shown) {
                    messages.text("deviceDsl.inspector.visible")
                } else {
                    messages.text("deviceDsl.inspector.notVisible")
                },
                if (snapshot?.ended == true || nodeInspectionEnded) {
                    messages.text("deviceDsl.inspector.ended")
                } else {
                    messages.text("deviceDsl.inspector.live")
                },
            ),
        )
        append("\n\n")
        if (snapshot == null) {
            append(messages.text("deviceDsl.inspector.summary.unavailable"))
            return@buildString
        }
        append(
            messages.text(
                "deviceDsl.inspector.summary.committed",
                snapshot.committedFrameId?.toString() ?: messages.text("deviceDsl.inspector.none"),
            ),
        )
        append('\n')
        val frame = snapshot.latestFrame
        if (frame == null) {
            append(messages.text("deviceDsl.inspector.summary.noFrame"))
        } else {
            append(
                messages.text(
                    "deviceDsl.inspector.summary.frame",
                    frame.frameId,
                    frame.status.wireValue,
                    frame.failures.size,
                    frame.droppedFailures,
                ),
            )
        }
        append('\n')
        val failure = snapshot.latestFailure
        if (failure == null) {
            append(messages.text("deviceDsl.inspector.summary.noFailure"))
        } else {
            append(
                messages.text(
                    "deviceDsl.inspector.summary.failure",
                    failure.phase.wireValue,
                    failure.recovery.wireValue,
                    failure.exceptionType,
                ),
            )
            failure.operation?.let { operation ->
                append('\n')
                append(
                    messages.text(
                        "deviceDsl.inspector.summary.operation",
                        operation.wireValue,
                    ),
                )
            }
        }
    }
}

internal data class ResolvedDeviceDiagnosticsNode(
    val node: StudioDeviceDslNode,
    val source: StudioPreviewSourceLocation?,
)

internal data class ResolvedDeviceDiagnosticsTimingRecord(
    val record: StudioDeviceDslTimingRecord,
    val source: StudioPreviewSourceLocation?,
)

internal fun ResolvedDeviceDiagnosticsNode.inspectorLabel(): String {
    val sourceSuffix = source?.let { " · ${it.inspectorLabel()}" }.orEmpty()
    return "  ".repeat(node.depth.coerceAtMost(12)) + node.type + sourceSuffix
}

internal fun ResolvedDeviceDiagnosticsTimingRecord.inspectorLabel(
    messages: PreviewUiMessages,
): String {
    return record.inspectorLabel(messages, source)
}

internal fun StudioPreviewSourceLocation.inspectorLabel(): String {
    val fileName = filePath.replace('\\', '/').substringAfterLast('/')
    return "$fileName:$line · $symbolName"
}
