package com.viewcompose.demo.registry

import android.content.Context
import android.content.Intent
import com.viewcompose.FeedbackActivity
import com.viewcompose.R
import com.viewcompose.ResourceConfigurationActivity
import com.viewcompose.StateActivity
import com.viewcompose.SystemNavigationActivity
import com.viewcompose.demo.contract.DemoAutomationContract
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoBenchmarkContract
import com.viewcompose.demo.contract.DemoHostPolicy
import com.viewcompose.demo.contract.DemoRouteExtra
import com.viewcompose.demo.contract.DemoScenarioCategory
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioRoute
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.contract.DemoVerificationKind
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.performance.EXTRA_PERFORMANCE_ENGINE
import com.viewcompose.performance.EXTRA_PERFORMANCE_SCENARIO
import com.viewcompose.performance.PerformanceComparisonActivity

internal object DemoScenarioIds {
    val RuntimeState = DemoScenarioId("runtime.state")
    val RuntimeViewPatch = DemoScenarioId("runtime.view-patch")
    val EnvironmentResources = DemoScenarioId("environment.resources")
    val OverlayDialog = DemoScenarioId("overlay.dialog")
    val NavigationSystem = DemoScenarioId("navigation.system")
    val PerformanceList = DemoScenarioId("performance.list")
}

