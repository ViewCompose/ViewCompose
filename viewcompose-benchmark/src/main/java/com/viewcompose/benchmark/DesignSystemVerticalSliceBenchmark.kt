package com.viewcompose.benchmark

import android.os.SystemClock
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

    @Test
    fun cutContrastOverlayLifecycle() = overlayLifecycle(
        initialKind = CUT_CONTRAST,
        replacementKind = ROUNDED_REFERENCE,
    )

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
            SystemClock.sleep(SCROLL_SETTLE_MILLIS)
        },
    ) {
        // A single retained patch can complete without enough frame slices for Perfetto to report
        // stable run-level percentiles. Repeat the same patch workload inside each iteration so the
        // metric still isolates retained updates while producing a representative frame sample.
        repeat(PATCH_UPDATES_PER_ITERATION) { updateIndex ->
            clickVisibleText("Confirm")
            waitForText("Button clicks: ${updateIndex + 1}")
        }
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
            scrollUntilText("Checked: true")
            SystemClock.sleep(SCROLL_SETTLE_MILLIS)
        },
    ) {
        clickVisibleCheckableControlWithoutIdle()
        waitForText("Checked: false")
        // The logical checked state changes before the visual spring completes. Keep the measured
        // trace open through the terminal frame so one representative user transition supplies the
        // whole animation workload instead of only its first frame.
        SystemClock.sleep(ACTIVE_ANIMATION_SETTLE_MILLIS)
    }

    private fun overlayLifecycle(
        initialKind: String,
        replacementKind: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = CompilationMode.None(),
        iterations = designSystemIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDesignSystemAndWait(initialKind)
            scrollUntilText("Open dialog")
            // UiAutomator idle waits are disabled for OEM stability, so let the page gesture settle
            // before the measured overlay lifecycle begins.
            SystemClock.sleep(SCROLL_SETTLE_MILLIS)
        },
    ) {
        // One overlay lifecycle yields too few frame slices on some OEM devices for stable
        // run-level percentiles. Repeat show and dismiss without changing the page position, then
        // use the final lifecycle for the representative root-scoped overlay replacement.
        repeat(OVERLAY_SHOW_DISMISS_REPEATS_PER_ITERATION) {
            clickVisibleText("Open dialog")
            waitForText("Overlay system: $initialKind")
            clickVisibleText("Close coherent dialog")
            waitForTextGone("Overlay system: $initialKind")
        }
        clickVisibleText("Open dialog")
        waitForText("Overlay system: $initialKind")
        clickVisibleText("Switch overlay to $replacementKind")
        waitForText("Overlay system: $replacementKind")
        clickVisibleText("Close coherent dialog")
        waitForTextGone("Overlay system: $replacementKind")
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

private const val SCROLL_SETTLE_MILLIS = 2_000L
private const val ACTIVE_ANIMATION_SETTLE_MILLIS = 600L
private const val PATCH_UPDATES_PER_ITERATION = 24
private const val OVERLAY_SHOW_DISMISS_REPEATS_PER_ITERATION = 5
