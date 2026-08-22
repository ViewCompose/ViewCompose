package com.viewcompose.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated scale/workload benchmark for ViewCompose ConstraintLayout and direct AndroidX. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ConstraintLayoutPerformanceComparisonBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun viewComposeConstraintLayoutStable10() = measure("viewcompose", 10, "stable")

    @Test
    fun androidViewsConstraintLayoutStable10() = measure("android_views", 10, "stable")

    @Test
    fun viewComposeConstraintLayoutStable50() = measure("viewcompose", 50, "stable")

    @Test
    fun androidViewsConstraintLayoutStable50() = measure("android_views", 50, "stable")

    @Test
    fun viewComposeConstraintLayoutStable100() = measure("viewcompose", 100, "stable")

    @Test
    fun androidViewsConstraintLayoutStable100() = measure("android_views", 100, "stable")

    @Test
    fun viewComposeConstraintLayoutScalar10() = measure("viewcompose", 10, "scalar")

    @Test
    fun androidViewsConstraintLayoutScalar10() = measure("android_views", 10, "scalar")

    @Test
    fun viewComposeConstraintLayoutScalar50() = measure("viewcompose", 50, "scalar")

    @Test
    fun androidViewsConstraintLayoutScalar50() = measure("android_views", 50, "scalar")

    @Test
    fun viewComposeConstraintLayoutScalar100() = measure("viewcompose", 100, "scalar")

    @Test
    fun androidViewsConstraintLayoutScalar100() = measure("android_views", 100, "scalar")

    @Test
    fun viewComposeConstraintLayoutHelper10() = measure("viewcompose", 10, "helper")

    @Test
    fun androidViewsConstraintLayoutHelper10() = measure("android_views", 10, "helper")

    @Test
    fun viewComposeConstraintLayoutHelper50() = measure("viewcompose", 50, "helper")

    @Test
    fun androidViewsConstraintLayoutHelper50() = measure("android_views", 50, "helper")

    @Test
    fun viewComposeConstraintLayoutHelper100() = measure("viewcompose", 100, "helper")

    @Test
    fun androidViewsConstraintLayoutHelper100() = measure("android_views", 100, "helper")

    @Test
    fun viewComposeConstraintLayoutTopology10() = measure("viewcompose", 10, "topology")

    @Test
    fun androidViewsConstraintLayoutTopology10() = measure("android_views", 10, "topology")

    @Test
    fun viewComposeConstraintLayoutTopology50() = measure("viewcompose", 50, "topology")

    @Test
    fun androidViewsConstraintLayoutTopology50() = measure("android_views", 50, "topology")

    @Test
    fun viewComposeConstraintLayoutTopology100() = measure("viewcompose", 100, "topology")

    @Test
    fun androidViewsConstraintLayoutTopology100() = measure("android_views", 100, "topology")

    private fun measure(
        engine: String,
        nodeCount: Int,
        workload: String,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
        ),
        compilationMode = CompilationMode.None(),
        iterations = FORMAL_INTERACTION_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startPerformanceScenarioAndWait(
                scenarioId = PerformanceComplexLayoutScenario,
                engine = engine,
                constraintLayoutNodeCount = nodeCount,
                constraintLayoutWorkload = workload,
            )
        },
    ) {
        repeat(ConstraintLayoutMutationCycles) {
            val initial = scenarioTargetText(
                PerformanceComplexLayoutScenario,
                DemoTargetRole.State,
            )
            performScenarioTargetClick(
                PerformanceComplexLayoutScenario,
                DemoTargetRole.PrimaryAction,
            )
            val updated = waitForScenarioTargetTextChange(
                PerformanceComplexLayoutScenario,
                DemoTargetRole.State,
                initial,
            )
            performScenarioTargetClick(
                PerformanceComplexLayoutScenario,
                DemoTargetRole.Reset,
            )
            val reset = waitForScenarioTargetTextChange(
                PerformanceComplexLayoutScenario,
                DemoTargetRole.State,
                updated,
            )
            assertEquals(initial, reset)
        }
    }
}

private const val PerformanceComplexLayoutScenario = "performance.complex-layout"
private const val ConstraintLayoutMutationCycles = 16
