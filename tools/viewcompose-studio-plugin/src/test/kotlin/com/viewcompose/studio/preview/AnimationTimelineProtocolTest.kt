package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTimelineProtocolTest {
    @Test
    fun `parses bounded physical and unequal channel timelines`() {
        val report = parseAnimationTimelineReport(reportJson())
        val timeline = report.transitions.single()
        val sample = timeline.samples.single()

        assertEquals(REQUEST_ID, report.requestId)
        assertEquals("capture", report.mode)
        assertEquals("success", report.status)
        assertEquals("interrupted", sample.runState)
        assertEquals(listOf(300_000_000L, 900_000_000L), sample.channels.map { it.durationNanos })
        assertEquals("durationlimitreached", sample.channels.last().terminalCondition)
        assertEquals(null, sample.channels.last().currentValue)
    }

    @Test
    fun `read-only report exposes control boundary and unsupported values`() {
        val timeline = parseAnimationTimelineReport(reportJson()).transitions.single()
        val messages = PreviewUiMessages.forLanguage(PreviewUiLanguage.English)
        val text = timeline.presentableReport(messages)

        assertTrue(text.contains("bounded observation only"))
        assertTrue(text.contains("static Preview only"))
        assertTrue(text.contains("Unequal channel durations: true"))
        assertTrue(text.contains("Interruption/retarget observed: true"))
        assertTrue(text.contains("<unsupported/private>"))
        assertFalse(text.contains("remote seek enabled", ignoreCase = true))
    }

    @Test
    fun `rejects malformed values unsupported enums and oversized reports`() {
        assertTrue(
            runCatching {
                parseAnimationTimelineReport(reportJson().replace("\"tween\"", "\"forever\""))
            }.isFailure,
        )
        assertTrue(
            runCatching {
                parseAnimationTimelineReport(reportJson().replace("[0.5]", "[0.5, 0.6]"))
            }.isFailure,
        )
        assertTrue(
            runCatching {
                parseAnimationTimelineReport(" ".repeat(256 * 1024 + 1))
            }.isFailure,
        )
        val oversizedArray = reportJson().replace(
            Regex("\"transitions\"\\s*:\\s*\\[[\\s\\S]*]\\s*}$"),
            "\"transitions\": [" + List(65) { index ->
                "{\"identity\":\"transition-$index\",\"label\":\"\",\"samples\":[]}"
            }.joinToString() + "]}",
        )
        assertTrue(runCatching { parseAnimationTimelineReport(oversizedArray) }.isFailure)
    }

    private fun reportJson(): String {
        return """
            {
              "protocolVersion": 1,
              "requestId": "$REQUEST_ID",
              "packageName": "com.example.app",
              "processId": 4242,
              "generatedAtEpochMillis": 123456,
              "mode": "capture",
              "status": "success",
              "transitions": [{
                "identity": "transition-1",
                "label": "panel",
                "samples": [{
                  "identity": "transition-1",
                  "label": "panel",
                  "currentState": {"typeName": "boolean", "displayValue": "false"},
                  "targetState": {"typeName": "boolean", "displayValue": "true"},
                  "segmentInitialState": {"typeName": "boolean", "displayValue": "false"},
                  "segmentTargetState": {"typeName": "boolean", "displayValue": "true"},
                  "segmentVersion": 2,
                  "playTimeNanos": 80000000,
                  "durationNanos": 900000000,
                  "runState": "interrupted",
                  "channels": [
                    {
                      "identity": "channel-1",
                      "name": "Float 1",
                      "specFamily": "tween",
                      "startValue": {"kind": "float", "components": [0.0]},
                      "currentValue": {"kind": "float", "components": [0.5]},
                      "targetValue": {"kind": "float", "components": [1.0]},
                      "velocity": {"kind": "float", "components": [2.0]},
                      "durationNanos": 300000000,
                      "finished": false,
                      "terminalCondition": "finished"
                    },
                    {
                      "identity": "channel-2",
                      "name": "Value 2",
                      "specFamily": "spring",
                      "startValue": null,
                      "currentValue": null,
                      "targetValue": null,
                      "velocity": null,
                      "durationNanos": 900000000,
                      "finished": true,
                      "terminalCondition": "durationlimitreached"
                    }
                  ]
                }]
              }]
            }
        """.trimIndent()
    }

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
    }
}
