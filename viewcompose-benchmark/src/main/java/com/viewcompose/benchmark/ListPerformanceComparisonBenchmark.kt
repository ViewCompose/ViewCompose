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

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ListPerformanceComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun viewComposeListScroll() {
        measureListScroll(
            engine = "viewcompose",
            expectedText = "ViewCompose List Ready",
        )
    }

    @Test
    fun composeListScroll() {
        measureListScroll(
            engine = "compose",
            expectedText = "Compose List Ready",
        )
    }

    @Test
    fun viewComposeListMutation() {
        measureListMutation(
            engine = "viewcompose",
            expectedText = "ViewCompose List Ready",
        )
    }

    @Test
    fun composeListMutation() {
        measureListMutation(
            engine = "compose",
            expectedText = "Compose List Ready",
        )
    }

    private fun measureListScroll(
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
                scenario = "list",
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

    private fun measureListMutation(
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
                scenario = "list",
                expectedText = expectedText,
            )
            waitForText("List revision 0")
        },
    ) {
        clickText("Mutate list")
        waitForText("List revision 1")
        clickText("Reset list")
        waitForText("List revision 0")
    }

    private fun performanceComparisonMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )
}
