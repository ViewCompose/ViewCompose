package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTimelineAdbBridgeTest {
    @Test
    fun `requests a selected bounded capture from the foreground process`() {
        val commands = mutableListOf<String>()
        val device = fakeDevice { command ->
            commands += command
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") -> reportJson(REQUEST_ID)
                else -> error("Unexpected command: $command")
            }
        }

        val report = readDeviceAnimationTimelineReport(
            device = device,
            mode = ANIMATION_TIMELINE_CAPTURE_MODE,
            transitionId = "transition-1",
            requestIdFactory = { REQUEST_ID },
            sleep = {},
        )

        assertEquals(REQUEST_ID, report.requestId)
        assertTrue(commands.any { command -> command.contains("--es mode capture") })
        assertTrue(commands.any { command -> command.contains("--es transition_id transition-1") })
        assertTrue(commands.last().endsWith(ANIMATION_TIMELINE_REPORT_PATH))
    }

    @Test
    fun `stale nonce is rejected after the bounded polling window`() {
        var currentNanos = 0L
        val device = fakeDevice { command ->
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "4242"
                command.startsWith("am broadcast") -> "Broadcast completed: result=0"
                command.startsWith("run-as com.example.app") -> reportJson(STALE_REQUEST_ID)
                else -> error("Unexpected command: $command")
            }
        }

        val failure = runCatching {
            readDeviceAnimationTimelineReport(
                device = device,
                mode = ANIMATION_TIMELINE_DISCOVER_MODE,
                requestIdFactory = { REQUEST_ID },
                sleep = { millis -> currentNanos += millis * 1_000_000L },
                nanoTime = { currentNanos },
            )
        }.exceptionOrNull() as AnimationTimelineInspectFailure

        assertEquals(AnimationTimelineInspectFailureReason.StaleReport, failure.reason)
    }

    @Test
    fun `capture rejects an identity before invoking the device shell`() {
        var shellCount = 0
        val failure = runCatching {
            readDeviceAnimationTimelineReport(
                device = fakeDevice {
                    shellCount += 1
                    ""
                },
                mode = ANIMATION_TIMELINE_CAPTURE_MODE,
                transitionId = "transition/1",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, shellCount)
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

    private fun reportJson(requestId: String): String {
        return """
            {
              "protocolVersion": 1,
              "requestId": "$requestId",
              "packageName": "com.example.app",
              "processId": 4242,
              "generatedAtEpochMillis": 123456,
              "mode": "capture",
              "status": "success",
              "transitions": []
            }
        """.trimIndent()
    }

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
        const val STALE_REQUEST_ID = "fedcba9876543210fedcba9876543210"
    }
}
