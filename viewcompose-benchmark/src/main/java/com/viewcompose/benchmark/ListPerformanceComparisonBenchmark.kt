package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

/**
 * ViewCompose 与 Compose 大列表场景的帧耗时和内存对照 benchmark。
 * Paired frame-time and memory benchmark for the ViewCompose and Compose large-list scenario.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ListPerformanceComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun viewComposeListScroll() {
        measureListScroll(
            engine = "viewcompose",
        )
    }

    @Test
    fun composeListScroll() {
        measureListScroll(
            engine = "compose",
        )
    }

    @Test
    fun viewComposeListMutation() {
        measureListMutation(
            engine = "viewcompose",
        )
    }

    @Test
    fun composeListMutation() {
        measureListMutation(
            engine = "compose",
        )
    }

    private fun measureListScroll(
        engine: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceListAndWait(engine)
        },
    ) {
        repeat(4) {
            swipePageUp()
        }
        repeat(4) {
            swipePageDown()
        }
    }

    private fun measureListMutation(
        engine: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceListAndWait(engine)
        },
    ) {
        val initial = scenarioTargetText(PERFORMANCE_LIST_SCENARIO, DemoTargetRole.State)
        clickScenarioTarget(PERFORMANCE_LIST_SCENARIO, DemoTargetRole.PrimaryAction)
        val mutated = waitForScenarioTargetTextChange(
            PERFORMANCE_LIST_SCENARIO,
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget(PERFORMANCE_LIST_SCENARIO, DemoTargetRole.Reset)
        val reset = waitForScenarioTargetTextChange(
            PERFORMANCE_LIST_SCENARIO,
            DemoTargetRole.State,
            mutated,
        )
        assertEquals(initial, reset)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startPerformanceListAndWait(engine: String) {
        startDemoScenarioAndWait(PERFORMANCE_LIST_SCENARIO) {
            putExtra("performance_engine", engine)
            putExtra("performance_scenario", "list")
        }
        waitForScenarioTarget(PERFORMANCE_LIST_SCENARIO, DemoTargetRole.Target)
    }

    private fun performanceComparisonMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private companion object {
        const val PERFORMANCE_LIST_SCENARIO = "performance.list"
    }
}
