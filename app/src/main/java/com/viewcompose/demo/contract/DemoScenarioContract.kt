package com.viewcompose.demo.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.IdRes
import androidx.annotation.StringRes

internal const val EXTRA_DEMO_SCENARIO_ID: String = "demo_scenario_id"

@JvmInline
internal value class DemoScenarioId(
    val value: String,
) {
    init {
        require(value.matches(ID_PATTERN)) {
            "Invalid demo scenario ID: $value"
        }
    }

    override fun toString(): String = value

    private companion object {
        val ID_PATTERN = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
    }
}

internal enum class DemoScenarioCategory {
    Runtime,
    Rendering,
    Collections,
    Input,
    AndroidIntegration,
    Navigation,
    DesignSystems,
    Performance,
}

internal enum class DemoHostPolicy {
    SharedFixture,
    Dedicated,
    Overlay,
    SystemNavigation,
    Benchmark,
}

internal enum class DemoVerificationKind {
    Manual,
    Visual,
    Benchmark,
}

internal enum class DemoAutomationRole(
    val wireValue: String,
) {
    Root("root"),
    Ready("ready"),
    PrimaryAction("primary_action"),
    SecondaryAction("secondary_action"),
    Reset("reset"),
    State("state"),
    Target("target"),
    SecondaryTarget("secondary_target"),
}

internal data class DemoAutomationTarget(
    val scenarioId: DemoScenarioId,
    val role: DemoAutomationRole,
    @IdRes val androidViewId: Int,
    val resourceName: String,
) {
    val testTag: String = "demo.${scenarioId.value}.${role.wireValue}"
}

internal class DemoAutomationContract private constructor(
    private val targetsByRole: Map<DemoAutomationRole, DemoAutomationTarget>,
) {
    val targets: Collection<DemoAutomationTarget>
        get() = targetsByRole.values

    operator fun get(role: DemoAutomationRole): DemoAutomationTarget? = targetsByRole[role]

    fun require(role: DemoAutomationRole): DemoAutomationTarget =
        checkNotNull(targetsByRole[role]) {
            "Missing ${role.wireValue} target"
        }

    companion object {
        fun create(
            scenarioId: DemoScenarioId,
            vararg targets: Triple<DemoAutomationRole, Int, String>,
        ): DemoAutomationContract {
            val resolved = targets.map { (role, id, name) ->
                DemoAutomationTarget(
                    scenarioId = scenarioId,
                    role = role,
                    androidViewId = id,
                    resourceName = name,
                )
            }
            require(resolved.map(DemoAutomationTarget::role).distinct().size == resolved.size) {
                "Duplicate target role for ${scenarioId.value}"
            }
            return DemoAutomationContract(resolved.associateBy(DemoAutomationTarget::role))
        }
    }
}

internal data class DemoBenchmarkContract(
    val workloadRevision: Int,
    val actionSequence: List<DemoAutomationRole>,
)

internal sealed interface DemoRouteExtra {
    fun put(intent: Intent, key: String)

    data class IntValue(val value: Int) : DemoRouteExtra {
        override fun put(intent: Intent, key: String) {
            intent.putExtra(key, value)
        }
    }

    data class StringValue(val value: String) : DemoRouteExtra {
        override fun put(intent: Intent, key: String) {
            intent.putExtra(key, value)
        }
    }
}

internal data class DemoScenarioRoute(
    val activityClass: Class<out Activity>,
    val extras: Map<String, DemoRouteExtra> = emptyMap(),
    val callerOverrideableExtraKeys: Set<String> = emptySet(),
) {
    init {
        require(callerOverrideableExtraKeys.all(extras::containsKey)) {
            "Caller-overridable route extras must declare a deterministic default"
        }
    }

    fun createIntent(
        context: Context,
        source: Intent? = null,
    ): Intent {
        return Intent(context, activityClass).apply {
            source?.extras?.let { sourceExtras -> putExtras(sourceExtras) }
            // Declared route values define the scenario's deterministic initial state and therefore
            // win over stale launcher extras. A narrowly declared workload dimension may retain a
            // caller value, but only because the route also owns its deterministic default.
            this@DemoScenarioRoute.extras.forEach { (key, value) ->
                if (key !in callerOverrideableExtraKeys || !hasExtra(key)) {
                    value.put(this, key)
                }
            }
        }
    }
}

internal data class DemoScenarioSpec(
    val id: DemoScenarioId,
    val category: DemoScenarioCategory,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val host: DemoHostPolicy,
    val verificationKinds: Set<DemoVerificationKind>,
    val route: DemoScenarioRoute,
    val automation: DemoAutomationContract,
    val mutable: Boolean,
    val benchmark: DemoBenchmarkContract? = null,
)
