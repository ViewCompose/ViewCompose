package com.viewcompose

import android.content.Intent
import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.performance.EXTRA_PERFORMANCE_ENGINE
import com.viewcompose.performance.EXTRA_PERFORMANCE_SCENARIO
import com.viewcompose.performance.PerformanceComparisonActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device contract for the fixed Paging performance route. */
@RunWith(AndroidJUnit4::class)
class PagingPerformanceDeviceTest {
    @Test
    fun appendDropQueryReplacementAndResetStayBounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, PerformanceComparisonActivity::class.java)
            .putExtra(EXTRA_DEMO_SCENARIO_ID, PAGING_SCENARIO)
            .putExtra(EXTRA_PERFORMANCE_ENGINE, "viewcompose")
            .putExtra(EXTRA_PERFORMANCE_SCENARIO, "paging")

        launchDemoActivity<PerformanceComparisonActivity>(intent).use { scenario ->
            waitForPagingState(scenario, query = 0, target = 0)

            repeat(PAGE_ADVANCE_COUNT) { index ->
                scenario.onActivity { activity ->
                    activity.clickScenarioViewById(R.id.demo_performance_paging_primary_action)
                }
                waitForPagingState(
                    scenario = scenario,
                    query = 0,
                    target = (index + 1) * PAGE_SIZE,
                )
                assertBoundedWindow(scenario)
            }

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_performance_paging_secondary_action)
            }
            waitForPagingState(
                scenario = scenario,
                query = 1,
                target = PAGE_ADVANCE_COUNT * PAGE_SIZE,
            )
            assertBoundedWindow(scenario)

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_performance_paging_reset)
            }
            waitForPagingState(scenario, query = 0, target = 0)
            assertBoundedWindow(scenario)
        }
    }

    private fun waitForPagingState(
        scenario: ActivityScenario<PerformanceComparisonActivity>,
        query: Int,
        target: Int,
    ) {
        val expected = "q=$query;target=$target;ready=1"
        val deadline = SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MS
        var current = ""
        while (current != expected && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                current = activity
                    .findViewById<TextView>(R.id.demo_performance_paging_state)
                    ?.text
                    ?.toString()
                    .orEmpty()
            }
            if (current != expected) SystemClock.sleep(POLL_INTERVAL_MS)
        }
        assertEquals("Paging state did not settle.", expected, current)
    }

    private fun assertBoundedWindow(
        scenario: ActivityScenario<PerformanceComparisonActivity>,
    ) {
        var current = ""
        scenario.onActivity { activity ->
            current = activity
                .findViewById<TextView>(R.id.demo_performance_paging_secondary_target)
                ?.text
                ?.toString()
                .orEmpty()
        }
        val match = checkNotNull(WINDOW_PATTERN.matchEntire(current)) {
            "Unexpected Paging window state: $current"
        }
        val loaded = match.groupValues[1].toInt()
        val maximum = match.groupValues[2].toInt()
        assertEquals(MAX_LOADED_ITEMS, maximum)
        assertTrue("Paging window was not bounded: $current", loaded in 1..maximum)
    }

    private companion object {
        const val PAGING_SCENARIO = "performance.paging"
        const val PAGE_SIZE = 32
        const val PAGE_ADVANCE_COUNT = 8
        const val MAX_LOADED_ITEMS = 96
        const val WAIT_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 16L
        val WINDOW_PATTERN = Regex("loaded=(\\d+);max=(\\d+)")
    }
}
