package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class DemoInteractionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun chapterSwitch() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "foundations",
                expectedText = "Foundations",
            )
        },
    ) {
        startDemoActivityAndWait(
            moduleKey = "state",
            expectedText = "State Benchmark Anchor",
        )
    }

    @Test
    fun themeSwitch() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "environment",
                expectedText = "Environment",
            )
            waitForText("Light")
            waitForText("Dark")
        },
    ) {
        clickText("Dark")
        waitForText("Dark")
        clickText("Light")
        waitForText("Light")
    }

    @Test
    fun foundationsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "foundations",
                expectedText = "Foundations",
            )
            scrollUntilText("Foundations Benchmark Off")
            scrollUntilText("Reset Foundations Benchmark")
        },
    ) {
        clickText("Foundations Benchmark Off")
        waitForText("Foundations Benchmark On")
        clickText("Reset Foundations Benchmark")
        waitForText("Foundations Benchmark Off")
    }

    @Test
    fun layoutsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "layouts",
                expectedText = "Layouts",
            )
            scrollUntilText("Layouts Benchmark Compact")
            scrollUntilText("Reset Layouts Benchmark")
        },
    ) {
        clickText("Layouts Benchmark Compact")
        waitForText("Layouts Benchmark Expanded")
        clickText("Reset Layouts Benchmark")
        waitForText("Layouts Benchmark Compact")
    }

    @Test
    fun inputBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "input",
                expectedText = "Input",
            )
            scrollUntilText("Input Benchmark Compact")
            scrollUntilText("Reset Input Benchmark")
        },
    ) {
        clickText("Input Benchmark Compact")
        waitForText("Input Benchmark Expanded")
        clickText("Reset Input Benchmark")
        waitForText("Input Benchmark Compact")
    }

    @Test
    fun collectionsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "collections",
                expectedText = "Collections",
            )
            scrollUntilText("Collections Benchmark A-B-C")
            scrollUntilText("Reset Collections Benchmark")
        },
    ) {
        clickText("Collections Benchmark A-B-C")
        waitForText("Collections Benchmark C-A-B")
        clickText("Reset Collections Benchmark")
        waitForText("Collections Benchmark A-B-C")
    }

    @Test
    fun stateBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "state",
                expectedText = "State Benchmark Anchor",
            )
            scrollUntilText("Advance State Benchmark 0")
            scrollUntilText("Reset State Benchmark")
        },
    ) {
        clickText("Advance State Benchmark 0")
        waitForText("Advance State Benchmark 1")
        clickText("Reset State Benchmark")
        waitForText("Advance State Benchmark 0")
    }

    @Test
    fun diagnosticsBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "diagnostics",
                expectedText = "Diagnostics",
            )
            scrollUntilText("Refresh Diagnostics Benchmark")
            scrollUntilText("Reset Diagnostics Benchmark")
        },
    ) {
        clickText("Refresh Diagnostics Benchmark")
        waitForText("Diagnostics refresh count 1")
        clickText("Reset Diagnostics Benchmark")
        waitForText("Diagnostics refresh count 0")
    }

    @Test
    fun diagnosticsThemeLongFlingToBottomAndBack() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDiagnosticsThemeAndWait()
        },
    ) {
        repeat(DIAGNOSTICS_THEME_FLING_COUNT) {
            flingPageUp()
        }
        assertVisibleText("Expected")
        repeat(DIAGNOSTICS_THEME_FLING_COUNT) {
            flingPageDown()
        }
        assertVisibleText("Chapter Pages")
    }

    @Test
    fun diagnosticsTabSwitchThenImmediateLongFling() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDiagnosticsThemeAndWait()
        },
    ) {
        listOf("渲染器", "缺口", "主题").forEach { tab ->
            clickChapterTab(tab, waitForIdle = false)
            // The first forceful gesture intentionally overlaps the tab's initial mount and layout.
            flingPageUp()
            repeat(DIAGNOSTICS_THEME_FLING_COUNT - 1) {
                flingPageUp()
            }
            assertVisibleText("Expected")
            repeat(DIAGNOSTICS_THEME_FLING_COUNT) {
                flingPageDown()
            }
            assertVisibleText("Chapter Pages")
        }
    }

    @Test
    fun interopBenchmarkAnchor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "interop",
                expectedText = "Interop",
            )
            scrollUntilText("Interop Benchmark Primary")
            scrollUntilText("Reset Interop Benchmark")
        },
    ) {
        clickText("Interop Benchmark Primary")
        waitForText("Interop Benchmark Alternate")
        clickText("Reset Interop Benchmark")
        waitForText("Interop Benchmark Primary")
    }

    @Test
    fun collectionsScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "collections",
                expectedText = "Collections",
            )
        },
    ) {
        swipePageUp()
        swipePageUp()
        swipePageUp()
    }

    @Test
    fun patchUpdates() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
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

    @Test
    fun diagnosticsRefreshAfterPatch() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startDemoActivityAndWait(
                moduleKey = "state",
                expectedText = "State Benchmark Anchor",
            )
            scrollUntilText("Advance State Benchmark 0")
            clickText("Advance State Benchmark 0")
            waitForText("Advance State Benchmark 1")
        },
    ) {
        startDemoActivityAndWait(
            moduleKey = "diagnostics",
            expectedText = "Diagnostics",
        )
    }
}

private const val DIAGNOSTICS_THEME_FLING_COUNT = 8
