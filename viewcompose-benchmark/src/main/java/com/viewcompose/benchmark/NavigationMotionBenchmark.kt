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
 * Release/R8 frame-time coverage for framework-owned navigation transitions.
 *
 * Push and system Back are measured independently so the report can distinguish destination
 * creation from the already-retained destination path used by Pop.
 */
@RunWith(AndroidJUnit4::class)
class NavigationMotionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun pushDestination() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startSystemNavigationAndWait()
            scrollUntilText(PUSH_ACTION_TEXT)
        },
    ) {
        clickVisibleTextWithoutIdle(PUSH_ACTION_TEXT)
        waitForNavigationMotion()
        waitForText(DETAIL_DESTINATION_TEXT)
    }

    @Test
    fun systemBackPopDestination() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = RELEASE_BASELINE_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startSystemNavigationAndWait()
            scrollUntilText(PUSH_ACTION_TEXT)
            clickVisibleTextWithoutIdle(PUSH_ACTION_TEXT)
            waitForNavigationMotion()
            waitForText(DETAIL_DESTINATION_TEXT)
        },
    ) {
        device.pressBack()
        waitForNavigationMotion()
        waitForText(PUSH_ACTION_TEXT)
    }

    private companion object {
        const val PUSH_ACTION_TEXT = "Push 下一页面"
        const val DETAIL_DESTINATION_TEXT = "首页详情"
    }
}
