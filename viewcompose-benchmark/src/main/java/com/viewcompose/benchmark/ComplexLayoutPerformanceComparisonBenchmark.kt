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
 * ViewCompose 与 Compose 复杂布局场景的帧耗时和内存对照 benchmark。
 * Paired frame-time and memory benchmark for the ViewCompose and Compose complex-layout scenario.
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
            expectedText = "ViewCompose Complex Ready",
        )
    }

    @Test
    fun composeComplexLayoutScroll() {
        measureComplexLayoutScroll(
            engine = "compose",
            expectedText = "Compose Complex Ready",
        )
    }

    @Test
    fun viewComposeComplexLayoutUpdate() {
        measureComplexLayoutUpdate(
            engine = "viewcompose",
            expectedText = "ViewCompose Complex Ready",
        )
    }

    @Test
    fun composeComplexLayoutUpdate() {
        measureComplexLayoutUpdate(
            engine = "compose",
            expectedText = "Compose Complex Ready",
        )
    }

    private fun measureComplexLayoutScroll(
        engine: String,
        expectedText: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceComparisonAndWait(
                engine = engine,
                scenario = "complex_layout",
                expectedText = expectedText,
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
        expectedText: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = performanceComparisonMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceComparisonAndWait(
                engine = engine,
                scenario = "complex_layout",
                expectedText = expectedText,
            )
            waitForText("Dashboard revision 0")
        },
    ) {
        clickText("Update dashboard")
        waitForText("Dashboard revision 1")
        clickText("Reset dashboard")
        waitForText("Dashboard revision 0")
    }

    private fun performanceComparisonMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )
}
