package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDiagnosticsInspectorModelTest {
    @Test
    fun `session rows preserve the parent graph and keep orphaned sessions visible`() {
        val rows = deviceDiagnosticsSessionRows(
            listOf(
                session(id = 3, parentId = 1, role = StudioRenderSessionRole.LazyItem),
                session(id = 9, parentId = 99, role = StudioRenderSessionRole.OverlaySurface),
                session(id = 1, parentId = null, role = StudioRenderSessionRole.Host),
                session(id = 2, parentId = 1, role = StudioRenderSessionRole.NavigationDestination),
                session(id = 4, parentId = 2, role = StudioRenderSessionRole.PagerPage),
            ),
        )

        assertEquals(listOf(1L, 2L, 4L, 3L, 9L), rows.map { row -> row.sessionId })
        assertEquals(listOf(0, 1, 2, 1, 0), rows.map { row -> row.treeDepth })
    }

    @Test
    fun `summary correlates frame and safe failure without application content`() {
        val privateMessage = "private user text"
        val privateKey = "private application key"
        val failure = StudioDeviceDslFailure(
            frameId = 8L,
            phase = StudioRenderFailurePhase.ViewTreeRender,
            recovery = StudioRenderFailureRecovery.PreviousFrameRestored,
            operation = StudioRenderFailureOperation.AndroidViewUpdate,
            exceptionType = IllegalStateException::class.java.name,
        )
        val session = session(
            id = 2,
            parentId = 1,
            role = StudioRenderSessionRole.NavigationDestination,
            diagnostics = StudioDeviceDslSessionDiagnostics(
                sessionId = 2L,
                parentSessionId = 1L,
                role = StudioRenderSessionRole.NavigationDestination,
                renderingActive = true,
                committedFrameId = 7L,
                latestFrame = StudioDeviceDslFrame(
                    frameId = 8L,
                    status = StudioDeviceDslFrameStatus.RolledBack,
                    failures = listOf(failure),
                    droppedFailures = 0,
                ),
                latestFailure = failure,
                ended = false,
            ),
        )

        val summary = session.diagnosticSummary(
            PreviewUiMessages.forLanguage(PreviewUiLanguage.English),
        )

        assertTrue(summary.contains("Session: 2"))
        assertTrue(summary.contains("Latest committed frame: 7"))
        assertTrue(summary.contains("Latest frame attempt: 8 · rolled_back"))
        assertTrue(summary.contains("java.lang.IllegalStateException"))
        assertFalse(summary.contains(privateMessage))
        assertFalse(summary.contains(privateKey))
    }

    @Test
    fun `source label describes the same resolved location used for navigation`() {
        val source = StudioPreviewSourceLocation(
            filePath = "/project/app/src/main/java/com/viewcompose/DemoSubPageScaffold.kt",
            line = 85,
            column = 1,
            symbolName = "DemoSubPageScaffold",
        )

        assertEquals(
            "DemoSubPageScaffold.kt:85 · DemoSubPageScaffold",
            source.inspectorLabel(),
        )
    }

    @Test
    fun `node and timing rows display their resolved navigation target`() {
        val source = StudioPreviewSourceLocation(
            filePath = "/project/app/src/main/java/com/viewcompose/DiagnosticsPage.kt",
            line = 189,
            column = 1,
            symbolName = "DiagnosticsPage",
        )
        val node = StudioDeviceDslNode(
            token = "1a",
            parentToken = null,
            type = "AndroidView",
            depth = 1,
            synthetic = false,
            sourceCallSites = emptyList(),
        )
        val timing = StudioDeviceDslTimingRecord(
            frameId = 4L,
            nodeToken = "1a",
            parentNodeToken = null,
            nodeType = "AndroidView",
            depth = 1,
            synthetic = false,
            phase = StudioDeviceDslTimingPhase.Binding,
            inclusion = StudioDeviceDslTimingInclusion.Direct,
            durationNanos = 1_250_000L,
            repetitions = 1L,
            truncated = false,
            sourceCallSites = emptyList(),
        )

        assertEquals(
            "  AndroidView · DiagnosticsPage.kt:189 · DiagnosticsPage",
            ResolvedDeviceDiagnosticsNode(node, source).inspectorLabel(),
        )
        assertEquals(
            "1.250 ms · binding/direct · AndroidView · frame 4 · " +
                "DiagnosticsPage.kt:189 · DiagnosticsPage",
            ResolvedDeviceDiagnosticsTimingRecord(timing, source).inspectorLabel(
                PreviewUiMessages.forLanguage(PreviewUiLanguage.English),
            ),
        )
    }

    @Test
    fun `automation roles are unique and stable namespaced identifiers`() {
        val roles = DeviceDiagnosticsAutomationRoles::class.java.declaredFields
            .filter { field -> field.type == String::class.java }
            .map { field -> field.get(null) as String }

        assertEquals(roles.size, roles.distinct().size)
        assertTrue(roles.all { role -> role.startsWith("viewcompose.deviceDiagnostics.") })
    }

    private fun session(
        id: Long,
        parentId: Long?,
        role: StudioRenderSessionRole,
        diagnostics: StudioDeviceDslSessionDiagnostics? = null,
    ): StudioDeviceDslSourceSession {
        return StudioDeviceDslSourceSession(
            sessionId = id,
            parentSessionId = parentId,
            role = role,
            physicalContainerToken = id,
            renderingActive = true,
            attachedToWindow = true,
            shown = true,
            hasWindowFocus = true,
            windowVisibility = 0,
            viewDepth = 1,
            diagnostics = diagnostics,
            sourceCandidates = emptyList(),
            nodes = emptyList(),
            nodeGeneration = 0L,
            nodeInspectionSupported = true,
            nodeInspectionEnded = false,
            visitedNodes = 0,
            droppedNodes = 0,
            nodesTruncated = false,
        )
    }
}
