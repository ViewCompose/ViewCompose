package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
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
                command.startsWith("run-as com.example.app") -> validReportJson()
                else -> error("Unexpected command: $command")
            }
        }

        val report = readDeviceDslSourceReport(device)

        assertEquals("com.example.app", report.packageName)
        assertTrue(commands.last().endsWith(DEVICE_DSL_SOURCE_REPORT_PATH))
    }

    @Test
    fun `rejects report from a stopped process`() {
        val device = fakeDevice { command ->
            when {
                command.startsWith("dumpsys activity") ->
                    "topResumedActivity=ActivityRecord{a u0 com.example.app/.MainActivity t3}"
                command == "pidof com.example.app" -> "9000"
                command.startsWith("run-as com.example.app") -> validReportJson()
                else -> error("Unexpected command: $command")
            }
        }

        val failure = runCatching { readDeviceDslSourceReport(device) }.exceptionOrNull()

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

    private fun validReportJson(): String {
        return """
            {
              "protocolVersion": $DEVICE_DSL_SOURCE_PROTOCOL_VERSION,
              "packageName": "com.example.app",
              "processId": 4242,
              "generatedAtEpochMillis": 123456,
              "sessions": []
            }
        """.trimIndent()
    }
}