internal object DemoScenarioRegistry {
    private val scenarios: List<DemoScenarioSpec> = listOf(
        scenario(
            id = DemoScenarioIds.RuntimeState,
            category = DemoScenarioCategory.Runtime,
            titleRes = R.string.demo_scenario_runtime_state_title,
            summaryRes = R.string.demo_scenario_runtime_state_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(
                activityClass = StateActivity::class.java,
                extras = mapOf("state_page_index" to DemoRouteExtra.IntValue(0)),
            ),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_runtime_state_root,
                ready = R.id.demo_runtime_state_ready,
                primaryAction = R.id.demo_runtime_state_primary_action,
                reset = R.id.demo_runtime_state_reset,
                state = R.id.demo_runtime_state_state,
                target = R.id.demo_runtime_state_target,
            ),
            benchmarkRevision = 1,
        ),
        scenario(
            id = DemoScenarioIds.RuntimeViewPatch,
            category = DemoScenarioCategory.Runtime,
            titleRes = R.string.demo_scenario_runtime_view_patch_title,
            summaryRes = R.string.demo_scenario_runtime_view_patch_summary,
            host = DemoHostPolicy.SharedFixture,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(
                activityClass = StateActivity::class.java,
                extras = mapOf("state_page_index" to DemoRouteExtra.IntValue(2)),
            ),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_runtime_view_patch_root,
                ready = R.id.demo_runtime_view_patch_ready,
                primaryAction = R.id.demo_runtime_view_patch_primary_action,
                reset = R.id.demo_runtime_view_patch_reset,
                state = R.id.demo_runtime_view_patch_state,
                target = R.id.demo_runtime_view_patch_target,
            ),
            benchmarkRevision = 1,
        ),
        scenario(
            id = DemoScenarioIds.EnvironmentResources,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_environment_resources_title,
            summaryRes = R.string.demo_scenario_environment_resources_summary,
            host = DemoHostPolicy.Dedicated,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(ResourceConfigurationActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_environment_resources_root,
                ready = R.id.demo_environment_resources_ready,
                primaryAction = R.id.demo_environment_resources_primary_action,
                reset = R.id.demo_environment_resources_reset,
                state = R.id.demo_environment_resources_state,
                target = R.id.demo_environment_resources_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.OverlayDialog,
            category = DemoScenarioCategory.AndroidIntegration,
            titleRes = R.string.demo_scenario_overlay_dialog_title,
            summaryRes = R.string.demo_scenario_overlay_dialog_summary,
            host = DemoHostPolicy.Overlay,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Visual),
            route = DemoScenarioRoute(
                activityClass = FeedbackActivity::class.java,
                extras = mapOf("feedback_page_index" to DemoRouteExtra.IntValue(1)),
            ),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_overlay_dialog_root,
                ready = R.id.demo_overlay_dialog_ready,
                primaryAction = R.id.demo_overlay_dialog_primary_action,
                reset = R.id.demo_overlay_dialog_reset,
                state = R.id.demo_overlay_dialog_state,
                target = R.id.demo_overlay_dialog_target,
            ),
        ),
        scenario(
            id = DemoScenarioIds.NavigationSystem,
            category = DemoScenarioCategory.Navigation,
            titleRes = R.string.demo_scenario_navigation_system_title,
            summaryRes = R.string.demo_scenario_navigation_system_summary,
            host = DemoHostPolicy.SystemNavigation,
            verificationKinds = setOf(DemoVerificationKind.Manual, DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(SystemNavigationActivity::class.java),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_navigation_system_root,
                ready = R.id.demo_navigation_system_ready,
                primaryAction = R.id.demo_navigation_system_primary_action,
                reset = R.id.demo_navigation_system_reset,
                state = R.id.demo_navigation_system_state,
                target = R.id.demo_navigation_system_target,
            ),
            benchmarkRevision = 1,
        ),
        scenario(
            id = DemoScenarioIds.PerformanceList,
            category = DemoScenarioCategory.Performance,
            titleRes = R.string.demo_scenario_performance_list_title,
            summaryRes = R.string.demo_scenario_performance_list_summary,
            host = DemoHostPolicy.Benchmark,
            verificationKinds = setOf(DemoVerificationKind.Benchmark),
            route = DemoScenarioRoute(
                activityClass = PerformanceComparisonActivity::class.java,
                extras = mapOf(
                    EXTRA_PERFORMANCE_ENGINE to DemoRouteExtra.StringValue("viewcompose"),
                    EXTRA_PERFORMANCE_SCENARIO to DemoRouteExtra.StringValue("list"),
                ),
            ),
            mutable = true,
            ids = TargetIds(
                root = R.id.demo_performance_list_root,
                ready = R.id.demo_performance_list_ready,
                primaryAction = R.id.demo_performance_list_primary_action,
                reset = R.id.demo_performance_list_reset,
                state = R.id.demo_performance_list_state,
                target = R.id.demo_performance_list_target,
            ),
            benchmarkRevision = 1,
        ),
    )

    private val scenariosById: Map<String, DemoScenarioSpec> = scenarios.associateBy { it.id.value }

    init {
        validate(scenarios)
    }

    fun all(): List<DemoScenarioSpec> = scenarios

    fun find(id: String?): DemoScenarioSpec? = id?.let(scenariosById::get)

    fun require(id: String?): DemoScenarioSpec =
        requireNotNull(find(id)) {
            "Unknown demo scenario ID: $id"
        }

    fun fromIntent(intent: Intent?): DemoScenarioSpec? =
        find(intent?.getStringExtra(EXTRA_DEMO_SCENARIO_ID))

    fun createLaunchIntent(
        context: Context,
        scenario: DemoScenarioSpec,
        source: Intent? = null,
    ): Intent {
        return scenario.route.createIntent(context, source).apply {
            putExtra(EXTRA_DEMO_SCENARIO_ID, scenario.id.value)
        }
    }

    internal fun validate(specs: List<DemoScenarioSpec>) {
        require(specs.map { it.id }.distinct().size == specs.size) {
            "Duplicate demo scenario ID"
        }
        specs.forEach { spec ->
            require(spec.titleRes != 0 && spec.summaryRes != 0) {
                "${spec.id} is missing display resources"
            }
            require(spec.verificationKinds.isNotEmpty()) {
                "${spec.id} has no verification kind"
            }
            val targets = spec.automation.targets
            require(spec.automation[DemoAutomationRole.Root] != null) {
                "${spec.id} is missing root"
            }
            require(spec.automation[DemoAutomationRole.Ready] != null) {
                "${spec.id} is missing ready"
            }
            require(targets.map { it.androidViewId }.distinct().size == targets.size) {
                "${spec.id} reuses an Android target ID"
            }
            require(targets.map { it.resourceName }.distinct().size == targets.size) {
                "${spec.id} reuses an Android target resource name"
            }
            require(targets.map { it.testTag }.distinct().size == targets.size) {
                "${spec.id} reuses an in-process target tag"
            }
            targets.forEach { target ->
                require(target.androidViewId != 0) {
                    "${spec.id}/${target.role.wireValue} has no Android resource ID"
                }
                val expectedName = "demo_${spec.id.value.replace('.', '_').replace('-', '_')}_" +
                    target.role.wireValue
                require(target.resourceName == expectedName) {
                    "${spec.id}/${target.role.wireValue} must use $expectedName"
                }
            }
            if (spec.mutable) {
                require(spec.automation[DemoAutomationRole.Reset] != null) {
                    "${spec.id} is mutable but has no reset target"
                }
            }
            spec.benchmark?.let { benchmark ->
                require(benchmark.workloadRevision > 0) {
                    "${spec.id} has an invalid workload revision"
                }
                require(benchmark.actionSequence.isNotEmpty()) {
                    "${spec.id} benchmark has no action sequence"
                }
                benchmark.actionSequence.forEach { role ->
                    require(spec.automation[role] != null) {
                        "${spec.id} benchmark action ${role.wireValue} has no target"
                    }
                }
                require(spec.automation[DemoAutomationRole.State] != null) {
                    "${spec.id} benchmark has no state target"
                }
                require(spec.automation[DemoAutomationRole.Target] != null) {
                    "${spec.id} benchmark has no fixture target"
                }
            }
            if (spec.host == DemoHostPolicy.Benchmark) {
                require(spec.benchmark != null) {
                    "${spec.id} uses the benchmark host without a workload contract"
                }
            }
        }
    }

    private data class TargetIds(
        val root: Int,
        val ready: Int,
        val primaryAction: Int,
        val reset: Int,
        val state: Int,
        val target: Int,
    )

    private fun scenario(
        id: DemoScenarioId,
        category: DemoScenarioCategory,
        titleRes: Int,
        summaryRes: Int,
        host: DemoHostPolicy,
        verificationKinds: Set<DemoVerificationKind>,
        route: DemoScenarioRoute,
        mutable: Boolean,
        ids: TargetIds,
        benchmarkRevision: Int? = null,
    ): DemoScenarioSpec {
        fun target(
            role: DemoAutomationRole,
            androidViewId: Int,
        ): Triple<DemoAutomationRole, Int, String> {
            val resourceName = "demo_${id.value.replace('.', '_').replace('-', '_')}_${role.wireValue}"
            return Triple(role, androidViewId, resourceName)
        }

        return DemoScenarioSpec(
            id = id,
            category = category,
            titleRes = titleRes,
            summaryRes = summaryRes,
            host = host,
            verificationKinds = verificationKinds,
            route = route,
            automation = DemoAutomationContract.create(
                id,
                target(DemoAutomationRole.Root, ids.root),
                target(DemoAutomationRole.Ready, ids.ready),
                target(DemoAutomationRole.PrimaryAction, ids.primaryAction),
                target(DemoAutomationRole.Reset, ids.reset),
                target(DemoAutomationRole.State, ids.state),
                target(DemoAutomationRole.Target, ids.target),
            ),
            mutable = mutable,
            benchmark = benchmarkRevision?.let { revision ->
                DemoBenchmarkContract(
                    workloadRevision = revision,
                    actionSequence = listOf(
                        DemoAutomationRole.PrimaryAction,
                        DemoAutomationRole.Reset,
                    ),
                )
            },
        )
    }
}
