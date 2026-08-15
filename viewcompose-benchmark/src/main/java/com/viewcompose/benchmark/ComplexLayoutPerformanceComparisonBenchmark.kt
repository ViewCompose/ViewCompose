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
import org.junit.runner.RunWith

/**
 * Frame-time and memory benchmark for all three complex-layout implementations.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ComplexLayoutPerformanceComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun viewComposeComplexLayoutScroll() {
        measureComplexLayoutScroll(
            engine = "viewcompose",
        )
    }

    @Test
    fun composeComplexLayoutScroll() {
        measureComplexLayoutScroll(
            engine = "compose",
        )
    }

    @Test
    fun androidViewsComplexLayoutScroll() {
        measureComplexLayoutScroll(
            engine = "android_views",
        )
    }

    @Test
    fun viewComposeComplexLayoutUpdate() {
        measureComplexLayoutUpdate(
            engine = "viewcompose",
        )
    }

    @Test
    fun composeComplexLayoutUpdate() {
        measureComplexLayoutUpdate(
            engine = "compose",
        )
    }

    @Test
    fun androidViewsComplexLayoutUpdate() {
        measureComplexLayoutUpdate(
            engine = "android_views",
        )
    }

    private fun measureComplexLayoutScroll(
        engine: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = FORMAL_INTERACTION_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(
                scenarioId = PERFORMANCE_COMPLEX_LAYOUT_SCENARIO,
                engine = engine,
            )
        },
    ) {
        repeat(4) {
            swipePageUp()
        }
        repeat(4) {
            swipePageDown()
        }
    }

    private fun measureComplexLayoutUpdate(
        engine: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = FORMAL_INTERACTION_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(
                scenarioId = PERFORMANCE_COMPLEX_LAYOUT_SCENARIO,
                engine = engine,
            )
        },
    ) {
        repeat(PERFORMANCE_UPDATE_CYCLES_PER_ITERATION) {
            val initial = scenarioTargetText(
                PERFORMANCE_COMPLEX_LAYOUT_SCENARIO,
                DemoTargetRole.State,
            )
            clickScenarioTarget(PERFORMANCE_COMPLEX_LAYOUT_SCENARIO, DemoTargetRole.PrimaryAction)
            val updated = waitForScenarioTargetTextChange(
                PERFORMANCE_COMPLEX_LAYOUT_SCENARIO,
                DemoTargetRole.State,
                initial,
            )
            clickScenarioTarget(PERFORMANCE_COMPLEX_LAYOUT_SCENARIO, DemoTargetRole.Reset)
            val reset = waitForScenarioTargetTextChange(
                PERFORMANCE_COMPLEX_LAYOUT_SCENARIO,
                DemoTargetRole.State,
                updated,
            )
            org.junit.Assert.assertEquals(initial, reset)
        }
    }

    private fun performanceComparisonMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private companion object {
        const val PERFORMANCE_COMPLEX_LAYOUT_SCENARIO = "performance.complex-layout"
    }
}

private const val PERFORMANCE_UPDATE_CYCLES_PER_ITERATION = 8
