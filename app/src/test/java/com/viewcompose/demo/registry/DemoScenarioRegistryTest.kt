package com.viewcompose.demo.registry

import android.content.Intent
import android.view.View
import com.viewcompose.R
import com.viewcompose.StateActivity
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationContract
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoBenchmarkContract
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoVerificationKind
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.performance.EXTRA_PERFORMANCE_ENGINE
import com.viewcompose.performance.EXTRA_PERFORMANCE_SCENARIO
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.modifier.TestTagModifierElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoScenarioRegistryTest {
    @Test
    fun `registry IDs routes and target resources are complete`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val scenarios = DemoScenarioRegistry.all()

        assertTrue(scenarios.isNotEmpty())
        assertEquals(scenarios.size, scenarios.map { it.id }.distinct().size)
        scenarios.forEach { scenario ->
            assertEquals(
                scenario.automation.targets.size,
                scenario.automation.targets.map { it.role }.distinct().size,
            )
            scenario.automation.targets.forEach { target ->
                assertEquals(
                    target.resourceName,
                    resources.getResourceEntryName(target.androidViewId),
                )
            }
        }
    }

    @Test
    fun `strict route carries scenario identity and preserves external extras`() {
        val context = RuntimeEnvironment.getApplication()
        val scenario = DemoScenarioRegistry.require(DemoScenarioIds.RuntimeViewPatch.value)
        val source = Intent().apply {
            putExtra("preserved_external_value", "kept")
        }

        val intent = DemoScenarioRegistry.createLaunchIntent(context, scenario, source)

        assertEquals(StateActivity::class.java.name, intent.component?.className)
        assertEquals(scenario.id.value, intent.getStringExtra(EXTRA_DEMO_SCENARIO_ID))
        assertEquals("kept", intent.getStringExtra("preserved_external_value"))
        assertEquals(
            StateActivity::class.java,
            DemoScenarioRegistry.require(DemoScenarioIds.RuntimeKeyIdentity.value).route.activityClass,
        )
        assertThrows(IllegalArgumentException::class.java) {
            DemoScenarioRegistry.require("missing.scenario")
        }
    }

    @Test
    fun `performance route fixes workload identity but allows its engine dimension`() {
        val context = RuntimeEnvironment.getApplication()
        val scenario = DemoScenarioRegistry.require(DemoScenarioIds.PerformanceShadowList.value)
        val source = Intent().apply {
            putExtra(EXTRA_PERFORMANCE_ENGINE, "compose")
            putExtra(EXTRA_PERFORMANCE_SCENARIO, "complex_layout")
        }

        val intent = DemoScenarioRegistry.createLaunchIntent(context, scenario, source)

        assertEquals("compose", intent.getStringExtra(EXTRA_PERFORMANCE_ENGINE))
        assertEquals("shadow_list", intent.getStringExtra(EXTRA_PERFORMANCE_SCENARIO))
        assertEquals(scenario.id.value, intent.getStringExtra(EXTRA_DEMO_SCENARIO_ID))
    }

    @Test
    fun `benchmark workload revisions match the accepted contracts`() {
        val expected = mapOf(
            DemoScenarioIds.NavigationSystem to 3,
            DemoScenarioIds.DesignBundleMaterial3 to 3,
            DemoScenarioIds.DesignBundleContrast to 3,
            DemoScenarioIds.PerformanceList to 3,
            DemoScenarioIds.PerformanceComplexLayout to 3,
            DemoScenarioIds.PerformanceShadowList to 2,
            DemoScenarioIds.PerformanceShadowComplexLayout to 2,
        )

        expected.forEach { (scenarioId, workloadRevision) ->
            assertEquals(
                workloadRevision,
                DemoScenarioRegistry.require(scenarioId.value).benchmark?.workloadRevision,
            )
        }
    }

    @Test
    fun `validation rejects duplicate identity missing reset and invalid workload`() {
        val source = DemoScenarioRegistry.require(DemoScenarioIds.RuntimeState.value)
        assertThrows(IllegalArgumentException::class.java) {
            DemoScenarioRegistry.validate(listOf(source, source))
        }

        val invalidId = DemoScenarioId("invalid.mutable")
        val missingReset = source.copy(
            id = invalidId,
            automation = DemoAutomationContract.create(
                invalidId,
                Triple(DemoAutomationRole.Root, R.id.demo_runtime_state_root, "demo_invalid_mutable_root"),
                Triple(DemoAutomationRole.Ready, R.id.demo_runtime_state_ready, "demo_invalid_mutable_ready"),
            ),
            benchmark = null,
        )
        assertThrows(IllegalArgumentException::class.java) {
            DemoScenarioRegistry.validate(listOf(missingReset))
        }

        val invalidWorkload = source.copy(
            benchmark = DemoBenchmarkContract(
                workloadRevision = 0,
                actionSequence = listOf(DemoAutomationRole.PrimaryAction),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            DemoScenarioRegistry.validate(listOf(invalidWorkload))
        }

        assertThrows(IllegalArgumentException::class.java) {
            DemoScenarioRegistry.validate(
                listOf(source.copy(verificationKinds = emptySet<DemoVerificationKind>())),
            )
        }
    }

    @Test
    fun `automation modifier applies one target to both bridges replay safely`() {
        val target = DemoScenarioRegistry
            .require(DemoScenarioIds.RuntimeState.value)
            .automation
            .require(DemoAutomationRole.Ready)
        val modifier = Modifier.demoAutomationTarget(target)
        val testTag = modifier.elements.filterIsInstance<TestTagModifierElement>().single()
        val native = modifier.elements.filterIsInstance<NativeViewElement>().single()
        val view = View(RuntimeEnvironment.getApplication())

        native.configure(view)
        native.configure(view)

        assertEquals(target.testTag, testTag.tag)
        assertEquals(target.androidViewId, view.id)
    }
}
