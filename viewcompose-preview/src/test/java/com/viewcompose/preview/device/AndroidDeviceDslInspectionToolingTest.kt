package com.viewcompose.preview.device

import android.content.Context
import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.ui.foundation.RenderInspectedNode
import com.viewcompose.ui.foundation.RenderInspectedNodeKind
import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.foundation.RenderFailureOperation
import com.viewcompose.ui.foundation.RenderFailurePhase
import com.viewcompose.ui.foundation.RenderFailureRecovery
import com.viewcompose.ui.foundation.RenderFrameStatus
import com.viewcompose.ui.foundation.RenderNodeTimingCapture
import com.viewcompose.ui.foundation.RenderNodeTimingCaptureRequest
import com.viewcompose.ui.foundation.RenderNodeTimingCaptureResult
import com.viewcompose.ui.foundation.RenderNodeTimingCaptureStart
import com.viewcompose.ui.foundation.RenderNodeTimingEndReason
import com.viewcompose.ui.foundation.RenderNodeTimingInclusion
import com.viewcompose.ui.foundation.RenderNodeTimingPhase
import com.viewcompose.ui.foundation.RenderNodeTimingRecord
import com.viewcompose.ui.foundation.RenderNodeTimingStartStatus
import com.viewcompose.ui.foundation.RenderNodeTimingUnsupportedDomain
import com.viewcompose.ui.foundation.RenderNodePlatformTarget
import com.viewcompose.ui.foundation.RenderNodeToken
import com.viewcompose.ui.foundation.RenderSessionDiagnosticInspection
import com.viewcompose.ui.foundation.RenderSessionDiagnosticSnapshot
import com.viewcompose.ui.foundation.RenderSessionInspectedFailure
import com.viewcompose.ui.foundation.RenderSessionInspectedFrame
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.foundation.RenderSessionNodeInspection
import com.viewcompose.ui.foundation.RenderSessionTimingInspection
import com.viewcompose.ui.foundation.RenderSessionTraceId
import com.viewcompose.ui.foundation.RenderNodeInspectionSnapshot
import com.viewcompose.ui.tooling.UiSourceCallSite
import com.viewcompose.ui.node.NodeType
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidDeviceDslInspectionToolingTest {
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
            nodeInspection = emptyNodeInspection(),
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
    fun `lazy item registration remains discoverable without source candidates`() {
        val registry = AndroidDeviceDslSourceRegistry()
        val registration = registry.register(
            container = FrameLayout(applicationContext()),
            sessionId = 7,
            parentSessionId = 1,
            role = RenderSessionRole.LazyItem,
            sourceCandidates = emptyList(),
            nodeInspection = emptyNodeInspection(),
        )

        assertNotNull(registration)
        val report = registry.snapshot(
            packageName = "example",
            request = sourceRequest(),
        )
        assertEquals(1, report.sessions.size)
        assertEquals(RenderSessionRole.LazyItem, report.sessions.single().role)
        assertTrue(report.sessions.single().sourceCandidates.isEmpty())
        registration?.dispose()
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
            handler.handle(
                context,
                DeviceDslToolingRequest("bad nonce!", DeviceDslToolingOperation.Source),
            ) { finishedCount += 1 }
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
            nodeInspection = emptyNodeInspection(),
            diagnosticInspection = fixedDiagnosticInspection(),
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
            request = sourceRequest(),
            onFinished = { finishedCount += 1 },
        )

        val report = JSONObject(checkNotNull(writtenJson))
        assertEquals(1, executeCount)
        assertEquals(1, writeCount)
        assertEquals(1, finishedCount)
        assertTrue(checkNotNull(writtenFile).path.endsWith(DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH))
        assertEquals(DEVICE_DSL_SOURCE_PROTOCOL_VERSION, report.getInt("protocolVersion"))
        assertEquals(REQUEST_ID, report.getString("requestId"))
        assertEquals("source", report.getString("operation"))
        assertEquals(42, report.getInt("processId"))
        assertEquals(1, report.getJSONArray("sessions").length())
        val session = report.getJSONArray("sessions").getJSONObject(0)
        assertEquals(1L, session.getLong("sessionId"))
        assertTrue(session.isNull("parentSessionId"))
        assertEquals(RenderSessionRole.Host.name, session.getString("role"))
        val diagnostics = session.getJSONObject("diagnostics")
        assertEquals(1L, diagnostics.getLong("sessionId"))
        assertEquals(7L, diagnostics.getLong("committedFrameId"))
        assertEquals("committed", diagnostics.getJSONObject("latestFrame").getString("status"))
        val failure = diagnostics.getJSONObject("latestFailure")
        assertEquals("android_view_update", failure.getString("operation"))
        assertEquals(IllegalStateException::class.java.name, failure.getString("exceptionType"))
        assertFalse(checkNotNull(writtenJson).contains("private-message"))
        assertFalse(checkNotNull(writtenJson).contains("private-key"))
        registration?.dispose()
    }

    @Test
    fun `timing request writes one complete bounded v7 response`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry(
            processId = { 42 },
            currentTimeMillis = { 1234L },
        )
        var capturedRequest: RenderNodeTimingCaptureRequest? = null
        val result = timingResult(
            records = listOf(
                RenderNodeTimingRecord(
                    frameId = 3L,
                    nodeToken = renderNodeToken(35L),
                    parentNodeToken = null,
                    nodeType = NodeType.Text,
                    depth = 1,
                    synthetic = false,
                    sourceCallSites = sourceCandidates().single(),
                    phase = RenderNodeTimingPhase.Binding,
                    inclusion = RenderNodeTimingInclusion.Direct,
                    durationNanos = 2_500L,
                    repetitions = 1L,
                    truncated = false,
                ),
            ),
        )
        registry.register(
            container = FrameLayout(context),
            sessionId = 21L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = emptyNodeInspection(),
            timingInspection = object : RenderSessionTimingInspection {
                override fun startCapture(
                    request: RenderNodeTimingCaptureRequest,
                ): RenderNodeTimingCaptureStart {
                    capturedRequest = request
                    return RenderNodeTimingCaptureStart(
                        RenderNodeTimingStartStatus.Started,
                        fixedTimingCapture(result),
                    )
                }
            },
        )
        var writtenJson: String? = null
        var finishedCount = 0
        val handler = DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable -> runnable.run() },
            writeResponse = { _, json -> writtenJson = json },
            postDelayed = { _, _ -> error("Complete timing capture must not be polled again.") },
        )

        handler.handle(
            context = context,
            request = DeviceDslToolingRequest(
                requestId = REQUEST_ID,
                operation = DeviceDslToolingOperation.Timing,
                sessionId = 21L,
                timingPhases = setOf(
                    RenderNodeTimingPhase.Composition,
                    RenderNodeTimingPhase.Binding,
                ),
            ),
            onFinished = { finishedCount += 1 },
        )

        val json = checkNotNull(writtenJson)
        val report = JSONObject(json)
        val timing = report.getJSONObject("timing")
        val capture = timing.getJSONObject("result")
        val record = capture.getJSONArray("records").getJSONObject(0)
        assertEquals(DEVICE_DSL_SOURCE_PROTOCOL_VERSION, report.getInt("protocolVersion"))
        assertEquals("timing", report.getString("operation"))
        assertEquals("started", timing.getString("startStatus"))
        assertTrue(capture.getBoolean("complete"))
        assertEquals("frame_limit", capture.getString("endReason"))
        assertEquals("z", record.getString("nodeToken"))
        assertEquals("binding", record.getString("phase"))
        assertEquals("direct", record.getString("inclusion"))
        assertEquals(1, finishedCount)
        assertEquals(
            setOf(RenderNodeTimingPhase.Composition, RenderNodeTimingPhase.Binding),
            checkNotNull(capturedRequest).phases,
        )
        assertTrue(json.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
    }

    @Test
    fun `armed timing matches only a future lazy item under the exact parent`() {
        var nowNanos = 100L
        val registry = AndroidDeviceDslSourceRegistry(
            monotonicTimeNanos = { nowNanos },
        )
        registry.register(
            container = FrameLayout(applicationContext()),
            sessionId = 1L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = emptyNodeInspection(),
        )
        val reusedPhysicalContainer = FrameLayout(applicationContext())
        registry.register(
            container = reusedPhysicalContainer,
            sessionId = 2L,
            parentSessionId = 1L,
            role = RenderSessionRole.LazyItem,
            sourceCandidates = emptyList(),
            nodeInspection = emptyNodeInspection(),
        )

        val start = registry.startTiming(futureLazyItemTimingRequest(parentSessionId = 1L))
        assertEquals(RenderNodeTimingStartStatus.Started, start.status)
        assertNotNull(start.arm)
        assertFalse(
            registry.shouldRegisterBeforeFirstFrame(
                diagnosticContext(2L, 1L, RenderSessionRole.LazyItem),
            ),
        )
        assertFalse(
            registry.shouldRegisterBeforeFirstFrame(
                diagnosticContext(3L, 2L, RenderSessionRole.LazyItem),
            ),
        )
        assertFalse(
            registry.shouldRegisterBeforeFirstFrame(
                diagnosticContext(3L, 1L, RenderSessionRole.PagerPage),
            ),
        )
        assertTrue(
            registry.shouldRegisterBeforeFirstFrame(
                diagnosticContext(3L, 1L, RenderSessionRole.LazyItem),
            ),
        )
        registry.unregister(2L)

        var capturedRequest: RenderNodeTimingCaptureRequest? = null
        val result = timingResult(
            sessionId = 3L,
            parentSessionId = 1L,
            role = RenderSessionRole.LazyItem,
        )
        registry.register(
            container = reusedPhysicalContainer,
            sessionId = 3L,
            parentSessionId = 1L,
            role = RenderSessionRole.LazyItem,
            sourceCandidates = emptyList(),
            nodeInspection = emptyNodeInspection(),
            timingInspection = object : RenderSessionTimingInspection {
                override fun startCapture(
                    request: RenderNodeTimingCaptureRequest,
                ): RenderNodeTimingCaptureStart {
                    capturedRequest = request
                    return RenderNodeTimingCaptureStart(
                        RenderNodeTimingStartStatus.Started,
                        fixedTimingCapture(result),
                    )
                }
            },
        )

        val armed = registry.pollArmedTiming(checkNotNull(start.arm))
        assertEquals(DeviceDslTimingArmEndReason.Matched, armed.snapshot.endReason)
        assertEquals(3L, armed.snapshot.matchedSessionId)
        assertEquals(2L, armed.snapshot.matchedPhysicalContainerToken)
        assertNotNull(armed.capture)
        assertEquals(1, checkNotNull(capturedRequest).maxFrames)
        assertEquals(RenderNodeTimingPhase.entries.toSet(), capturedRequest?.phases)
        nowNanos += 1L
    }

    @Test
    fun `armed timing request writes the matched cold session response once`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry(
            processId = { 42 },
            currentTimeMillis = { 1234L },
            monotonicTimeNanos = { 100L },
        )
        registry.register(
            container = FrameLayout(context),
            sessionId = 1L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = emptyNodeInspection(),
        )
        val delayed = mutableListOf<Runnable>()
        var writtenJson: String? = null
        var finishedCount = 0
        val handler = DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable -> runnable.run() },
            writeResponse = { _, json -> writtenJson = json },
            postDelayed = { _, action -> delayed += action },
        )

        handler.handle(
            context = context,
            request = futureLazyItemTimingRequest(1L),
            onFinished = { finishedCount += 1 },
        )
        assertEquals(1, delayed.size)
        assertEquals(null, writtenJson)

        val result = timingResult(
            sessionId = 2L,
            parentSessionId = 1L,
            role = RenderSessionRole.LazyItem,
        )
        registry.register(
            container = FrameLayout(context),
            sessionId = 2L,
            parentSessionId = 1L,
            role = RenderSessionRole.LazyItem,
            sourceCandidates = emptyList(),
            nodeInspection = emptyNodeInspection(),
            timingInspection = fixedTimingInspection(result),
        )
        delayed.removeAt(0).run()

        val timing = JSONObject(checkNotNull(writtenJson)).getJSONObject("timing")
        val arm = timing.getJSONObject("arm")
        val capture = timing.getJSONObject("result")
        assertEquals("matched", arm.getString("endReason"))
        assertEquals(1L, arm.getLong("parentSessionId"))
        assertEquals(2L, arm.getLong("matchedSessionId"))
        assertTrue(arm.getLong("matchedPhysicalContainerToken") > 0L)
        assertEquals(2L, capture.getLong("sessionId"))
        assertEquals(1L, capture.getLong("parentSessionId"))
        assertEquals(RenderSessionRole.LazyItem.name, capture.getString("role"))
        assertEquals(1, finishedCount)
        assertTrue(delayed.isEmpty())
    }

    @Test
    fun `armed timing timeout parent end and supersession are explicit`() {
        var nowNanos = 100L
        val registry = AndroidDeviceDslSourceRegistry(
            monotonicTimeNanos = { nowNanos },
        )
        fun registerParent() {
            registry.register(
                container = FrameLayout(applicationContext()),
                sessionId = 1L,
                parentSessionId = null,
                role = RenderSessionRole.Host,
                sourceCandidates = sourceCandidates(),
                nodeInspection = emptyNodeInspection(),
            )
        }
        registerParent()

        val timedOut = checkNotNull(
            registry.startTiming(futureLazyItemTimingRequest(1L)).arm,
        )
        nowNanos += 10_000_000_000L
        assertEquals(
            DeviceDslTimingArmEndReason.DurationLimit,
            registry.pollArmedTiming(timedOut).snapshot.endReason,
        )

        val parentEnded = checkNotNull(
            registry.startTiming(futureLazyItemTimingRequest(1L)).arm,
        )
        registry.unregister(1L)
        assertEquals(
            DeviceDslTimingArmEndReason.ParentEnded,
            registry.pollArmedTiming(parentEnded).snapshot.endReason,
        )

        registerParent()
        val superseded = checkNotNull(
            registry.startTiming(futureLazyItemTimingRequest(1L)).arm,
        )
        val replacement = registry.startTiming(
            futureLazyItemTimingRequest(1L, requestId = "replacement-request"),
        )
        assertEquals(
            DeviceDslTimingArmEndReason.Superseded,
            registry.pollArmedTiming(superseded).snapshot.endReason,
        )
        assertNotNull(replacement.arm)
    }

    @Test
    fun `only one process timing capture may remain active`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry()
        val activeResult = timingResult(complete = false, endReason = null)
        var secondSessionStarts = 0
        registry.register(
            container = FrameLayout(context),
            sessionId = 21L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = emptyNodeInspection(),
            timingInspection = fixedTimingInspection(activeResult),
        )
        registry.register(
            container = FrameLayout(context),
            sessionId = 22L,
            parentSessionId = null,
            role = RenderSessionRole.NavigationDestination,
            sourceCandidates = sourceCandidates(),
            nodeInspection = emptyNodeInspection(),
            timingInspection = object : RenderSessionTimingInspection {
                override fun startCapture(
                    request: RenderNodeTimingCaptureRequest,
                ): RenderNodeTimingCaptureStart {
                    secondSessionStarts += 1
                    return RenderNodeTimingCaptureStart(
                        RenderNodeTimingStartStatus.Started,
                        fixedTimingCapture(activeResult),
                    )
                }
            },
        )

        val first = registry.startTiming(timingRequest(sessionId = 21L))
        val second = registry.startTiming(timingRequest(sessionId = 22L))

        assertEquals(RenderNodeTimingStartStatus.Started, first.status)
        assertEquals(RenderNodeTimingStartStatus.AlreadyActive, second.status)
        assertEquals(null, second.capture)
        assertEquals(0, secondSessionStarts)
        registry.finishTiming(checkNotNull(first.capture))
    }

    @Test
    fun `oversized timing response keeps a valid bounded record prefix`() {
        val context = applicationContext()
        val registry = AndroidDeviceDslSourceRegistry()
        val longText = "界".repeat(2_000)
        val records = List(512) { index ->
            RenderNodeTimingRecord(
                frameId = index.toLong(),
                nodeToken = renderNodeToken(index + 1L),
                parentNodeToken = null,
                nodeType = NodeType.Text,
                depth = 1,
                synthetic = false,
                sourceCallSites = List(16) {
                    UiSourceCallSite(longText, longText, longText, index + 1)
                },
                phase = RenderNodeTimingPhase.Composition,
                inclusion = RenderNodeTimingInclusion.Self,
                durationNanos = index + 1L,
                repetitions = 1L,
                truncated = false,
            )
        }
        registry.register(
            container = FrameLayout(context),
            sessionId = 21L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = emptyNodeInspection(),
            timingInspection = fixedTimingInspection(timingResult(records)),
        )
        var writtenJson: String? = null
        val handler = DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable -> runnable.run() },
            writeResponse = { _, json -> writtenJson = json },
            postDelayed = { _, _ -> error("Complete timing capture must not be polled again.") },
        )

        handler.handle(context, timingRequest()) {}

        val json = checkNotNull(writtenJson)
        val encodedRecords = JSONObject(json)
            .getJSONObject("timing")
            .getJSONObject("result")
        assertTrue(json.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
        assertTrue(encodedRecords.getBoolean("recordsTruncated"))
        assertTrue(encodedRecords.getJSONArray("records").length() in 1 until records.size)
        assertEquals("1", encodedRecords.getJSONArray("records").getJSONObject(0).getString("nodeToken"))
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
            nodeInspection = emptyNodeInspection(),
        )
        var finishedCount = 0
        val handler = DeviceDslSourceRequestHandler(
            registry = registry,
            execute = { runnable -> runnable.run() },
            writeResponse = { _, _ -> error("test writer failure") },
        )

        handler.handle(context, sourceRequest()) { finishedCount += 1 }

        assertEquals(1, finishedCount)
    }

    @Test
    fun `request nonce is strict and bounded`() {
        assertTrue(isValidDeviceDslRequestNonce(REQUEST_ID))
        assertTrue(isValidDeviceDslRequestNonce("Studio.request_1-A"))
        assertFalse(isValidDeviceDslRequestNonce(""))
        assertFalse(isValidDeviceDslRequestNonce("bad nonce"))
        assertFalse(isValidDeviceDslRequestNonce("x".repeat(129)))
    }

    @Test
    fun `node snapshot selects a real mounted view and replacement makes the old token stale`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = FrameLayout(activity)
        val target = View(activity)
        container.addView(target, FrameLayout.LayoutParams(120, 80))
        activity.setContentView(container)
        container.layout(0, 0, 300, 300)
        target.layout(24, 36, 144, 116)
        val currentToken = longArrayOf(7L)
        val registry = AndroidDeviceDslSourceRegistry()
        registry.register(
            container = container,
            sessionId = 11L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = nodeInspection(currentToken, target),
        )

        val first = registry.snapshot(
            packageName = "com.example.app",
            request = DeviceDslToolingRequest(
                requestId = REQUEST_ID,
                operation = DeviceDslToolingOperation.Nodes,
                sessionId = 11L,
            ),
        )
        assertEquals("7", first.sessions.single().nodes.single().token)
        val selected = registry.snapshot(
            packageName = "com.example.app",
            request = DeviceDslToolingRequest(
                requestId = REQUEST_ID,
                operation = DeviceDslToolingOperation.Select,
                sessionId = 11L,
                nodeToken = "7",
            ),
        )
        assertTrue(
            selected.highlight?.state in setOf(
                DeviceDslHighlightState.Selected,
                DeviceDslHighlightState.Clipped,
            ),
        )

        currentToken[0] = 8L
        registry.snapshot(
            packageName = "com.example.app",
            request = DeviceDslToolingRequest(
                requestId = REQUEST_ID,
                operation = DeviceDslToolingOperation.Nodes,
                sessionId = 11L,
            ),
        )
        val stale = registry.snapshot(
            packageName = "com.example.app",
            request = DeviceDslToolingRequest(
                requestId = REQUEST_ID,
                operation = DeviceDslToolingOperation.Select,
                sessionId = 11L,
                nodeToken = "7",
            ),
        )
        assertEquals(DeviceDslHighlightState.Stale, stale.highlight?.state)

        val cleared = registry.snapshot(
            packageName = "com.example.app",
            request = DeviceDslToolingRequest(REQUEST_ID, DeviceDslToolingOperation.Clear),
        )
        assertEquals(DeviceDslHighlightState.Cleared, cleared.highlight?.state)
    }

    @Test
    fun `synthetic hidden missing and ended selections are explicit`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = FrameLayout(activity)
        val target = View(activity)
        container.addView(target, FrameLayout.LayoutParams(100, 60))
        activity.setContentView(container)
        container.layout(0, 0, 200, 200)
        target.layout(0, 0, 100, 60)
        val registry = AndroidDeviceDslSourceRegistry()
        val registration = registry.register(
            container = container,
            sessionId = 12L,
            parentSessionId = null,
            role = RenderSessionRole.Host,
            sourceCandidates = sourceCandidates(),
            nodeInspection = nodeInspection(longArrayOf(9L), target, synthetic = true),
        )
        registry.snapshot(
            "com.example.app",
            DeviceDslToolingRequest(REQUEST_ID, DeviceDslToolingOperation.Nodes, sessionId = 12L),
        )
        val synthetic = registry.snapshot(
            "com.example.app",
            DeviceDslToolingRequest(
                REQUEST_ID,
                DeviceDslToolingOperation.Select,
                sessionId = 12L,
                nodeToken = "9",
            ),
        )
        assertEquals(DeviceDslHighlightState.UnsupportedSynthetic, synthetic.highlight?.state)
        val missing = registry.snapshot(
            "com.example.app",
            DeviceDslToolingRequest(
                REQUEST_ID,
                DeviceDslToolingOperation.Select,
                sessionId = 12L,
                nodeToken = "abc",
            ),
        )
        assertEquals(DeviceDslHighlightState.Missing, missing.highlight?.state)
        registration?.dispose()
        val ended = registry.snapshot(
            "com.example.app",
            DeviceDslToolingRequest(
                REQUEST_ID,
                DeviceDslToolingOperation.Select,
                sessionId = 12L,
                nodeToken = "9",
            ),
        )
        assertEquals(DeviceDslHighlightState.EndedSession, ended.highlight?.state)
    }

    @Test
    fun `response remains valid under the protocol byte limit`() {
        val longText = "界".repeat(2_000)
        val sessions = List(64) { sessionIndex ->
            DeviceDslSourceSessionSnapshot(
                sessionId = sessionIndex + 1L,
                parentSessionId = null,
                role = RenderSessionRole.Host,
                physicalContainerToken = sessionIndex + 1L,
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
                nodeInspection = emptyNodeInspection(),
            )
        }

        val report = registry.snapshot("com.example.app", sourceRequest())
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

    private fun sourceRequest(): DeviceDslToolingRequest {
        return DeviceDslToolingRequest(REQUEST_ID, DeviceDslToolingOperation.Source)
    }

    private fun timingRequest(sessionId: Long = 21L): DeviceDslToolingRequest {
        return DeviceDslToolingRequest(
            requestId = REQUEST_ID,
            operation = DeviceDslToolingOperation.Timing,
            sessionId = sessionId,
            timingPhases = RenderNodeTimingPhase.entries.toSet(),
        )
    }

    private fun futureLazyItemTimingRequest(
        parentSessionId: Long,
        requestId: String = REQUEST_ID,
    ): DeviceDslToolingRequest {
        return DeviceDslToolingRequest(
            requestId = requestId,
            operation = DeviceDslToolingOperation.Timing,
            sessionId = parentSessionId,
            timingPhases = RenderNodeTimingPhase.entries.toSet(),
            futureLazyItemTiming = true,
        )
    }

    private fun diagnosticContext(
        sessionId: Long,
        parentSessionId: Long?,
        role: RenderSessionRole,
    ): RenderDiagnosticContext = RenderDiagnosticContext(
        sessionId = renderSessionTraceId(sessionId),
        parentSessionId = parentSessionId?.let(::renderSessionTraceId),
        role = role,
        frameId = null,
        eventSequence = 0L,
        monotonicTimestampNanos = 100L,
    )

    private fun emptyNodeInspection(): RenderSessionNodeInspection {
        return object : RenderSessionNodeInspection {
            override fun snapshot(): RenderNodeInspectionSnapshot {
                return RenderNodeInspectionSnapshot(
                    nodes = emptyList(),
                    visitedNodes = 0,
                    droppedNodes = 0,
                    truncated = false,
                    supported = true,
                    ended = false,
                )
            }
        }
    }

    private fun nodeInspection(
        token: LongArray,
        target: View,
        synthetic: Boolean = false,
    ): RenderSessionNodeInspection {
        return object : RenderSessionNodeInspection {
            override fun snapshot(): RenderNodeInspectionSnapshot {
                return RenderNodeInspectionSnapshot(
                    nodes = listOf(
                        RenderInspectedNode(
                            token = renderNodeToken(token[0]),
                            parentToken = null,
                            type = NodeType.Text,
                            depth = 0,
                            kind = if (synthetic) {
                                RenderInspectedNodeKind.Synthetic
                            } else {
                                RenderInspectedNodeKind.Declarative
                            },
                            sourceCallSites = sourceCandidates().single(),
                            platformTarget = RenderNodePlatformTarget { target },
                        ),
                    ),
                    visitedNodes = 1,
                    droppedNodes = 0,
                    truncated = false,
                    supported = true,
                    ended = false,
                )
            }
        }
    }

    private fun renderNodeToken(value: Long): RenderNodeToken {
        val method = RenderNodeToken::class.java.getDeclaredMethod("box-impl", Long::class.javaPrimitiveType)
        return method.invoke(null, value) as RenderNodeToken
    }

    private fun renderSessionTraceId(value: Long): RenderSessionTraceId {
        val method = RenderSessionTraceId::class.java.getDeclaredMethod(
            "box-impl",
            Long::class.javaPrimitiveType,
        )
        return method.invoke(null, value) as RenderSessionTraceId
    }

    private fun fixedDiagnosticInspection(): RenderSessionDiagnosticInspection {
        val failure = RenderSessionInspectedFailure(
            frameId = 7L,
            phase = RenderFailurePhase.ViewTreeRender,
            recovery = RenderFailureRecovery.FrameCommitted,
            operation = RenderFailureOperation.AndroidViewUpdate,
            exceptionType = IllegalStateException::class.java.name,
        )
        return object : RenderSessionDiagnosticInspection {
            override fun snapshot(): RenderSessionDiagnosticSnapshot {
                return RenderSessionDiagnosticSnapshot(
                    sessionId = renderSessionTraceId(1L),
                    parentSessionId = null,
                    role = RenderSessionRole.Host,
                    renderingActive = true,
                    committedFrameId = 7L,
                    latestFrame = RenderSessionInspectedFrame(
                        frameId = 7L,
                        status = RenderFrameStatus.Committed,
                        failures = listOf(failure),
                        droppedFailures = 0,
                    ),
                    latestFailure = failure,
                    ended = false,
                )
            }
        }
    }

    private fun timingResult(
        records: List<RenderNodeTimingRecord> = emptyList(),
        complete: Boolean = true,
        endReason: RenderNodeTimingEndReason? = RenderNodeTimingEndReason.FrameLimit,
        sessionId: Long = 21L,
        parentSessionId: Long? = null,
        role: RenderSessionRole = RenderSessionRole.Host,
    ): RenderNodeTimingCaptureResult {
        return RenderNodeTimingCaptureResult(
            context = RenderDiagnosticContext(
                sessionId = renderSessionTraceId(sessionId),
                parentSessionId = parentSessionId?.let(::renderSessionTraceId),
                role = role,
                frameId = null,
                eventSequence = 0L,
                monotonicTimestampNanos = 100L,
            ),
            records = records,
            completedFrames = 1,
            startedAtNanos = 100L,
            endedAtNanos = 200L,
            attemptedClockReads = 2L,
            retainedClockReads = 2L,
            emptyPairOverheadNanos = 3L,
            droppedTimedNodes = 0,
            droppedRecords = 0,
            droppedStrings = 0,
            truncated = false,
            unsupportedDomains = RenderNodeTimingUnsupportedDomain.entries.toSet(),
            complete = complete,
            endReason = endReason,
        )
    }

    private fun fixedTimingInspection(
        result: RenderNodeTimingCaptureResult,
    ): RenderSessionTimingInspection = object : RenderSessionTimingInspection {
        override fun startCapture(
            request: RenderNodeTimingCaptureRequest,
        ): RenderNodeTimingCaptureStart = RenderNodeTimingCaptureStart(
            RenderNodeTimingStartStatus.Started,
            fixedTimingCapture(result),
        )
    }

    private fun fixedTimingCapture(
        result: RenderNodeTimingCaptureResult,
    ): RenderNodeTimingCapture = object : RenderNodeTimingCapture {
        override fun snapshot(): RenderNodeTimingCaptureResult = result

        override fun stop(): RenderNodeTimingCaptureResult = result
    }

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
    }
}
