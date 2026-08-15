package com.viewcompose.performance

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PerformanceComparisonContractTest {
    @Test
    fun `every wire engine maps to one strict implementation`() {
        val expected = mapOf(
            "viewcompose" to PerformanceEngine.ViewCompose,
            "compose" to PerformanceEngine.Compose,
            "android_views" to PerformanceEngine.AndroidViews,
        )

        expected.forEach { (wireValue, engine) ->
            val parsed = PerformanceEngine.fromIntent(
                Intent().putExtra(EXTRA_PERFORMANCE_ENGINE, wireValue),
            )
            assertEquals(engine, parsed)
        }
    }

    @Test
    fun `unknown engine fails fast`() {
        val intent = Intent().putExtra(
            EXTRA_PERFORMANCE_ENGINE,
            "unknown",
        )

        assertThrows(IllegalStateException::class.java) {
            PerformanceEngine.fromIntent(intent)
        }
    }

    @Test
    fun `every wire scenario maps to one strict demo scenario`() {
        val expected = mapOf(
            "list" to "performance.list",
            "complex_layout" to "performance.complex-layout",
            "shadow_list" to "performance.shadow-list",
            "shadow_complex_layout" to "performance.shadow-complex-layout",
        )

        expected.forEach { (wireValue, scenarioId) ->
            val parsed = PerformanceScenario.fromIntent(
                Intent().putExtra(EXTRA_PERFORMANCE_SCENARIO, wireValue),
            )
            assertEquals(scenarioId, parsed.demoScenarioId)
        }
    }

    @Test
    fun `unknown scenario fails fast`() {
        val intent = Intent().putExtra(
            EXTRA_PERFORMANCE_SCENARIO,
            "unknown",
        )

        assertThrows(IllegalStateException::class.java) {
            PerformanceScenario.fromIntent(intent)
        }
    }
}
