package com.viewcompose.performance

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.shadow.android.ShadowDecorationLayer
import com.viewcompose.shadow.android.ShadowRenderPolicy

/**
 * 承载 ViewCompose 与 Compose 对照性能页面的 Activity。
 * Activity that hosts paired ViewCompose and Compose performance screens.
 *
 * benchmark 只通过 Intent extras 选择引擎和场景，避免测试代码直接依赖具体 Activity 子类。
 * Benchmarks select engine and scenario only through Intent extras, avoiding direct dependencies
 * on separate Activity subclasses.
 */
class PerformanceComparisonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = PerformanceEngine.fromIntent(intent)
        val performanceScenario = PerformanceScenario.fromIntent(intent)
        val demoScenario = DemoScenarioRegistry.require(performanceScenario.demoScenarioId)
        check(DemoScenarioRegistry.fromIntent(intent) == demoScenario) {
            "${PerformanceComparisonActivity::class.simpleName} requires " +
                "${performanceScenario.demoScenarioId} for ${performanceScenario.wireValue}"
        }
        val shadowPolicy = ShadowRenderPolicy.fromWireValue(
            intent.getStringExtra(EXTRA_SHADOW_RENDER_POLICY),
        )
        ShadowDecorationLayer.setRenderPolicy(shadowPolicy)
        ShadowDecorationLayer.resetBackendDiagnostics()
        val fixtures = PerformanceFixtures(this)
        when (engine) {
            PerformanceEngine.ViewCompose -> {
                setMaterial3UiContent(debug = false) {
                    when (performanceScenario) {
                        PerformanceScenario.List -> {
                            ViewComposeListPerformanceScreen(
                                shadowsEnabled = false,
                                scenario = demoScenario,
                                fixtures = fixtures,
                            )
                        }
                        PerformanceScenario.ComplexLayout -> {
                            ViewComposeComplexLayoutPerformanceScreen(
                                shadowsEnabled = false,
                                scenario = demoScenario,
                                fixtures = fixtures,
                            )
                        }
                        PerformanceScenario.ShadowList -> {
                            ViewComposeListPerformanceScreen(
                                shadowsEnabled = true,
                                scenario = demoScenario,
                                fixtures = fixtures,
                            )
                        }
                        PerformanceScenario.ShadowComplexLayout -> {
                            ViewComposeComplexLayoutPerformanceScreen(
                                shadowsEnabled = true,
                                scenario = demoScenario,
                                fixtures = fixtures,
                            )
                        }
                    }
                }
            }

            PerformanceEngine.Compose -> {
                setContentView(
                    ComposeView(this).apply {
                        setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                        )
                        setContent {
                            when (performanceScenario) {
                                PerformanceScenario.List -> {
                                    ComposeListPerformanceScreen(
                                        shadowsEnabled = false,
                                        scenario = demoScenario,
                                        fixtures = fixtures,
                                    )
                                }
                                PerformanceScenario.ComplexLayout -> {
                                    ComposeComplexLayoutPerformanceScreen(
                                        shadowsEnabled = false,
                                        scenario = demoScenario,
                                        fixtures = fixtures,
                                    )
                                }
                                PerformanceScenario.ShadowList -> {
                                    ComposeListPerformanceScreen(
                                        shadowsEnabled = true,
                                        scenario = demoScenario,
                                        fixtures = fixtures,
                                    )
                                }
                                PerformanceScenario.ShadowComplexLayout -> {
                                    ComposeComplexLayoutPerformanceScreen(
                                        shadowsEnabled = true,
                                        scenario = demoScenario,
                                        fixtures = fixtures,
                                    )
                                }
                            }
                        }
                    },
                )
            }

            PerformanceEngine.AndroidViews -> {
                val content = when (performanceScenario) {
                    PerformanceScenario.List -> createAndroidViewsListPerformanceScreen(
                        context = this,
                        scenario = demoScenario,
                        fixtures = fixtures,
                    )
                    PerformanceScenario.ComplexLayout ->
                        createAndroidViewsComplexLayoutPerformanceScreen(
                            context = this,
                            scenario = demoScenario,
                            fixtures = fixtures,
                        )
                    PerformanceScenario.ShadowList,
                    PerformanceScenario.ShadowComplexLayout,
                    -> error(
                        "Android Views control does not support shadow scenario: " +
                            performanceScenario.wireValue,
                    )
                }
                setContentView(content)
            }
        }
    }

    override fun onDestroy() {
        ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
        super.onDestroy()
    }
}
