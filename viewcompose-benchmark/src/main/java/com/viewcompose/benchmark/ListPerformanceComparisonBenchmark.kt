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
 * Frame-time and memory benchmark for the ViewCompose, Compose, and Android Views list scenario.
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
    fun androidViewsListScroll() {
        measureListScroll(
            engine = "android_views",
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

    @Test
    fun androidViewsListMutation() {
        measureListMutation(
            engine = "android_views",
        )
    }

    private fun measureListScroll(
        engine: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = FORMAL_INTERACTION_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(PERFORMANCE_LIST_SCENARIO, engine)
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
        iterations = FORMAL_INTERACTION_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(PERFORMANCE_LIST_SCENARIO, engine)
        },
    ) {
        repeat(PERFORMANCE_MUTATION_CYCLES_PER_ITERATION) {
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
    }

    private fun performanceComparisonMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private companion object {
        const val PERFORMANCE_LIST_SCENARIO = "performance.list"
    }
}

private const val PERFORMANCE_MUTATION_CYCLES_PER_ITERATION = 8
