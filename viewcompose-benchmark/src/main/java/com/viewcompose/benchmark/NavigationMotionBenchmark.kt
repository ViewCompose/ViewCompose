package com.viewcompose.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release/R8 frame-time coverage for framework-owned navigation transitions.
 *
 * Push and system Back are measured independently so the report can distinguish destination
 * creation from the already-retained destination path used by Pop.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class NavigationMotionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun pushDestination() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = navigationMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = navigationMotionIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startSystemNavigationAndWait()
            scrollUntilText(PUSH_ACTION_TEXT)
        },
    ) {
        clickVisibleTextWithoutIdle(PUSH_ACTION_TEXT)
        waitForNavigationMotion()
        waitForText(DETAIL_DESTINATION_TEXT)
    }

    @Test
    fun systemBackPopDestination() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = navigationMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = navigationMotionIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startSystemNavigationAndWait()
            scrollUntilText(PUSH_ACTION_TEXT)
            clickVisibleTextWithoutIdle(PUSH_ACTION_TEXT)
            waitForNavigationMotion()
            waitForText(DETAIL_DESTINATION_TEXT)
        },
    ) {
        device.pressBack()
        waitForNavigationMotion()
        waitForText(PUSH_ACTION_TEXT)
    }

    @Test
    fun pushDestinationAfterProfileGuidedCompilation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = navigationMetrics(),
        compilationMode = profileGuidedCompilation(),
        iterations = navigationMotionIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startSystemNavigationAndWait()
            scrollUntilText(PUSH_ACTION_TEXT)
        },
    ) {
        clickVisibleTextWithoutIdle(PUSH_ACTION_TEXT)
        waitForNavigationMotion()
        waitForText(DETAIL_DESTINATION_TEXT)
    }

    @Test
    fun systemBackPopDestinationAfterProfileGuidedCompilation() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = navigationMetrics(),
            compilationMode = profileGuidedCompilation(),
            iterations = navigationMotionIterations(),
            startupMode = StartupMode.WARM,
            setupBlock = {
                startSystemNavigationAndWait()
                scrollUntilText(PUSH_ACTION_TEXT)
                clickVisibleTextWithoutIdle(PUSH_ACTION_TEXT)
                waitForNavigationMotion()
                waitForText(DETAIL_DESTINATION_TEXT)
            },
        ) {
            device.pressBack()
            waitForNavigationMotion()
            waitForText(PUSH_ACTION_TEXT)
        }

    private companion object {
        const val PUSH_ACTION_TEXT = "Push 下一页面"
        const val DETAIL_DESTINATION_TEXT = "首页详情"
        const val ITERATIONS_ARGUMENT = "navigationMotionIterations"

        fun navigationMotionIterations(): Int {
            return InstrumentationRegistry.getArguments()
                .getString(ITERATIONS_ARGUMENT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: RELEASE_BASELINE_ITERATIONS
        }

        fun navigationMetrics(): List<Metric> = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(
                sectionName = "VC.Nav.PrepareDestination",
                mode = TraceSectionMetric.Mode.Sum,
                label = "navPrepare",
            ),
            TraceSectionMetric(
                sectionName = "VC.FrameRender",
                mode = TraceSectionMetric.Mode.Count,
                label = "frameRenderCount",
            ),
            TraceSectionMetric(
                sectionName = "VC.RenderTree",
                mode = TraceSectionMetric.Mode.Max,
                label = "renderTreeMax",
            ),
            TraceSectionMetric(
                sectionName = "VC.Nav.MotionFrame",
                mode = TraceSectionMetric.Mode.Max,
                label = "navMotionFrameMax",
            ),
        )

        fun profileGuidedCompilation(): CompilationMode {
            return CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Disable,
                warmupIterations = PROFILE_WARMUP_ITERATIONS,
            )
        }

        const val PROFILE_WARMUP_ITERATIONS = 3
    }
}
