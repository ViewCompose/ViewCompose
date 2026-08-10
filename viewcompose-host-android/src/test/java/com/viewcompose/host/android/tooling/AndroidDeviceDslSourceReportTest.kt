package com.viewcompose.host.android.tooling

import com.viewcompose.ui.tooling.UiSourceCallSite
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidDeviceDslSourceReportTest {
    @Test
    fun `report encodes source identity and live session state`() {
        val json = deviceDslSourceReportJson(
            packageName = "com.example.app",
            processId = 42,
            generatedAtEpochMillis = 1234L,
            sessions = listOf(
                DeviceDslSourceSessionSnapshot(
                    sessionId = 7L,
                    renderingActive = true,
                    attachedToWindow = true,
                    shown = true,
                    hasWindowFocus = false,
                    windowVisibility = 0,
                    viewDepth = 3,
                    sourceCandidates = listOf(
                        listOf(
                            UiSourceCallSite(
                                className = "com.example.PageKt",
                                methodName = "SettingsPage",
                                fileName = "SettingsPage.kt",
                                lineNumber = 27,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val report = JSONObject(json)
        assertEquals(DEVICE_DSL_SOURCE_PROTOCOL_VERSION, report.getInt("protocolVersion"))
        assertEquals("com.example.app", report.getString("packageName"))
        assertEquals(42, report.getInt("processId"))
        assertEquals(1234L, report.getLong("generatedAtEpochMillis"))
        val session = report.getJSONArray("sessions").getJSONObject(0)
        assertTrue(session.getBoolean("renderingActive"))
        assertTrue(session.getBoolean("attachedToWindow"))
        assertTrue(session.getBoolean("shown"))
        assertFalse(session.getBoolean("hasWindowFocus"))
        assertEquals(3, session.getInt("viewDepth"))
        val source = session
            .getJSONArray("sourceCandidates")
            .getJSONObject(0)
            .getJSONArray("callSites")
            .getJSONObject(0)
        assertEquals("SettingsPage.kt", source.getString("fileName"))
        assertEquals(27, source.getInt("lineNumber"))
    }

    @Test
    fun `report escapes source strings as json`() {
        val json = deviceDslSourceReportJson(
            packageName = "com.example.app",
            processId = 1,
            generatedAtEpochMillis = 2L,
            sessions = listOf(
                DeviceDslSourceSessionSnapshot(
                    sessionId = 1L,
                    renderingActive = false,
                    attachedToWindow = false,
                    shown = false,
                    hasWindowFocus = false,
                    windowVisibility = 8,
                    viewDepth = 0,
                    sourceCandidates = listOf(
                        listOf(
                            UiSourceCallSite(
                                className = "com.example.Quote\"Page",
                                methodName = "line\nbreak",
                                fileName = "Page\\Name.kt",
                                lineNumber = 9,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val source = JSONObject(json)
            .getJSONArray("sessions")
            .getJSONObject(0)
            .getJSONArray("sourceCandidates")
            .getJSONObject(0)
            .getJSONArray("callSites")
            .getJSONObject(0)
        assertEquals("com.example.Quote\"Page", source.getString("className"))
        assertEquals("line\nbreak", source.getString("methodName"))
        assertEquals("Page\\Name.kt", source.getString("fileName"))
    }
}
