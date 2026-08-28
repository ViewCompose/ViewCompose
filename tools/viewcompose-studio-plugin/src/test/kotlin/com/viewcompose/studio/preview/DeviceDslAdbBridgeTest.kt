package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDslAdbBridgeTest {
    @Test
    fun `prefers the top resumed activity package`() {
        val packageName = parseForegroundPackage(
            activityDump = """
                Display #0 activities:
                  topResumedActivity=ActivityRecord{b56 u0 com.example.demo/.MainActivity t42}
            """.trimIndent(),
            windowDump = { error("window fallback should not run") },
        )

        assertEquals("com.example.demo", packageName)
    }

    @Test
    fun `falls back to the focused window package`() {
        val packageName = parseForegroundPackage(
            activityDump = "No resumed activity",
            windowDump = {
                "mCurrentFocus=Window{84 u0 com.example.fallback/com.example.fallback.MainActivity}"
            },
        )

        assertEquals("com.example.fallback", packageName)
    }

    @Test
    fun `returns null when no component is focused`() {
        assertNull(
            parseForegroundPackage(
                activityDump = "empty",
                windowDump = { "empty" },
            ),
        )
    }

    @Test
    fun `parses every positive process id`() {
        assertEquals(setOf(123, 456), parseProcessIds("123  456\nnot-a-pid -2"))
    }

    @Test
    fun `reads report for the foreground package and live process`() {
        val commands = mutableListOf<String>()
        val device = fakeDevice { command ->
            commands += command
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242 4243"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") -> validReportJson(REQUEST_ID)
                else -> error("Unexpected command: $command")
            }
        }

        val report = readDeviceDslSourceReport(
            device = device,
            requestIdFactory = { REQUEST_ID },
            sleep = {},
        )

        assertEquals("com.example.app", report.packageName)
        assertTrue(commands.any { command -> command.contains("--es request_id $REQUEST_ID") })
        assertTrue(commands.any { command -> command.contains("--es operation source") })
        assertTrue(commands.last().endsWith(DEVICE_DSL_SOURCE_REPORT_PATH))
    }

    @Test
    fun `rejects report from a stopped process`() {
        val device = fakeDevice { command ->
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "9000"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") -> validReportJson(REQUEST_ID)
                else -> error("Unexpected command: $command")
            }
        }

        val failure = runCatching {
            readDeviceDslSourceReport(
                device = device,
                requestIdFactory = { REQUEST_ID },
                sleep = {},
            )
        }.exceptionOrNull()

        assertEquals(
            DeviceDslLocateFailureReason.StaleReport,
            (failure as DeviceDslLocateFailure).reason,
        )
    }

    private fun fakeDevice(shell: (String) -> String): StudioAndroidDevice {
        return StudioAndroidDevice(
            serialNumber = "serial-1",
            displayName = "Test phone",
            androidVersion = "16",
            emulator = false,
            shellExecutor = shell,
        )
    }

    @Test
    fun `rejects a stale response nonce`() {
        var currentNanos = 0L
        val device = fakeDevice { command ->
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") -> validReportJson(STALE_REQUEST_ID)
                else -> error("Unexpected command: $command")
            }
        }

        val failure = runCatching {
            readDeviceDslSourceReport(
                device = device,
                requestIdFactory = { REQUEST_ID },
                sleep = { millis -> currentNanos += millis * 1_000_000L },
                nanoTime = { currentNanos },
            )
        }.exceptionOrNull()

        assertEquals(
            DeviceDslLocateFailureReason.StaleReport,
            (failure as DeviceDslLocateFailure).reason,
        )
    }

    @Test
    fun `polls past a stale response and accepts the matching nonce`() {
        var reportReads = 0
        var currentNanos = 0L
        val device = fakeDevice { command ->
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") -> {
                    reportReads += 1
                    validReportJson(
                        if (reportReads == 1) STALE_REQUEST_ID else REQUEST_ID,
                    )
                }
                else -> error("Unexpected command: $command")
            }
        }

        val report = readDeviceDslSourceReport(
            device = device,
            requestIdFactory = { REQUEST_ID },
            sleep = { millis -> currentNanos += millis * 1_000_000L },
            nanoTime = { currentNanos },
        )

        assertEquals(REQUEST_ID, report.requestId)
        assertEquals(2, reportReads)
    }

    @Test
    fun `times out when the optional device receiver is absent`() {
        var currentNanos = 0L
        val device = fakeDevice { command ->
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") ->
                    "cat: $DEVICE_DSL_SOURCE_REPORT_PATH: No such file or directory"
                else -> error("Unexpected command: $command")
            }
        }

        val failure = runCatching {
            readDeviceDslSourceReport(
                device = device,
                requestIdFactory = { REQUEST_ID },
                sleep = { millis -> currentNanos += millis * 1_000_000L },
                nanoTime = { currentNanos },
            )
        }.exceptionOrNull()

        assertEquals(
            DeviceDslLocateFailureReason.ReportUnavailable,
            (failure as DeviceDslLocateFailure).reason,
        )
    }

    @Test
    fun `select request carries session and token and validates matching operation`() {
        var broadcastRequestId: String? = null
        val commands = mutableListOf<String>()
        val device = fakeDevice { command ->
            commands += command
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> {
                    broadcastRequestId = Regex("--es request_id ([a-f0-9]{32})")
                        .find(command)?.groupValues?.get(1)
                    "Broadcast completed: result=0"
                }
                command.startsWith("run-as com.example.app") -> """
                    {
                      "protocolVersion": $DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
                      "requestId": "${checkNotNull(broadcastRequestId)}",
                      "operation": "select",
                      "packageName": "com.example.app",
                      "processId": 4242,
                      "generatedAtEpochMillis": 123456,
                      "sessions": [],
                      "highlight": {
                        "state": "selected",
                        "sessionId": 9,
                        "nodeToken": "abc",
                        "screenBounds": null,
                        "visibleBounds": null
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected command: $command")
            }
        }

        val result = selectDeviceDslNode(device, sessionId = 9L, nodeToken = "abc")

        assertEquals(StudioDeviceDslHighlightState.Selected, result.state)
        assertTrue(commands.any { it.contains("--es operation select") })
        assertTrue(commands.any { it.contains("--el session_id 9") })
        assertTrue(commands.any { it.contains("--es node_token abc") })
    }

    @Test
    fun `timing request carries sorted phases and reads a terminal result`() {
        var broadcastRequestId: String? = null
        val commands = mutableListOf<String>()
        val device = fakeDevice { command ->
            commands += command
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> {
                    broadcastRequestId = Regex("--es request_id ([a-f0-9]{32})")
                        .find(command)?.groupValues?.get(1)
                    "Broadcast completed: result=0"
                }
                command.startsWith("run-as com.example.app") -> timingReportJson(
                    checkNotNull(broadcastRequestId),
                )
                else -> error("Unexpected command: $command")
            }
        }

        val result = readDeviceDslTimingReport(
            device = device,
            sessionId = 9L,
            phases = setOf(
                StudioDeviceDslTimingPhase.Binding,
                StudioDeviceDslTimingPhase.Composition,
            ),
        )

        assertEquals(StudioDeviceDslTimingStartStatus.Started, result.startStatus)
        assertTrue(checkNotNull(result.result).complete)
        assertTrue(commands.any { it.contains("--es operation timing") })
        assertTrue(commands.any { it.contains("--el session_id 9") })
        assertTrue(commands.any {
            it.contains("--es timing_phases composition,binding")
        })
    }

    @Test
    fun `future lazy timing arms the exact parent without exposing an item key`() {
        var broadcastRequestId: String? = null
        val commands = mutableListOf<String>()
        val device = fakeDevice { command ->
            commands += command
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> {
                    broadcastRequestId = Regex("--es request_id ([a-f0-9]{32})")
                        .find(command)?.groupValues?.get(1)
                    "Broadcast completed: result=0"
                }
                command.startsWith("run-as com.example.app") -> timingReportJson(
                    checkNotNull(broadcastRequestId),
                )
                else -> error("Unexpected command: $command")
            }
        }

        val result = readFutureLazyItemTimingReport(
            device = device,
            parentSessionId = 9L,
        )

        assertTrue(checkNotNull(result.result).complete)
        assertTrue(commands.any { it.contains("--el session_id 9") })
        assertTrue(commands.any {
            it.contains("--ez timing_future_lazy_item true")
        })
        assertFalse(commands.any { it.contains("item_key") })
    }

    private fun validReportJson(requestId: String): String {
        return """
            {
              "protocolVersion": $DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
              "requestId": "$requestId",
              "operation": "source",
              "packageName": "com.example.app",
              "processId": 4242,
              "generatedAtEpochMillis": 123456,
              "sessions": []
            }
        """.trimIndent()
    }

    private fun timingReportJson(requestId: String): String = """
        {
          "protocolVersion": $DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
          "requestId": "$requestId",
          "operation": "timing",
          "packageName": "com.example.app",
          "processId": 4242,
          "generatedAtEpochMillis": 123456,
          "sessions": [],
          "timing": {
            "startStatus": "started",
            "result": {
              "sessionId": 9,
              "parentSessionId": null,
              "role": "Host",
              "clock": "monotonic_nanoseconds",
              "completedFrames": 1,
              "startedAtNanos": 100,
              "endedAtNanos": 200,
              "attemptedClockReads": 2,
              "retainedClockReads": 2,
              "emptyPairOverheadNanos": 1,
              "droppedTimedNodes": 0,
              "droppedRecords": 0,
              "droppedStrings": 0,
              "truncated": false,
              "complete": true,
              "endReason": "frame_limit",
              "unsupportedDomains": ["gpu"],
              "records": [],
              "recordsTruncated": false
            }
          }
        }
    """.trimIndent()

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
        const val STALE_REQUEST_ID = "fedcba9876543210fedcba9876543210"
    }
}
