package com.viewcompose.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Revisioned physical-device workloads for the Animation Compose-capability expansion.
 *
 * Historical methods preserve the Phase 0/1 baselines. Revision 2 compares keyed AnimatedContent
 * with the same-page alpha-only Crossfade control. Revision 3 measures rich parent-plus-descendant
 * visibility choreography. Each method measures complete forward and reverse animations after an
 * unmeasured launch settle. Accessibility actions avoid mixing pointer press frames into the
 * animation distribution, while state assertions prove every request reached the application.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class AnimationPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun durationSpringValueChannelsRevision1() {
        benchmarkRoundTrips(
            scenarioId = ANIMATION_SPECS_SCENARIO,
            forwardRole = DemoTargetRole.PrimaryAction,
            reverseRole = DemoTargetRole.PrimaryAction,
            settleMillis = VALUE_CHANNEL_SETTLE_MILLIS,
            prepare = {
                val initial = scenarioTargetText(ANIMATION_SPECS_SCENARIO, DemoTargetRole.State)
                clickScenarioTarget(
                    ANIMATION_SPECS_SCENARIO,
                    DemoTargetRole.SecondaryAction,
                )
                waitForScenarioTargetTextChange(
                    ANIMATION_SPECS_SCENARIO,
                    DemoTargetRole.State,
                    initial,
                )
            },
        )
    }

    @Test
    fun animatedContentRevision2() {
        benchmarkRoundTrips(
            scenarioId = ANIMATION_CONTENT_SCENARIO,
            forwardRole = DemoTargetRole.PrimaryAction,
            reverseRole = DemoTargetRole.PrimaryAction,
            settleMillis = CONTENT_REPLACEMENT_SETTLE_MILLIS,
        )
    }

    @Test
    fun crossfadeComparisonRevision2() {
        benchmarkRoundTrips(
            scenarioId = ANIMATION_CONTENT_SCENARIO,
            forwardRole = DemoTargetRole.SecondaryAction,
            reverseRole = DemoTargetRole.SecondaryAction,
            settleMillis = CONTENT_REPLACEMENT_SETTLE_MILLIS,
        )
    }

    @Test
    fun richAnimatedVisibilityRevision3() {
        benchmarkRoundTrips(
            scenarioId = ANIMATION_CORE_SCENARIO,
            forwardRole = DemoTargetRole.PrimaryAction,
            reverseRole = DemoTargetRole.PrimaryAction,
            settleMillis = RICH_VISIBILITY_SETTLE_MILLIS,
        )
    }

    @Test
    fun animatedContentSizeRevision1() {
        benchmarkRoundTrips(
            scenarioId = ANIMATION_CONTENT_SIZE_SCENARIO,
            forwardRole = DemoTargetRole.PrimaryAction,
            reverseRole = DemoTargetRole.SecondaryAction,
            settleMillis = CONTENT_SIZE_SETTLE_MILLIS,
        )
    }

    @Test
    fun synchronizedTransitionRevision1() {
        benchmarkRoundTrips(
            scenarioId = ANIMATION_TRANSITION_SCENARIO,
            forwardRole = DemoTargetRole.PrimaryAction,
            reverseRole = DemoTargetRole.PrimaryAction,
            settleMillis = TRANSITION_SETTLE_MILLIS,
        )
    }

    private fun benchmarkRoundTrips(
        scenarioId: String,
        forwardRole: DemoTargetRole,
        reverseRole: DemoTargetRole,
        settleMillis: Long,
        prepare: (androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit)? = null,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = CompilationMode.None(),
        iterations = FORMAL_INTERACTION_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait(scenarioId)
            prepare?.invoke(this)
            waitForPerformanceMeasurementSettle()
        },
    ) {
        repeat(ROUND_TRIPS_PER_ITERATION) {
            val beforeForward = scenarioTargetText(scenarioId, DemoTargetRole.State)
            performScenarioTargetClick(scenarioId, forwardRole)
            val afterForward = waitForScenarioTargetTextChange(
                scenarioId,
                DemoTargetRole.State,
                beforeForward,
            )
            SystemClock.sleep(settleMillis)

            performScenarioTargetClick(scenarioId, reverseRole)
            waitForScenarioTargetTextChange(
                scenarioId,
                DemoTargetRole.State,
                afterForward,
            )
            SystemClock.sleep(settleMillis)
        }
    }

    private companion object {
        const val ANIMATION_CORE_SCENARIO = "animation.core"
        const val ANIMATION_SPECS_SCENARIO = "animation.specs"
        const val ANIMATION_CONTENT_SCENARIO = "animation.content"
        const val ANIMATION_CONTENT_SIZE_SCENARIO = "animation.content-size"
        const val ANIMATION_TRANSITION_SCENARIO = "animation.transition"
        const val ROUND_TRIPS_PER_ITERATION = 4
        const val VALUE_CHANNEL_SETTLE_MILLIS = 600L
        const val CONTENT_REPLACEMENT_SETTLE_MILLIS = 380L
        const val RICH_VISIBILITY_SETTLE_MILLIS = 900L
        const val CONTENT_SIZE_SETTLE_MILLIS = 650L
        const val TRANSITION_SETTLE_MILLIS = 540L
    }
}
