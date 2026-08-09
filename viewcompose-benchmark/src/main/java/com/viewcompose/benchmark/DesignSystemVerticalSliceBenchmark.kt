package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compares the internal high-fidelity slice with its rounded reference using Phase 0 metrics.
 *
 * Both paths use the same host, state, and renderer. The comparison therefore isolates the cost
 * of resolved recipes, custom geometry, and semantic motion without conflating a different app
 * startup path.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class DesignSystemVerticalSliceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun cutContrastInitialBuild() = initialBuild(CUT_CONTRAST)

    @Test
    fun roundedReferenceInitialBuild() = initialBuild(ROUNDED_REFERENCE)

    @Test
    fun cupertinoPressureInitialBuild() = initialBuild(CUPERTINO_PRESSURE)

    @Test
    fun cutContrastPatchOnlyUpdate() = patchOnlyUpdate(CUT_CONTRAST)

    @Test
    fun roundedReferencePatchOnlyUpdate() = patchOnlyUpdate(ROUNDED_REFERENCE)

    @Test
    fun cupertinoPressurePatchOnlyUpdate() = patchOnlyUpdate(CUPERTINO_PRESSURE)

    @Test
    fun cutContrastScrollAndDraw() = scrollAndDraw(CUT_CONTRAST)

    @Test
    fun roundedReferenceScrollAndDraw() = scrollAndDraw(ROUNDED_REFERENCE)

    @Test
    fun cupertinoPressureScrollAndDraw() = scrollAndDraw(CUPERTINO_PRESSURE)

    @Test
    fun cutContrastActiveAnimation() = activeAnimation(CUT_CONTRAST)

    @Test
    fun roundedReferenceActiveAnimation() = activeAnimation(ROUNDED_REFERENCE)

    @Test
    fun cupertinoPressureActiveAnimation() = activeAnimation(CUPERTINO_PRESSURE)

    private fun initialBuild(kind: String) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = CompilationMode.None(),
        iterations = designSystemIterations(),
        startupMode = StartupMode.COLD,
    ) {
        startDesignSystemAndWait(kind)
    }

    private fun patchOnlyUpdate(kind: String) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = CompilationMode.None(),
        iterations = designSystemIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDesignSystemAndWait(kind)
            scrollUntilText("Confirm")
        },
    ) {
        clickText("Confirm")
        waitForText("Button clicks: 1")
    }

    private fun scrollAndDraw(kind: String) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = designSystemIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDesignSystemAndWait(kind)
            scrollToPageTop()
        },
    ) {
        swipePageUp()
        swipePageUp()
        swipePageDown()
    }

    private fun activeAnimation(kind: String) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = designSystemIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDesignSystemAndWait(kind)
            scrollUntilText("Synchronize workspace")
        },
    ) {
        clickVisibleTextWithoutIdle("Synchronize workspace")
        waitForText("Checked: false")
    }

    private companion object {
        const val CUT_CONTRAST = "cut-contrast"
        const val CUPERTINO_PRESSURE = "cupertino-pressure"
        const val ROUNDED_REFERENCE = "rounded-reference"
    }
}

private fun designSystemIterations(): Int {
    return InstrumentationRegistry.getArguments()
        .getString("designSystemIterations")
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
        ?: DEFAULT_ITERATIONS
}
