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
    fun `parses every shadow performance scenario`() {
        val shadowList = Intent().putExtra(
            EXTRA_PERFORMANCE_SCENARIO,
            "shadow_list",
        )
        val shadowComplex = Intent().putExtra(
            EXTRA_PERFORMANCE_SCENARIO,
            "shadow_complex_layout",
        )

        assertEquals(
            PerformanceScenario.ShadowList,
            PerformanceScenario.fromIntent(shadowList),
        )
        assertEquals(
            PerformanceScenario.ShadowComplexLayout,
            PerformanceScenario.fromIntent(shadowComplex),
        )
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
