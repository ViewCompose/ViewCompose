package com.viewcompose.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
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
 * release/R8 环境下框架自有导航转场的帧耗时覆盖。
 * Release/R8 frame-time coverage for framework-owned navigation transitions.
 *
 * Push 和系统 Back 分开测量，便于报告区分目标页创建成本和 Pop 时复用已保留目标页的路径。
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
            scrollUntilScenarioTarget(NAVIGATION_SCENARIO, DemoTargetRole.PrimaryAction)
            waitForPerformanceMeasurementSettle()
        },
    ) {
        repeat(NAVIGATION_TRANSITIONS_PER_ITERATION) {
            pushDestinationAndWait()
        }
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
            scrollUntilScenarioTarget(NAVIGATION_SCENARIO, DemoTargetRole.PrimaryAction)
            repeat(NAVIGATION_TRANSITIONS_PER_ITERATION) {
                pushDestinationAndWait()
            }
            waitForPerformanceMeasurementSettle()
        },
    ) {
        repeat(NAVIGATION_TRANSITIONS_PER_ITERATION) {
            popDestinationAndWait()
        }
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
            scrollUntilScenarioTarget(NAVIGATION_SCENARIO, DemoTargetRole.PrimaryAction)
            waitForPerformanceMeasurementSettle()
        },
    ) {
        repeat(NAVIGATION_TRANSITIONS_PER_ITERATION) {
            pushDestinationAndWait()
        }
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
                scrollUntilScenarioTarget(NAVIGATION_SCENARIO, DemoTargetRole.PrimaryAction)
                repeat(NAVIGATION_TRANSITIONS_PER_ITERATION) {
                    pushDestinationAndWait()
                }
                waitForPerformanceMeasurementSettle()
            },
        ) {
            repeat(NAVIGATION_TRANSITIONS_PER_ITERATION) {
                popDestinationAndWait()
            }
        }

    private fun MacrobenchmarkScope.pushDestinationAndWait() {
        val previous = scenarioTargetText(NAVIGATION_SCENARIO, DemoTargetRole.State)
        clickScenarioTarget(
            NAVIGATION_SCENARIO,
            DemoTargetRole.PrimaryAction,
            waitForIdle = false,
        )
        waitForNavigationMotion()
        waitForScenarioTargetTextChange(NAVIGATION_SCENARIO, DemoTargetRole.State, previous)
    }

    private fun MacrobenchmarkScope.popDestinationAndWait() {
        val previous = scenarioTargetText(NAVIGATION_SCENARIO, DemoTargetRole.State)
        device.pressBack()
        waitForNavigationMotion()
        waitForScenarioTargetTextChange(NAVIGATION_SCENARIO, DemoTargetRole.State, previous)
    }

    private companion object {
        const val NAVIGATION_SCENARIO = "navigation.system"
        const val ITERATIONS_ARGUMENT = "navigationMotionIterations"

        fun navigationMotionIterations(): Int {
            return InstrumentationRegistry.getArguments()
                .getString(ITERATIONS_ARGUMENT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: FORMAL_INTERACTION_ITERATIONS
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
        const val NAVIGATION_TRANSITIONS_PER_ITERATION = 8
    }
}
