package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 高级阴影在 Lazy 列表和复杂布局中的 ViewCompose/Compose 对照基准。
 * ViewCompose/Compose comparison for advanced shadows in lazy lists and complex layouts.
 *
 * `shadowRenderPolicy` instrumentation 参数可以在不改变场景的情况下选择 `auto`、
 * `exact_bitmap` 或 `render_node`。因此 Bitmap 与 RenderNode 结果仍可使用 Compose 控制组
 * 归一化，避免设备温度和后台负载被误判为后端收益。
 * The `shadowRenderPolicy` instrumentation argument selects `auto`, `exact_bitmap`, or
 * `render_node` without changing workload, retaining Compose as a normalization control.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ShadowPerformanceComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun viewComposeShadowListScroll() {
        measureScroll(
            engine = "viewcompose",
            scenarioId = PERFORMANCE_SHADOW_LIST_SCENARIO,
        )
    }

    @Test
    fun composeShadowListScroll() {
        measureScroll(
            engine = "compose",
            scenarioId = PERFORMANCE_SHADOW_LIST_SCENARIO,
        )
    }

    @Test
    fun viewComposeShadowListMutation() {
        measureMutation(
            engine = "viewcompose",
            scenarioId = PERFORMANCE_SHADOW_LIST_SCENARIO,
        )
    }

    @Test
    fun composeShadowListMutation() {
        measureMutation(
            engine = "compose",
            scenarioId = PERFORMANCE_SHADOW_LIST_SCENARIO,
        )
    }

    @Test
    fun viewComposeShadowComplexLayoutScroll() {
        measureScroll(
            engine = "viewcompose",
            scenarioId = PERFORMANCE_SHADOW_COMPLEX_LAYOUT_SCENARIO,
        )
    }

    @Test
    fun composeShadowComplexLayoutScroll() {
        measureScroll(
            engine = "compose",
            scenarioId = PERFORMANCE_SHADOW_COMPLEX_LAYOUT_SCENARIO,
        )
    }

    @Test
    fun viewComposeShadowComplexLayoutUpdate() {
        measureMutation(
            engine = "viewcompose",
            scenarioId = PERFORMANCE_SHADOW_COMPLEX_LAYOUT_SCENARIO,
        )
    }

    @Test
    fun composeShadowComplexLayoutUpdate() {
        measureMutation(
            engine = "compose",
            scenarioId = PERFORMANCE_SHADOW_COMPLEX_LAYOUT_SCENARIO,
        )
    }

    private fun measureScroll(
        engine: String,
        scenarioId: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = shadowMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = shadowPerformanceIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(
                scenarioId = scenarioId,
                engine = engine,
                shadowRenderPolicy = shadowRenderPolicy(),
            )
        },
    ) {
        repeat(4) {
            swipePageUp()
        }
        repeat(4) {
            swipePageDown()
        }
    }

    private fun measureMutation(
        engine: String,
        scenarioId: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = shadowMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = shadowPerformanceIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(
                scenarioId = scenarioId,
                engine = engine,
                shadowRenderPolicy = shadowRenderPolicy(),
            )
        },
    ) {
        val initial = scenarioTargetText(scenarioId, DemoTargetRole.State)
        clickScenarioTarget(scenarioId, DemoTargetRole.PrimaryAction)
        val updated = waitForScenarioTargetTextChange(
            scenarioId,
            DemoTargetRole.State,
            initial,
        )
        clickScenarioTarget(scenarioId, DemoTargetRole.Reset)
        val reset = waitForScenarioTargetTextChange(
            scenarioId,
            DemoTargetRole.State,
            updated,
        )
        assertEquals(initial, reset)
    }

    private fun shadowMetrics() = listOf(
        FrameTimingMetric(),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private fun shadowRenderPolicy(): String {
        return InstrumentationRegistry.getArguments()
            .getString(SHADOW_POLICY_ARGUMENT)
            ?.takeIf(AllowedShadowPolicies::contains)
            ?: DEFAULT_SHADOW_POLICY
    }

    private fun shadowPerformanceIterations(): Int {
        return InstrumentationRegistry.getArguments()
            .getString(ITERATIONS_ARGUMENT)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: FORMAL_INTERACTION_ITERATIONS
    }

    private companion object {
        const val SHADOW_POLICY_ARGUMENT = "shadowRenderPolicy"
        const val ITERATIONS_ARGUMENT = "shadowPerformanceIterations"
        const val DEFAULT_SHADOW_POLICY = "auto"
        const val PERFORMANCE_SHADOW_LIST_SCENARIO = "performance.shadow-list"
        const val PERFORMANCE_SHADOW_COMPLEX_LAYOUT_SCENARIO =
            "performance.shadow-complex-layout"
        val AllowedShadowPolicies = setOf(
            "auto",
            "exact_bitmap",
            "render_node",
        )
    }
}
