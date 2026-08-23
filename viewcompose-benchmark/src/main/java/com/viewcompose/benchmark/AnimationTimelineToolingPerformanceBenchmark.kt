package com.viewcompose.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Same-device debug comparison for the optional, request-driven animation timeline tooling. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class AnimationTimelineToolingPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun inactiveTimelineToolingRevision1() {
        benchmarkTimelineWorkload(requestCapture = false)
    }

    @Test
    fun requestedTimelineToolingRevision1() {
        benchmarkTimelineWorkload(requestCapture = true)
    }

    private fun benchmarkTimelineWorkload(requestCapture: Boolean) {
        val iterations = requestedIterations()
        var transitionIdentity = ""
        var nextRequestOrdinal = 0
        var reportWrites = 0
        var workloadExecutions = 0
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            compilationMode = CompilationMode.None(),
            iterations = iterations,
            startupMode = StartupMode.WARM,
            setupBlock = {
                deleteTimelineReport()
                startDemoScenarioAndWait(ANIMATION_TRANSITION_SCENARIO)
                if (requestCapture) {
                    transitionIdentity = discoverTimeline(
                        requestId = requestId(++nextRequestOrdinal),
                    )
                }
                waitForPerformanceMeasurementSettle()
            },
        ) {
            workloadExecutions += 1
            repeat(ROUND_TRIPS_PER_ITERATION) {
                val beforeForward = scenarioTargetText(
                    ANIMATION_TRANSITION_SCENARIO,
                    DemoTargetRole.State,
                )
                val forwardRequest = if (requestCapture) {
                    beginTimelineCapture(
                        transitionIdentity = transitionIdentity,
                        requestId = requestId(++nextRequestOrdinal),
                    )
                } else {
                    null
                }
                performScenarioTargetClick(
                    ANIMATION_TRANSITION_SCENARIO,
                    DemoTargetRole.PrimaryAction,
                )
                val afterForward = waitForScenarioTargetTextChange(
                    ANIMATION_TRANSITION_SCENARIO,
                    DemoTargetRole.State,
                    beforeForward,
                )
                if (forwardRequest != null) {
                    reportWrites += awaitTimelineCapture(forwardRequest, transitionIdentity)
                    SystemClock.sleep(POST_CAPTURE_SETTLE_MILLIS)
                } else {
                    SystemClock.sleep(TRANSITION_SETTLE_MILLIS)
                }

                val reverseRequest = if (requestCapture) {
                    beginTimelineCapture(
                        transitionIdentity = transitionIdentity,
                        requestId = requestId(++nextRequestOrdinal),
                    )
                } else {
                    null
                }
                performScenarioTargetClick(
                    ANIMATION_TRANSITION_SCENARIO,
                    DemoTargetRole.SecondaryAction,
                )
                waitForScenarioTargetTextChange(
                    ANIMATION_TRANSITION_SCENARIO,
                    DemoTargetRole.State,
                    afterForward,
                )
                if (reverseRequest != null) {
                    reportWrites += awaitTimelineCapture(reverseRequest, transitionIdentity)
                    SystemClock.sleep(POST_CAPTURE_SETTLE_MILLIS)
                } else {
                    SystemClock.sleep(TRANSITION_SETTLE_MILLIS)
                }
            }
            if (!requestCapture) {
                assertTrue(
                    "Inactive tooling must not write a report.",
                    timelineReportContents().isBlank(),
                )
            }
        }
        assertEquals(
            if (requestCapture) {
                workloadExecutions * ROUND_TRIPS_PER_ITERATION * CAPTURES_PER_ROUND_TRIP
            } else {
                0
            },
            reportWrites,
        )
        assertTrue(workloadExecutions >= iterations)
    }

    private fun requestedIterations(): Int {
        return InstrumentationRegistry.getArguments()
            .getString(ITERATIONS_ARGUMENT)
            ?.toIntOrNull()
            ?.takeIf { iterations -> iterations in 1..FORMAL_INTERACTION_ITERATIONS }
            ?: FORMAL_INTERACTION_ITERATIONS
    }

    private fun MacrobenchmarkScope.discoverTimeline(requestId: String): String {
        device.executeShellCommand(
            requestCommand(requestId = requestId, mode = DISCOVER_MODE),
        )
        val report = awaitTimelineReport(requestId)
        assertEquals(DISCOVER_MODE, report.getString("mode"))
        assertEquals("success", report.getString("status"))
        val transitions = report.getJSONArray("transitions")
        for (index in 0 until transitions.length()) {
            val timeline = transitions.getJSONObject(index)
            if (timeline.getString("label") == TARGET_TIMELINE_LABEL) {
                return timeline.getString("identity")
            }
        }
        error("Expected the seekable transition timeline in the discovery report.")
    }

    private fun MacrobenchmarkScope.beginTimelineCapture(
        transitionIdentity: String,
        requestId: String,
    ): PendingTimelineCapture {
        assertTrue(transitionIdentity.matches(VALID_TIMELINE_IDENTITY))
        val failure = AtomicReference<Throwable?>()
        val requestThread = thread(
            name = "viewcompose-animation-timeline-request",
            start = true,
        ) {
            runCatching {
                device.executeShellCommand(
                    requestCommand(
                        requestId = requestId,
                        mode = CAPTURE_MODE,
                        transitionIdentity = transitionIdentity,
                    ),
                )
            }.onFailure(failure::set)
        }
        SystemClock.sleep(REQUEST_ARM_MILLIS)
        return PendingTimelineCapture(
            requestId = requestId,
            requestThread = requestThread,
            failure = failure,
        )
    }

    private fun MacrobenchmarkScope.awaitTimelineCapture(
        request: PendingTimelineCapture,
        transitionIdentity: String,
    ): Int {
        request.requestThread.join(REPORT_TIMEOUT_MILLIS)
        assertTrue(
            "Animation timeline capture request must finish within the bounded timeout.",
            !request.requestThread.isAlive,
        )
        request.failure.get()?.let { failure -> throw failure }
        val report = awaitTimelineReport(request.requestId)
        assertEquals(CAPTURE_MODE, report.getString("mode"))
        assertEquals("success", report.getString("status"))
        val transitions = report.getJSONArray("transitions")
        assertEquals(1, transitions.length())
        val timeline = transitions.getJSONObject(0)
        assertEquals(transitionIdentity, timeline.getString("identity"))
        assertTrue(timeline.getJSONArray("samples").length() in 1..MAX_CAPTURE_SAMPLES)
        return 1
    }

    private data class PendingTimelineCapture(
        val requestId: String,
        val requestThread: Thread,
        val failure: AtomicReference<Throwable?>,
    )

    private fun MacrobenchmarkScope.awaitTimelineReport(requestId: String): JSONObject {
        val deadline = SystemClock.uptimeMillis() + REPORT_TIMEOUT_MILLIS
        var lastReport = ""
        while (SystemClock.uptimeMillis() < deadline) {
            lastReport = device.executeShellCommand(
                "run-as $TARGET_PACKAGE cat $TIMELINE_REPORT_PATH",
            )
            val parsed = runCatching { JSONObject(lastReport) }.getOrNull()
            if (parsed?.optString("requestId") == requestId) return parsed
            SystemClock.sleep(REPORT_POLL_MILLIS)
        }
        error("Timed out waiting for animation timeline report $requestId: $lastReport")
    }

    private fun MacrobenchmarkScope.deleteTimelineReport() {
        device.executeShellCommand(
            "run-as $TARGET_PACKAGE rm -f $TIMELINE_REPORT_PATH",
        )
    }

    private fun MacrobenchmarkScope.timelineReportContents(): String {
        return device.executeShellCommand(
            "run-as $TARGET_PACKAGE cat $TIMELINE_REPORT_PATH",
        )
    }

    private fun requestCommand(
        requestId: String,
        mode: String,
        transitionIdentity: String? = null,
    ): String {
        val transitionArgument = transitionIdentity?.let { identity ->
            " --es transition_id $identity"
        }.orEmpty()
        return "am broadcast --user current " +
            "-a com.viewcompose.preview.action.REQUEST_ANIMATION_TIMELINE " +
            "-p $TARGET_PACKAGE --es request_id $requestId --es mode $mode$transitionArgument"
    }

    private fun requestId(ordinal: Int): String = ordinal.toString(16).padStart(32, '0')

    private companion object {
        const val ANIMATION_TRANSITION_SCENARIO = "animation.transition"
        const val TARGET_TIMELINE_LABEL = "demo_seekable_transition"
        const val DISCOVER_MODE = "discover"
        const val CAPTURE_MODE = "capture"
        const val TIMELINE_REPORT_PATH = "cache/viewcompose/animation-timeline-v1.json"
        const val ROUND_TRIPS_PER_ITERATION = 4
        const val TRANSITION_SETTLE_MILLIS = 850L
        const val CAPTURE_DURATION_MILLIS = 500L
        const val POST_CAPTURE_SETTLE_MILLIS =
            TRANSITION_SETTLE_MILLIS - CAPTURE_DURATION_MILLIS
        const val REPORT_TIMEOUT_MILLIS = 5_000L
        const val REPORT_POLL_MILLIS = 25L
        const val REQUEST_ARM_MILLIS = 25L
        const val MAX_CAPTURE_SAMPLES = 64
        const val CAPTURES_PER_ROUND_TRIP = 2
        const val ITERATIONS_ARGUMENT = "viewcompose.benchmark.iterations"
        val VALID_TIMELINE_IDENTITY = Regex("[A-Za-z0-9-]{1,256}")
    }
}
