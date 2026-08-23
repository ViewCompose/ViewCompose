package com.viewcompose.preview.device

import android.content.ComponentName
import android.content.Context
import com.viewcompose.animation.tooling.AnimationTimelineChannelSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineRunState
import com.viewcompose.animation.tooling.AnimationTimelineSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineSource
import com.viewcompose.animation.tooling.AnimationTimelineSpecFamily
import com.viewcompose.animation.tooling.AnimationTimelineStateSummary
import com.viewcompose.animation.tooling.AnimationTimelineTerminalCondition
import com.viewcompose.animation.tooling.AnimationTimelineTooling
import com.viewcompose.animation.tooling.AnimationTimelineValue
import com.viewcompose.animation.tooling.AnimationTimelineValueKind
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
class AndroidAnimationTimelineToolingTest {
    @Test
    fun `service is discoverable only from the optional preview artifact`() {
        val providers = ServiceLoader.load(
            AnimationTimelineTooling::class.java,
            AnimationTimelineTooling::class.java.classLoader,
        ).toList()

        assertTrue(providers.any { provider -> provider is AndroidAnimationTimelineTooling })
    }

    @Test
    fun `provider retains only a weak source before the debuggable request gate`() {
        AndroidDeviceToolingDebugGate.resetForTest()
        val provider = AndroidAnimationTimelineTooling()
        val source = MutableTimelineSource("transition-1")
        val registration = checkNotNull(provider.register(source))

        assertFalse(AndroidDeviceToolingDebugGate.isDebuggable())
        assertFalse(registration.captureRequested())
        assertEquals(0, source.snapshotCount)

        AndroidDeviceToolingDebugGate.markAndGet(applicationContext())
        assertTrue(AndroidDeviceToolingDebugGate.isDebuggable())
        assertEquals(0, source.snapshotCount)
    }

    @Test
    fun `one selected capture records bounded samples and matching nonce`() {
        val context = applicationContext()
        val registry = AndroidAnimationTimelineRegistry(
            processId = { 42 },
            currentTimeMillis = { 1_234L },
        )
        val source = MutableTimelineSource("transition-1")
        val registration = registry.register(source)
        var delayed: Runnable? = null
        var writtenFile: File? = null
        var writtenJson: String? = null
        var finishedCount = 0
        val handler = AnimationTimelineRequestHandler(
            registry = registry,
            postDelayed = { delay, action ->
                assertEquals(500L, delay)
                delayed = action
            },
            execute = Runnable::run,
            writeResponse = { file, json ->
                writtenFile = file
                writtenJson = json
            },
        )

        handler.handle(
            context = context,
            requestId = REQUEST_ID,
            mode = ANIMATION_TIMELINE_CAPTURE_MODE,
            transitionId = source.identity,
            onFinished = { finishedCount += 1 },
        )
        assertTrue(registration.captureRequested())
        source.sample = timelineSnapshot(source.identity, playTimeNanos = 80_000_000L)
        registration.record(source.sample)
        checkNotNull(delayed).run()

        val report = JSONObject(checkNotNull(writtenJson))
        assertEquals(REQUEST_ID, report.getString("requestId"))
        assertEquals("capture", report.getString("mode"))
        assertEquals("success", report.getString("status"))
        assertEquals(42, report.getInt("processId"))
        assertEquals(1, report.getJSONArray("transitions").length())
        assertEquals(
            2,
            report.getJSONArray("transitions").getJSONObject(0).getJSONArray("samples").length(),
        )
        assertTrue(checkNotNull(writtenFile).path.endsWith(ANIMATION_TIMELINE_REPORT_RELATIVE_PATH))
        assertEquals(1, finishedCount)
        assertFalse(registration.captureRequested())
    }

    @Test
    fun `discovery is a single snapshot and does not enable frame recording`() {
        val registry = AndroidAnimationTimelineRegistry()
        val source = MutableTimelineSource("transition-2")
        val registration = registry.register(source)
        val report = registry.discover("com.example.app", REQUEST_ID)

        assertEquals(AnimationTimelineReportStatus.Success, report.status)
        assertEquals(1, report.transitions.size)
        assertEquals(1, report.transitions.single().samples.size)
        assertFalse(registration.captureRequested())
    }

    @Test
    fun `capture coalesces channel commits from the same logical frame`() {
        val registry = AndroidAnimationTimelineRegistry()
        val source = MutableTimelineSource("transition-coalesced")
        val registration = registry.register(source)
        val active = registry.beginCapture(
            "com.example.app",
            REQUEST_ID,
            source.identity,
        ) as TimelineCaptureStart.Active

        source.sample = timelineSnapshot(source.identity, playTimeNanos = 0L).copy(
            currentState = AnimationTimelineStateSummary("boolean", "same-frame-update"),
        )
        registration.record(source.sample)
        source.sample = timelineSnapshot(source.identity, playTimeNanos = 16_000_000L)
        registration.record(source.sample)
        val report = registry.finishCapture(
            token = active.token,
            packageName = "com.example.app",
            requestId = REQUEST_ID,
        )

        assertEquals(2, report.transitions.single().samples.size)
        assertEquals(
            "same-frame-update",
            report.transitions.single().samples.first().currentState.displayValue,
        )
    }

