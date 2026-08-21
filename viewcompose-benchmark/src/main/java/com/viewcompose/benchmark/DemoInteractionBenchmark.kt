package com.viewcompose.benchmark

import android.graphics.Rect
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * demo 模块交互路径的轻量帧耗时 benchmark。
 * Lightweight frame-time benchmarks for demo module interaction paths.
 *
 * 这些用例覆盖手工验收锚点，帮助发现常用 DSL 页面在状态切换和滚动时的回归。
 * These cases exercise manual-QA anchors and catch regressions during state toggles and scrolling.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class DemoInteractionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun layoutsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("layout.linear")
        },
    ) {
        val initial = scenarioTargetText("layout.linear", DemoTargetRole.State)
        clickScenarioTarget("layout.linear", DemoTargetRole.PrimaryAction)
        val changed = waitForScenarioTargetTextChange(
            "layout.linear",
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget("layout.linear", DemoTargetRole.Reset)
        waitForScenarioTargetTextChange("layout.linear", DemoTargetRole.State, changed)
    }

    @Test
    fun inputBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("input.fields")
        },
    ) {
        val initial = scenarioTargetText("input.fields", DemoTargetRole.State)
        clickScenarioTarget("input.fields", DemoTargetRole.PrimaryAction)
        val changed = waitForScenarioTargetTextChange(
            "input.fields",
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget("input.fields", DemoTargetRole.Reset)
        waitForScenarioTargetTextChange("input.fields", DemoTargetRole.State, changed)
    }

    @Test
    fun collectionsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("collection.controls")
        },
    ) {
        val initial = scenarioTargetText("collection.controls", DemoTargetRole.State)
        clickScenarioTarget("collection.controls", DemoTargetRole.PrimaryAction)
        val changed = waitForScenarioTargetTextChange(
            "collection.controls",
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget("collection.controls", DemoTargetRole.Reset)
        waitForScenarioTargetTextChange("collection.controls", DemoTargetRole.State, changed)
    }

    @Test
    fun stateBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("runtime.state")
        },
    ) {
        val initial = scenarioTargetText("runtime.state", DemoTargetRole.State)
        clickScenarioTarget("runtime.state", DemoTargetRole.PrimaryAction)
        val changed = waitForScenarioTargetTextChange(
            "runtime.state",
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget("runtime.state", DemoTargetRole.Reset)
        waitForScenarioTargetTextChange("runtime.state", DemoTargetRole.State, changed)
    }

    @Test
    fun diagnosticsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("diagnostics.renderer")
        },
    ) {
        repeat(DIAGNOSTICS_RENDERER_CYCLES_PER_ITERATION) {
            val beforeRefresh = scenarioTargetText(
                "diagnostics.renderer",
                DemoTargetRole.State,
            )
            clickScenarioTarget("diagnostics.renderer", DemoTargetRole.PrimaryAction)
            val afterRefresh = waitForScenarioTargetTextChange(
                "diagnostics.renderer",
                DemoTargetRole.State,
                beforeRefresh,
            )
            clickScenarioTarget("diagnostics.renderer", DemoTargetRole.Reset)
            waitForScenarioTargetTextChange(
                "diagnostics.renderer",
                DemoTargetRole.State,
                afterRefresh,
            )
        }
    }

    @Test
    fun diagnosticsThemeLongFlingToBottomAndBackRevision2() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDiagnosticsThemeAndWait()
            waitForPerformanceMeasurementSettle()
        },
    ) {
        repeat(DIAGNOSTICS_THEME_FLING_COUNT) {
            flingPageUp()
        }
        waitForScenarioTarget("diagnostics.theme", DemoTargetRole.SecondaryTarget)
        repeat(DIAGNOSTICS_THEME_FLING_COUNT) {
            flingPageDown()
        }
        waitForScenarioTarget("diagnostics.theme", DemoTargetRole.Target)
    }

    @Test
    fun interopBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("interop.android-view")
        },
    ) {
        val initial = scenarioTargetText("interop.android-view", DemoTargetRole.State)
        clickScenarioTarget("interop.android-view", DemoTargetRole.PrimaryAction)
        waitForScenarioTargetTextChange(
            "interop.android-view",
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget("interop.android-view", DemoTargetRole.Reset)
        waitForScenarioTargetText("interop.android-view", DemoTargetRole.State, initial)
    }

    @Test
    fun collectionsScrollRevision3() {
        var scrollBounds = Rect()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = collectionStressMetrics(),
            compilationMode = CompilationMode.Partial(),
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startDemoScenarioAndWait("collection.stress")
                scrollBounds = scenarioTargetBounds("collection.stress", DemoTargetRole.Target)
                waitForPerformanceMeasurementSettle()
            },
        ) {
            repeat(COLLECTION_SCROLL_SWIPES_PER_DIRECTION) {
                swipeWithinBounds(scrollBounds, PageSwipeDirection.TowardBottom)
            }
            repeat(COLLECTION_SCROLL_SWIPES_PER_DIRECTION) {
                swipeWithinBounds(scrollBounds, PageSwipeDirection.TowardTop)
            }
        }
    }

    @Test
    fun collectionsStressMutationRevision3() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = collectionStressMetrics(),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("collection.stress")
            waitForPerformanceMeasurementSettle()
        },
    ) {
        repeat(COLLECTION_MUTATION_CYCLES_PER_ITERATION) {
            val initial = scenarioTargetText("collection.stress", DemoTargetRole.State)
            clickScenarioTarget("collection.stress", DemoTargetRole.PrimaryAction)
            val rotated = waitForScenarioTargetTextChange(
                "collection.stress",
                DemoTargetRole.State,
                initial,
            )
            clickScenarioTarget("collection.stress", DemoTargetRole.SecondaryAction)
            val inserted = waitForScenarioTargetTextChange(
                "collection.stress",
                DemoTargetRole.State,
                rotated,
            )
            clickScenarioTarget("collection.stress", DemoTargetRole.Reset)
            val reset = waitForScenarioTargetTextChange(
                "collection.stress",
                DemoTargetRole.State,
                inserted,
            )
            assertEquals(initial, reset)
        }
    }

    @Test
    fun patchUpdates() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoScenarioAndWait("runtime.view-patch")
        },
    ) {
        val initial = scenarioTargetText("runtime.view-patch", DemoTargetRole.State)
        clickScenarioTarget("runtime.view-patch", DemoTargetRole.PrimaryAction)
        val first = waitForScenarioTargetTextChange(
            "runtime.view-patch",
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget("runtime.view-patch", DemoTargetRole.PrimaryAction)
        val second = waitForScenarioTargetTextChange(
            "runtime.view-patch",
            DemoTargetRole.State,
            first,
        )
        clickScenarioTarget("runtime.view-patch", DemoTargetRole.Reset)
        waitForScenarioTargetTextChange("runtime.view-patch", DemoTargetRole.State, second)
    }

    private fun collectionStressMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

}

private const val DIAGNOSTICS_THEME_FLING_COUNT = 8
private const val DIAGNOSTICS_RENDERER_CYCLES_PER_ITERATION = 8
private const val COLLECTION_SCROLL_SWIPES_PER_DIRECTION = 8
private const val COLLECTION_MUTATION_CYCLES_PER_ITERATION = 8
