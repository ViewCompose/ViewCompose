package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            scenario = "shadow_list",
            expectedText = "ViewCompose Shadow List Ready",
        )
    }

    @Test
    fun composeShadowListScroll() {
        measureScroll(
            engine = "compose",
            scenario = "shadow_list",
            expectedText = "Compose Shadow List Ready",
        )
    }

    @Test
    fun viewComposeShadowListMutation() {
        measureMutation(
            engine = "viewcompose",
            scenario = "shadow_list",
            expectedText = "ViewCompose Shadow List Ready",
            revisionLabel = "List revision",
            mutateAction = "Mutate list",
            resetAction = "Reset list",
        )
    }

    @Test
    fun composeShadowListMutation() {
        measureMutation(
            engine = "compose",
            scenario = "shadow_list",
            expectedText = "Compose Shadow List Ready",
            revisionLabel = "List revision",
            mutateAction = "Mutate list",
            resetAction = "Reset list",
        )
    }

    @Test
    fun viewComposeShadowComplexLayoutScroll() {
        measureScroll(
            engine = "viewcompose",
            scenario = "shadow_complex_layout",
            expectedText = "ViewCompose Shadow Complex Ready",
        )
    }

    @Test
    fun composeShadowComplexLayoutScroll() {
        measureScroll(
            engine = "compose",
            scenario = "shadow_complex_layout",
            expectedText = "Compose Shadow Complex Ready",
        )
    }

    @Test
    fun viewComposeShadowComplexLayoutUpdate() {
        measureMutation(
            engine = "viewcompose",
            scenario = "shadow_complex_layout",
            expectedText = "ViewCompose Shadow Complex Ready",
            revisionLabel = "Dashboard revision",
            mutateAction = "Update dashboard",
            resetAction = "Reset dashboard",
        )
    }

    @Test
    fun composeShadowComplexLayoutUpdate() {
        measureMutation(
            engine = "compose",
            scenario = "shadow_complex_layout",
            expectedText = "Compose Shadow Complex Ready",
            revisionLabel = "Dashboard revision",
            mutateAction = "Update dashboard",
            resetAction = "Reset dashboard",
        )
    }

    private fun measureScroll(
        engine: String,
        scenario: String,
        expectedText: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = shadowMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = shadowPerformanceIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceComparisonAndWait(
                engine = engine,
                scenario = scenario,
                expectedText = expectedText,
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
        scenario: String,
        expectedText: String,
        revisionLabel: String,
        mutateAction: String,
        resetAction: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = shadowMetrics(),
        compilationMode = CompilationMode.None(),
        iterations = shadowPerformanceIterations(),
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceComparisonAndWait(
                engine = engine,
                scenario = scenario,
                expectedText = expectedText,
                shadowRenderPolicy = shadowRenderPolicy(),
            )
            waitForText("$revisionLabel 0")
        },
    ) {
        clickText(mutateAction)
        waitForText("$revisionLabel 1")
        clickText(resetAction)
        waitForText("$revisionLabel 0")
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
            ?: RELEASE_BASELINE_ITERATIONS
    }

    private companion object {
        const val SHADOW_POLICY_ARGUMENT = "shadowRenderPolicy"
        const val ITERATIONS_ARGUMENT = "shadowPerformanceIterations"
        const val DEFAULT_SHADOW_POLICY = "auto"
        val AllowedShadowPolicies = setOf(
            "auto",
            "exact_bitmap",
            "render_node",
        )
    }
}
