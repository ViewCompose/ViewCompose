package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stable release baseline for the R8-optimized, non-debuggable benchmark target.
 *
 * [CompilationMode.None] keeps ART pre-compilation out of the result so regressions in the shipped
 * binary remain visible independently from baseline-profile work.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseBaselineBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutArtPrecompilation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        startDemoAndWait()
    }

    @Test
    fun statePatchWithoutArtPrecompilation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "state",
                expectedText = "Patch Stress",
                extras = mapOf("state_page_index" to 2),
            )
            waitForText("Advance patch state 0")
            waitForText("Reset patch state")
        },
    ) {
        clickText("Advance patch state 0")
        waitForText("Advance patch state 1")
        clickText("Advance patch state 1")
        waitForText("Advance patch state 2")
        clickText("Reset patch state")
        waitForText("Advance patch state 0")
    }
}
