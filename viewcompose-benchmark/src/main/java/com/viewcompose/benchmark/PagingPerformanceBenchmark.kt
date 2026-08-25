package com.viewcompose.benchmark

import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Release frame and memory baseline for the fixed one-million-position Paging workload. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class PagingPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun pagingAppendDrop() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = pagingMetrics(),
            compilationMode = CompilationMode.None(),
            iterations = FORMAL_INTERACTION_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startPagingScenarioAndWait()
            },
        ) {
            repeat(PAGING_PAGE_ADVANCE_COUNT) { index ->
                performScenarioTargetClick(
                    PAGING_SCENARIO,
                    DemoTargetRole.PrimaryAction,
                )
                waitForPagingState(
                    query = 0,
                    target = (index + 1) * PAGING_PAGE_SIZE,
                )
                assertPagingWindowBounded()
            }
        }
    }

    @Test
    fun pagingQueryReplacement() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = pagingMetrics(),
            compilationMode = CompilationMode.None(),
            iterations = FORMAL_INTERACTION_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startPagingScenarioAndWait()
                repeat(PAGING_PAGE_ADVANCE_COUNT) { index ->
                    performScenarioTargetClick(
                        PAGING_SCENARIO,
                        DemoTargetRole.PrimaryAction,
                    )
                    waitForPagingState(
                        query = 0,
                        target = (index + 1) * PAGING_PAGE_SIZE,
                    )
                }
            },
        ) {
            repeat(PAGING_QUERY_REPLACEMENT_COUNT) { index ->
                val query = (index + 1) % PAGING_QUERY_COUNT
                performScenarioTargetClick(
                    PAGING_SCENARIO,
                    DemoTargetRole.SecondaryAction,
                )
                waitForPagingState(
                    query = query,
                    target = PAGING_PAGE_ADVANCE_COUNT * PAGING_PAGE_SIZE,
                )
                assertPagingWindowBounded()
            }
        }
    }

    @Test
    fun pagingScroll() {
        lateinit var listBounds: Rect
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = pagingMetrics(),
            compilationMode = CompilationMode.None(),
            iterations = FORMAL_INTERACTION_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                startPagingScenarioAndWait()
                listBounds = scenarioTargetBounds(PAGING_SCENARIO, DemoTargetRole.Target)
            },
        ) {
            repeat(PAGING_SCROLL_CYCLES) {
                swipeWithinBounds(listBounds, PageSwipeDirection.TowardBottom)
            }
            repeat(PAGING_SCROLL_CYCLES) {
                swipeWithinBounds(listBounds, PageSwipeDirection.TowardTop)
            }
            assertPagingWindowBounded()
        }
    }

    private fun pagingMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private fun MacrobenchmarkScope.startPagingScenarioAndWait() {
        startPerformanceScenarioAndWait(
            scenarioId = PAGING_SCENARIO,
            engine = "viewcompose",
        )
        waitForPagingState(query = 0, target = 0)
        assertPagingWindowBounded()
    }

    private fun MacrobenchmarkScope.waitForPagingState(
        query: Int,
        target: Int,
    ) {
        val expected = "q=$query;target=$target;ready=1"
        val deadline = SystemClock.elapsedRealtime() + UI_WAIT_TIMEOUT_MS
        var current = scenarioTargetText(PAGING_SCENARIO, DemoTargetRole.State)
        while (current != expected && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(PAGING_STATE_POLL_INTERVAL_MS)
            current = scenarioTargetText(PAGING_SCENARIO, DemoTargetRole.State)
        }
        assertEquals("Paging presentation did not reach its requested target.", expected, current)
    }

    private fun MacrobenchmarkScope.assertPagingWindowBounded() {
        val state = scenarioTargetText(PAGING_SCENARIO, DemoTargetRole.SecondaryTarget)
        val match = PAGING_WINDOW_PATTERN.matchEntire(state)
        val loaded = checkNotNull(match) { "Unexpected Paging window state: $state" }
            .groupValues[1]
            .toInt()
        val maximum = match.groupValues[2].toInt()
        assertEquals(PAGING_MAX_LOADED_ITEMS, maximum)
        check(loaded in 1..maximum) { "Paging loaded window exceeded its bound: $state" }
    }

    private companion object {
        const val PAGING_SCENARIO = "performance.paging"
        const val PAGING_PAGE_SIZE = 32
        const val PAGING_PAGE_ADVANCE_COUNT = 8
        const val PAGING_QUERY_COUNT = 2
        const val PAGING_QUERY_REPLACEMENT_COUNT = 8
        const val PAGING_SCROLL_CYCLES = 8
        const val PAGING_MAX_LOADED_ITEMS = 96
        const val PAGING_STATE_POLL_INTERVAL_MS = 16L
        val PAGING_WINDOW_PATTERN = Regex("loaded=(\\d+);max=(\\d+)")
    }
}
