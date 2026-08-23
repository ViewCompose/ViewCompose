package com.viewcompose.preview.device

import android.content.Context
import android.widget.FrameLayout
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.tooling.UiSourceCallSite
import java.io.File
import java.util.ServiceLoader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidDeviceDslSourceToolingTest {
    @Test
    fun `service is discoverable only from the optional preview artifact`() {
        val providers = ServiceLoader.load(
            RenderSessionSourceTooling::class.java,
            RenderSessionSourceTooling::class.java.classLoader,
        ).toList()

        assertTrue(
            providers.any { provider -> provider is AndroidDeviceDslSourceTooling },
        )
    }

    @Test
    fun `session changes remain passive until an explicit request`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry()
        var executeCount = 0
        var writeCount = 0
        DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable ->
                executeCount += 1
                runnable.run()
            },
            writeResponse = { _, _ -> writeCount += 1 },
        )
        val container = FrameLayout(context)
        val registration = registry.register(
            container = container,
            sessionId = 1,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
        )

        repeat(100) { index ->
            registration?.setRenderingActive(index % 2 == 0)
            container.layout(0, 0, 100 + index, 200 + index)
            container.scrollTo(0, index)
        }
        registration?.dispose()

        assertEquals(0, executeCount)
        assertEquals(0, writeCount)
        assertEquals(0, registry.sessionCountForTest())
    }

    @Test
    fun `invalid request never schedules or writes a response`() {
        val context = applicationContext()
        var executeCount = 0
        var writeCount = 0
        var finishedCount = 0
        val handler = DeviceDslSourceRequestHandler(
            registry = AndroidDeviceDslSourceRegistry(),
            execute = {
                executeCount += 1
                it.run()
            },
            writeResponse = { _, _ -> writeCount += 1 },
        )

        val failure = runCatching {
            handler.handle(context, "invalid") { finishedCount += 1 }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(0, executeCount)
        assertEquals(0, writeCount)
        assertEquals(0, finishedCount)
    }

    @Test
    fun `one valid request writes one matching response`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry(
            processId = { 42 },
            currentTimeMillis = { 1234L },
        )
        val registration = registry.register(
            container = FrameLayout(context),
            sessionId = 1,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
        )
        var executeCount = 0
        var writeCount = 0
        var finishedCount = 0
        var writtenFile: File? = null
        var writtenJson: String? = null
        val handler = DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable ->
                executeCount += 1
                runnable.run()
            },
            writeResponse = { file, json ->
                writeCount += 1
                writtenFile = file
                writtenJson = json
            },
        )

        handler.handle(
            context = context,
            requestId = REQUEST_ID,
            onFinished = { finishedCount += 1 },
        )

        val report = JSONObject(checkNotNull(writtenJson))
        assertEquals(1, executeCount)
        assertEquals(1, writeCount)
        assertEquals(1, finishedCount)
        assertTrue(checkNotNull(writtenFile).path.endsWith(DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH))
        assertEquals(DEVICE_DSL_SOURCE_PROTOCOL_VERSION, report.getInt("protocolVersion"))
        assertEquals(REQUEST_ID, report.getString("requestId"))
        assertEquals(42, report.getInt("processId"))
        assertEquals(1, report.getJSONArray("sessions").length())
        val session = report.getJSONArray("sessions").getJSONObject(0)
        assertEquals(1L, session.getLong("sessionId"))
        assertTrue(session.isNull("parentSessionId"))
        assertEquals(RenderSessionRole.Host.name, session.getString("role"))
        registration?.dispose()
    }

    @Test
    fun `response completion survives writer failure`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry()
        registry.register(
            container = FrameLayout(context),
            sessionId = 1,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
        )
        var finishedCount = 0
        val handler = DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable -> runnable.run() },
            writeResponse = { _, _ -> error("test writer failure") },
        )

        handler.handle(context, REQUEST_ID) { finishedCount += 1 }

        assertEquals(1, finishedCount)
    }

    @Test
    fun `request nonce is strict and bounded`() {
        assertTrue(isValidDeviceDslSourceRequestId(REQUEST_ID))
        assertFalse(isValidDeviceDslSourceRequestId("ABCDEF0123456789ABCDEF0123456789"))
        assertFalse(isValidDeviceDslSourceRequestId("0123"))
        assertFalse(isValidDeviceDslSourceRequestId("g123456789abcdef0123456789abcdef"))
    }

    @Test
    fun `response remains valid under the protocol byte limit`() {
        val longText = "界".repeat(2_000)
        val sessions = List(64) { sessionIndex ->
            DeviceDslSourceSessionSnapshot(
                sessionId = sessionIndex + 1L,
                parentSessionId = null,
                role = RenderSessionRole.Host,
                renderingActive = true,
                attachedToWindow = true,
                shown = true,
                hasWindowFocus = true,
                windowVisibility = 0,
                viewDepth = sessionIndex,
                sourceCandidates = List(32) {
                    List(24) {
                        UiSourceCallSite(longText, longText, longText, 1)
                    }
                },
            )
        }

        val json = deviceDslSourceReportJson(
            requestId = REQUEST_ID,
            packageName = "com.example.app",
            processId = 42,
            generatedAtEpochMillis = 1L,
            sessions = sessions,
        )

        assertTrue(json.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
        assertEquals(DEVICE_DSL_SOURCE_PROTOCOL_VERSION, JSONObject(json).getInt("protocolVersion"))
    }

    @Test
    fun `utf8 size matches platform encoding without allocating encoded bytes`() {
        val inputs = listOf(
            "plain ASCII",
            "Grüße",
            "时间线",
            "timeline 🚀",
            "broken-high-\uD800",
            "broken-low-\uDC00",
        )

        inputs.forEach { input ->
            assertEquals(input.toByteArray(Charsets.UTF_8).size, input.utf8Size())
            val wrapped = "prefix-$input-suffix"
            assertEquals(
                input.toByteArray(Charsets.UTF_8).size,
                wrapped.utf8Size("prefix-".length, wrapped.length - "-suffix".length),
            )
        }
    }

    @Test
    fun `registry bounds retained sessions and source strings`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry()
        val containers = List(80) { FrameLayout(context) }
        val longText = "x".repeat(2_000)
        containers.forEachIndexed { index, container ->
            registry.register(
                container = container,
                sessionId = index + 1L,
                parentSessionId = null,
                role = RenderSessionRole.Host,
                sourceCandidates = listOf(
                    listOf(UiSourceCallSite(longText, longText, longText, 1)),
                ),
            )
        }

        val report = registry.snapshot("com.example.app", REQUEST_ID)
        val source = report.sessions.first().sourceCandidates.first().first()

        assertEquals(64, registry.sessionCountForTest())
        assertEquals(64, report.sessions.size)
        assertEquals(1_024, source.className.length)
        assertEquals(1_024, source.methodName.length)
        assertEquals(1_024, source.fileName.length)
    }

    @Test
    fun `manifest receiver is exported only behind the dump permission`() {
        val context = applicationContext()
        val component = android.content.ComponentName(
            context,
            DeviceDslSourceRequestReceiver::class.java,
        )
        val receiver = context.packageManager.getReceiverInfo(component, 0)

        assertTrue(receiver.exported)
        assertEquals("android.permission.DUMP", receiver.permission)
        assertNotNull(receiver.name)
    }

    private fun applicationContext(): Context = RuntimeEnvironment.getApplication()

    private fun sourceCandidates(): List<List<UiSourceCallSite>> {
        return listOf(
            listOf(
                UiSourceCallSite(
                    className = "com.example.PageKt",
                    methodName = "Page",
                    fileName = "Page.kt",
                    lineNumber = 27,
                ),
            ),
        )
    }

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
    }
}