    @Test
    fun `missing busy and stale captures preserve request identity`() {
        val registry = AndroidAnimationTimelineRegistry()
        registry.register(MutableTimelineSource("transition-1"))
        val missing = registry.beginCapture(
            "com.example.app",
            REQUEST_ID,
            "transition-2",
        ) as TimelineCaptureStart.Immediate
        val active = registry.beginCapture(
            "com.example.app",
            REQUEST_ID,
            "transition-1",
        ) as TimelineCaptureStart.Active
        val busy = registry.beginCapture(
            "com.example.app",
            SECOND_REQUEST_ID,
            "transition-1",
        ) as TimelineCaptureStart.Immediate
        val stale = registry.finishCapture(
            token = active.token + 1L,
            packageName = "com.example.app",
            requestId = SECOND_REQUEST_ID,
        )

        assertEquals(AnimationTimelineReportStatus.Missing, missing.report.status)
        assertEquals(AnimationTimelineReportStatus.Busy, busy.report.status)
        assertEquals(AnimationTimelineReportStatus.Stale, stale.status)
        assertEquals(SECOND_REQUEST_ID, stale.requestId)
    }

    @Test
    fun `request identifiers and transition identities are strict`() {
        assertTrue(isValidAnimationTimelineIdentity("transition-123"))
        assertFalse(isValidAnimationTimelineIdentity("transition/123"))
        assertFalse(isValidAnimationTimelineIdentity("x".repeat(257)))

        val handler = AnimationTimelineRequestHandler(
            registry = AndroidAnimationTimelineRegistry(),
            execute = Runnable::run,
            writeResponse = { _, _ -> error("must not write") },
        )
        assertTrue(
            runCatching {
                handler.handle(
                    context = applicationContext(),
                    requestId = "invalid",
                    mode = ANIMATION_TIMELINE_DISCOVER_MODE,
                    transitionId = null,
                    onFinished = {},
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                handler.handle(
                    context = applicationContext(),
                    requestId = REQUEST_ID,
                    mode = ANIMATION_TIMELINE_CAPTURE_MODE,
                    transitionId = "transition/1",
                    onFinished = {},
                )
            }.isFailure,
        )
    }

    @Test
    fun `manifest receiver is exported only behind the dump permission`() {
        val context = applicationContext()
        val component = ComponentName(context, AnimationTimelineRequestReceiver::class.java)
        val receiver = context.packageManager.getReceiverInfo(component, 0)

        assertTrue(receiver.exported)
        assertEquals("android.permission.DUMP", receiver.permission)
        assertNotNull(receiver.name)
    }

    private fun applicationContext(): Context = RuntimeEnvironment.getApplication()

    private class MutableTimelineSource(
        override val identity: String,
    ) : AnimationTimelineSource {
        override val label: String = "panel"
        var sample: AnimationTimelineSnapshot = timelineSnapshot(identity, 0L)
        var snapshotCount: Int = 0

        override fun snapshot(): AnimationTimelineSnapshot {
            snapshotCount += 1
            return sample
        }
    }

    private companion object {
        const val REQUEST_ID = "0123456789abcdef0123456789abcdef"
        const val SECOND_REQUEST_ID = "fedcba9876543210fedcba9876543210"
    }
}

private fun timelineSnapshot(
    identity: String,
    playTimeNanos: Long,
): AnimationTimelineSnapshot {
    val state = AnimationTimelineStateSummary("boolean", "false")
    return AnimationTimelineSnapshot(
        identity = identity,
        label = "panel",
        currentState = state,
        targetState = AnimationTimelineStateSummary("boolean", "true"),
        segmentInitialState = state,
        segmentTargetState = AnimationTimelineStateSummary("boolean", "true"),
        segmentVersion = 1L,
        playTimeNanos = playTimeNanos,
        durationNanos = 300_000_000L,
        runState = AnimationTimelineRunState.Running,
        channels = listOf(
            AnimationTimelineChannelSnapshot(
                identity = "channel-1",
                name = "Float 1",
                specFamily = AnimationTimelineSpecFamily.Tween,
                startValue = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(0f)),
                currentValue = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(0.5f)),
                targetValue = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(1f)),
                velocity = AnimationTimelineValue(AnimationTimelineValueKind.Float, listOf(3f)),
                durationNanos = 300_000_000L,
                finished = false,
                terminalCondition = AnimationTimelineTerminalCondition.Finished,
            ),
        ),
    )
}
