package com.viewcompose

import com.viewcompose.demo.contract.DemoScenarioCategory
import com.viewcompose.demo.contract.DemoVerificationKind
import com.viewcompose.demo.registry.DemoScenarioRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoCatalogFilterTest {
    private val scenarios = DemoScenarioRegistry.all().map { scenario ->
        LocalizedDemoScenario(
            scenario = scenario,
            title = "Localized ${scenario.id.value}",
            summary = "Summary for ${scenario.id.value}",
        )
    }

    @Test
    fun `query matches stable identity and localized copy without locale-sensitive casing`() {
        assertEquals(
            listOf("runtime.view-patch"),
            filterDemoScenarios(
                scenarios = scenarios,
                query = "VIEW-PATCH",
                category = null,
                kind = null,
            ).map { item -> item.scenario.id.value },
        )
        assertEquals(
            listOf("environment.resources"),
            filterDemoScenarios(
                scenarios = scenarios.map { item ->
                    if (item.scenario.id.value == "environment.resources") {
                        item.copy(title = "多语言资源")
                    } else {
                        item
                    }
                },
                query = "多语言",
                category = null,
                kind = null,
            ).map { item -> item.scenario.id.value },
        )
    }

    @Test
    fun `category and verification filters compose deterministically`() {
        assertEquals(
            listOf("environment.resources", "overlay.dialog"),
            filterDemoScenarios(
                scenarios = scenarios,
                query = "",
                category = DemoScenarioCategory.AndroidIntegration,
                kind = DemoVerificationKind.Visual,
            ).map { item -> item.scenario.id.value },
        )
        assertEquals(
            listOf("performance.list"),
            filterDemoScenarios(
                scenarios = scenarios,
                query = "",
                category = DemoScenarioCategory.Performance,
                kind = DemoVerificationKind.Benchmark,
            ).map { item -> item.scenario.id.value },
        )
    }
}
