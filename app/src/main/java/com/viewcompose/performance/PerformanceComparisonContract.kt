package com.viewcompose.performance

import android.content.Intent

/**
 * benchmark 启动性能对比页时传递渲染引擎的 extra key。
 * Intent extra key used by benchmarks to pass the rendering engine to the comparison screen.
 */
const val EXTRA_PERFORMANCE_ENGINE: String = "performance_engine"

/**
 * benchmark 启动性能对比页时传递测试场景的 extra key。
 * Intent extra key used by benchmarks to pass the measured scenario to the comparison screen.
 */
const val EXTRA_PERFORMANCE_SCENARIO: String = "performance_scenario"

/**
 * benchmark 指定高级阴影后端策略的 extra key。
 * Intent extra key used by benchmarks to select the advanced-shadow backend policy.
 */
const val EXTRA_SHADOW_RENDER_POLICY: String = "shadow_render_policy"

/** Node count used by the dedicated ConstraintLayout comparison protocol. */
const val EXTRA_CONSTRAINT_LAYOUT_NODE_COUNT: String = "constraint_layout_node_count"

/** Workload used by the dedicated ConstraintLayout comparison protocol. */
const val EXTRA_CONSTRAINT_LAYOUT_WORKLOAD: String = "constraint_layout_workload"

/** Supported mutation classes for the dedicated ConstraintLayout comparison protocol. */
internal enum class ConstraintLayoutPerformanceWorkload(val wireValue: String) {
    Stable("stable"),
    Scalar("scalar"),
    Helper("helper"),
    Topology("topology"),
    ;

    companion object {
        fun fromWireValue(value: String): ConstraintLayoutPerformanceWorkload =
            entries.firstOrNull { it.wireValue == value }
                ?: error("Unknown ConstraintLayout workload: $value")
    }
}

/** One validated scale/workload pair for the dedicated ConstraintLayout comparison protocol. */
internal data class ConstraintLayoutPerformanceProfile(
    val nodeCount: Int,
    val workload: ConstraintLayoutPerformanceWorkload,
) {
    companion object {
        val SupportedNodeCounts: Set<Int> = setOf(10, 50, 100)

        fun fromIntent(intent: Intent): ConstraintLayoutPerformanceProfile? {
            val hasNodeCount = intent.hasExtra(EXTRA_CONSTRAINT_LAYOUT_NODE_COUNT)
            val hasWorkload = intent.hasExtra(EXTRA_CONSTRAINT_LAYOUT_WORKLOAD)
            if (!hasNodeCount && !hasWorkload) return null
            check(hasNodeCount && hasWorkload) {
                "ConstraintLayout performance profile requires both node count and workload."
            }
            val nodeCount = intent.getIntExtra(EXTRA_CONSTRAINT_LAYOUT_NODE_COUNT, -1)
            check(nodeCount in SupportedNodeCounts) {
                "Unsupported ConstraintLayout node count: $nodeCount"
            }
            return ConstraintLayoutPerformanceProfile(
                nodeCount = nodeCount,
                workload = ConstraintLayoutPerformanceWorkload.fromWireValue(
                    checkNotNull(intent.getStringExtra(EXTRA_CONSTRAINT_LAYOUT_WORKLOAD)),
                ),
            )
        }
    }
}

/**
 * 性能对比页支持的渲染引擎。
 * Rendering engines supported by the performance comparison screen.
 */
internal enum class PerformanceEngine(
    val wireValue: String,
    val displayName: String,
) {
    ViewCompose(
        wireValue = "viewcompose",
        displayName = "ViewCompose",
    ),
    Compose(
        wireValue = "compose",
        displayName = "Compose",
    ),
    AndroidViews(
        wireValue = "android_views",
        displayName = "Android Views",
    ),
    ;

    companion object {
        /**
         * 从 Intent 中解析渲染引擎，未知值直接失败以暴露错误 benchmark 配置。
         * Resolves the engine from an Intent and fails fast for invalid benchmark configuration.
         */
        fun fromIntent(intent: Intent): PerformanceEngine {
            val value = intent.getStringExtra(EXTRA_PERFORMANCE_ENGINE)
            return entries.firstOrNull { it.wireValue == value }
                ?: error("Unknown performance engine: $value")
        }
    }
}

/**
 * 性能对比页支持的测试场景。
 * Scenarios supported by the performance comparison screen.
 */
internal enum class PerformanceScenario(
    val wireValue: String,
    val demoScenarioId: String,
) {
    List(
        wireValue = "list",
        demoScenarioId = "performance.list",
    ),
    ComplexLayout(
        wireValue = "complex_layout",
        demoScenarioId = "performance.complex-layout",
    ),
    ShadowList(
        wireValue = "shadow_list",
        demoScenarioId = "performance.shadow-list",
    ),
    ShadowComplexLayout(
        wireValue = "shadow_complex_layout",
        demoScenarioId = "performance.shadow-complex-layout",
    ),
    Paging(
        wireValue = "paging",
        demoScenarioId = "performance.paging",
    ),
    ;

    companion object {
        /**
         * 从 Intent 中解析测试场景，未知值直接失败以暴露错误 benchmark 配置。
         * Resolves the scenario from an Intent and fails fast for invalid benchmark configuration.
         */
        fun fromIntent(intent: Intent): PerformanceScenario {
            val value = intent.getStringExtra(EXTRA_PERFORMANCE_SCENARIO)
            return entries.firstOrNull { it.wireValue == value }
                ?: error("Unknown performance scenario: $value")
        }
    }
}
