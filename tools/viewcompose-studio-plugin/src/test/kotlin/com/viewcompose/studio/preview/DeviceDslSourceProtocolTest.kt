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
        assertEquals(4242, report.processId)
        assertEquals(2, report.sessions.size)
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

    private fun reportJson(
        sessions: String,
        protocolVersion: Int = DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
    ): String {
        return """
            {
              "protocolVersion": $protocolVersion,
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
    ): String {
        return """
            {
              "sessionId": $id,
              "renderingActive": true,
              "attachedToWindow": $attached,
              "shown": $shown,
              "hasWindowFocus": $focused,
              "windowVisibility": 0,
              "viewDepth": $depth,
              "sourceCandidates": [{
                "callSites": [{
                  "className": "com.example.Page${id}Kt",
                  "methodName": "Page$id",
                  "fileName": "Page$id.kt",
                  "lineNumber": ${20 + id}
                }]
              }]
            }
        """.trimIndent()
    }
}
