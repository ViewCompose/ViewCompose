package com.viewcompose

import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PagingDemoDeviceTest {
    @Test
    fun controlledFixture_exposesInitialAppendEmptyErrorAndResetStates() {
        launchDemoScenarioActivity(
            activityClass = CollectionsActivity::class.java,
            scenarioId = "collection.paging",
        ).use { scenario ->
            waitForState(scenario, R.string.demo_collections_paging_body_initial_loading, "0")

            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_body_content, "10")

            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_load_loading, "10")
            click(scenario, R.id.demo_collection_paging_secondary_action)
            click(scenario, R.id.demo_collection_paging_secondary_action)
            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_load_error, "10")

            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_load_loading, "10")
            click(scenario, R.id.demo_collection_paging_secondary_action)
            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_body_content, "20")

            click(scenario, R.id.demo_collection_paging_reset)
            waitForState(scenario, R.string.demo_collections_paging_body_initial_loading, "0")
            click(scenario, R.id.demo_collection_paging_secondary_action)
            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_body_empty, "0")

            click(scenario, R.id.demo_collection_paging_reset)
            waitForState(scenario, R.string.demo_collections_paging_body_initial_loading, "0")
            click(scenario, R.id.demo_collection_paging_secondary_action)
            click(scenario, R.id.demo_collection_paging_secondary_action)
            click(scenario, R.id.demo_collection_paging_primary_action)
            waitForState(scenario, R.string.demo_collections_paging_body_initial_error, "0")
        }
    }

    private fun click(
        scenario: ActivityScenario<CollectionsActivity>,
        viewId: Int,
    ) {
        scenario.onActivity { activity -> activity.clickScenarioViewById(viewId) }
        waitForUiIdle()
    }

    private fun waitForState(
        scenario: ActivityScenario<CollectionsActivity>,
        expectedLabelRes: Int,
        expectedLoadedCount: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + STATE_TIMEOUT_MS
        var latest = ""
        var expectedLabel = ""
        while (SystemClock.uptimeMillis() < deadline) {
            waitForUiIdle()
            scenario.onActivity { activity ->
                expectedLabel = activity.getString(expectedLabelRes)
                latest = activity
                    .requireScenarioViewById<TextView>(R.id.demo_collection_paging_state)
                    .text
                    .toString()
            }
            if (latest.contains(expectedLabel) && latest.endsWith(expectedLoadedCount)) {
                return
            }
            SystemClock.sleep(16L)
        }
        assertTrue(
            "Expected Paging state '$expectedLabel' with loaded count $expectedLoadedCount, got '$latest'.",
            false,
        )
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
