package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDslSourceProtocolTest {
    @Test
    fun `parses a device source report`() {
        val report = parseDeviceDslSourceReport(
            reportJson(
                sessions = """
                    ${sessionJson(id = 1, depth = 1, focused = true)},
                    ${sessionJson(id = 2, depth = 3, focused = true)}
                """.trimIndent(),
            ),
        )

        assertEquals("com.example.app", report.packageName)
        assertEquals(REQUEST_ID, report.requestId)
        assertEquals(StudioDeviceDslOperation.Source, report.operation)
        assertEquals(4242, report.processId)
        assertEquals(2, report.sessions.size)
        assertEquals(StudioRenderSessionRole.NavigationDestination, report.sessions.last().role)
        assertEquals(1L, report.sessions.last().parentSessionId)
        assertEquals(
            "Page2.kt",
            report.sessions.last().sourceCandidates.single().single().fileName,
        )
    }

    @Test
    fun `selects deepest active visible sessions and preserves multi-pane ambiguity`() {
        val report = parseDeviceDslSourceReport(
            reportJson(
                sessions = """
                    ${sessionJson(id = 1, depth = 1, focused = true)},
                    ${sessionJson(id = 2, depth = 4, focused = true)},
                    ${sessionJson(id = 3, depth = 4, focused = true)},
                    ${sessionJson(id = 4, depth = 6, focused = false)}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(3L, 2L), report.visibleSourceSessions().map { it.sessionId })
    }

    @Test
    fun `falls back to an active unattached session during host attachment`() {
        val report = parseDeviceDslSourceReport(
            reportJson(
                sessions = sessionJson(
                    id = 8,
                    depth = 0,
                    focused = false,
                    attached = false,
                    shown = false,
                ),
            ),
        )

        assertEquals(8L, report.visibleSourceSessions().single().sessionId)
    }

    @Test
    fun `rejects an unsupported protocol`() {
        val failure = runCatching {
            parseDeviceDslSourceReport(
                reportJson(sessions = "", protocolVersion = 99),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `parses bounded nodes and a clipped highlight result`() {
        val json = """
            {
              "protocolVersion": $DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
              "requestId": "$REQUEST_ID",
              "operation": "nodes",
              "packageName": "com.example.app",
              "processId": 4242,
              "generatedAtEpochMillis": 123456,
              "sessions": [{
                "sessionId": 7,
                "parentSessionId": null,
                "role": "Host",
                "renderingActive": true,
                "attachedToWindow": true,
                "shown": true,
                "hasWindowFocus": true,
                "windowVisibility": 0,
                "viewDepth": 1,
                "nodeGeneration": 2,
                "nodeInspectionSupported": true,
                "nodeInspectionEnded": false,
                "visitedNodes": 1,
                "droppedNodes": 0,
                "nodesTruncated": false,
                "sourceCandidates": [],
                "nodes": [{
                  "token": "a",
                  "parentToken": null,
                  "type": "AndroidView",
                  "depth": 0,
                  "synthetic": false,
                  "callSites": []
                }]
              }],
              "highlight": {
                "state": "clipped",
                "sessionId": 7,
                "nodeToken": "a",
                "screenBounds": {"left": 0, "top": 0, "right": 100, "bottom": 80},
                "visibleBounds": {"left": 0, "top": 10, "right": 100, "bottom": 80}
              }
            }
        """.trimIndent()

        val report = parseDeviceDslSourceReport(json)

        assertEquals(StudioDeviceDslOperation.Nodes, report.operation)
        assertEquals("AndroidView", report.sessions.single().nodes.single().type)
        assertEquals(StudioDeviceDslHighlightState.Clipped, report.highlight?.state)
        assertEquals(10, report.highlight?.visibleBounds?.top)
    }

    @Test
    fun `parses a complete node timing result`() {
        val report = parseDeviceDslSourceReport(timingReportJson())

        val timing = checkNotNull(report.timing)
        val result = checkNotNull(timing.result)
        assertEquals(StudioDeviceDslOperation.Timing, report.operation)
        assertEquals(StudioDeviceDslTimingStartStatus.Started, timing.startStatus)
        assertEquals(2, result.completedFrames)
        assertEquals(16L, result.attemptedClockReads)
        assertEquals(12L, result.retainedClockReads)
        assertEquals("duration_limit", result.endReason)
        assertTrue(result.complete)
        assertEquals(StudioDeviceDslTimingPhase.Binding, result.records.single().phase)
        assertEquals(StudioDeviceDslTimingInclusion.Direct, result.records.single().inclusion)
        assertEquals("z", result.records.single().nodeToken)
    }

    @Test
    fun `timing summary exposes terminal scope and instrumentation overhead`() {
        val result = checkNotNull(parseDeviceDslSourceReport(timingReportJson()).timing?.result)

        val summary = result.toTopCostText(
            PreviewUiMessages.forLanguage(PreviewUiLanguage.English),
        )

        assertTrue(summary.contains("2 completed frames · 1 retained records"))
        assertTrue(summary.contains("Empty clock-pair overhead: 3 ns"))
        assertTrue(summary.contains("End reason: duration_limit"))
        assertTrue(summary.contains("unsupported domains: gpu, render_thread"))
        assertTrue(summary.contains("0.003 ms · binding/direct · Text · frame 2"))
    }

    @Test
    fun `rejects timing output with impossible retained clock reads`() {
        val invalid = timingReportJson().replace(
            "\"retainedClockReads\": 12",
            "\"retainedClockReads\": 17",
        )

        val failure = runCatching { parseDeviceDslSourceReport(invalid) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `timing session selection includes source-less lazy sessions`() {
        val report = parseDeviceDslSourceReport(
            reportJson(
                sessions = sessionJson(
                    id = 7,
                    depth = 5,
                    focused = true,
                    includeSources = false,
                ),
            ),
        )

        assertEquals(7L, report.visibleTimingSessions().single().sessionId)
        assertTrue(report.visibleTimingSessions().single().sourceCandidates.isEmpty())
    }

    @Test
    fun `parses one correlated privacy safe session diagnostic snapshot`() {
        val report = parseDeviceDslSourceReport(
            reportJson(
                sessions = sessionJson(
                    id = 2,
                    depth = 3,
                    focused = true,
                    diagnostics = diagnosticsJson(id = 2, parentId = 1),
                ),
            ),
        )

        val diagnostics = checkNotNull(report.sessions.single().diagnostics)
        assertEquals(2L, diagnostics.sessionId)
        assertEquals(1L, diagnostics.parentSessionId)
        assertEquals(9L, diagnostics.committedFrameId)
        assertEquals(StudioDeviceDslFrameStatus.Committed, diagnostics.latestFrame?.status)
        assertEquals(StudioRenderFailurePhase.ViewTreeRender, diagnostics.latestFailure?.phase)
        assertEquals(StudioRenderFailureRecovery.FrameCommitted, diagnostics.latestFailure?.recovery)
        assertEquals(StudioRenderFailureOperation.AndroidViewUpdate, diagnostics.latestFailure?.operation)
        assertEquals(IllegalStateException::class.java.name, diagnostics.latestFailure?.exceptionType)
    }

    @Test
    fun `rejects diagnostics attributed to a different session`() {
        val failure = runCatching {
            parseDeviceDslSourceReport(
                reportJson(
                    sessions = sessionJson(
                        id = 2,
                        depth = 3,
                        focused = true,
                        diagnostics = diagnosticsJson(id = 3, parentId = 1),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private fun reportJson(
        sessions: String,
        protocolVersion: Int = DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
    ): String {
        return """
            {
              "protocolVersion": $protocolVersion,
              "requestId": "$REQUEST_ID",
              "operation": "source",
              "packageName": "com.example.app",
              "processId": 4242,
              "generatedAtEpochMillis": 123456,
              "sessions": [$sessions]
            }
        """.trimIndent()
    }

    private fun sessionJson(
        id: Int,
        depth: Int,
        focused: Boolean,
        attached: Boolean = true,
        shown: Boolean = true,
        includeSources: Boolean = true,
        diagnostics: String = "null",
    ): String {
        val sourceCandidates = if (includeSources) {
            """
                [{
                  "callSites": [{
                    "className": "com.example.Page${id}Kt",
                    "methodName": "Page$id",
                    "fileName": "Page$id.kt",
                    "lineNumber": ${20 + id}
                  }]
                }]
            """.trimIndent()
        } else {
            "[]"
        }
        return """
            {
              "sessionId": $id,
              "parentSessionId": ${if (id == 1) "null" else "1"},
              "role": "${if (id == 1) "Host" else "NavigationDestination"}",
              "renderingActive": true,
              "attachedToWindow": $attached,
              "shown": $shown,
              "hasWindowFocus": $focused,
              "windowVisibility": 0,
              "viewDepth": $depth,
              "diagnostics": $diagnostics,
              "nodeGeneration": 0,
              "nodeInspectionSupported": true,
              "nodeInspectionEnded": false,
              "visitedNodes": 0,
              "droppedNodes": 0,
              "nodesTruncated": false,
              "sourceCandidates": $sourceCandidates,
              "nodes": []
            }
        """.trimIndent()
    }

    private fun diagnosticsJson(id: Int, parentId: Int?): String = """
        {
          "sessionId": $id,
          "parentSessionId": ${parentId ?: "null"},
          "role": "${if (id == 1) "Host" else "NavigationDestination"}",
          "renderingActive": true,
          "committedFrameId": 9,
          "ended": false,
          "latestFrame": {
            "frameId": 9,
            "status": "committed",
            "failures": [],
            "droppedFailures": 0
          },
          "latestFailure": {
            "frameId": 9,
            "phase": "view_tree_render",
            "recovery": "frame_committed",
            "operation": "android_view_update",
            "exceptionType": "java.lang.IllegalStateException"
          }
        }
    """.trimIndent()

    private fun timingReportJson(): String = """
        {
          "protocolVersion": $DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
          "requestId": "$REQUEST_ID",
          "operation": "timing",
          "packageName": "com.example.app",
          "processId": 4242,
          "generatedAtEpochMillis": 123456,
          "sessions": [],
          "timing": {
            "startStatus": "started",
            "result": {
              "sessionId": 7,
              "parentSessionId": null,
              "role": "Host",
              "clock": "monotonic_nanoseconds",
              "completedFrames": 2,
              "startedAtNanos": 100,
              "endedAtNanos": 500,
              "attemptedClockReads": 16,
              "retainedClockReads": 12,
              "emptyPairOverheadNanos": 3,
              "droppedTimedNodes": 0,
              "droppedRecords": 0,
              "droppedStrings": 0,
              "truncated": false,
              "complete": true,
              "endReason": "duration_limit",
              "unsupportedDomains": ["gpu", "render_thread"],
              "records": [{
                "frameId": 2,
                "nodeToken": "z",
                "parentNodeToken": null,
                "nodeType": "Text",
                "depth": 1,
                "synthetic": false,
                "phase": "binding",
                "inclusion": "direct",
                "durationNanos": 2500,
                "repetitions": 1,
                "truncated": false,
                "callSites": [{
                  "className": "com.example.PageKt",
                  "methodName": "Page",
                  "fileName": "Page.kt",
                  "lineNumber": 27
                }]
              }],
              "recordsTruncated": false
            }
          }
        }
    """.trimIndent()

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
    }
}
