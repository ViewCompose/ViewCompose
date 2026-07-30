package com.viewcompose.performance

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.viewcompose.host.android.setUiContent
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
        val scenario = PerformanceScenario.fromIntent(intent)
        val shadowPolicy = ShadowRenderPolicy.fromWireValue(
            intent.getStringExtra(EXTRA_SHADOW_RENDER_POLICY),
        )
        ShadowDecorationLayer.setRenderPolicy(shadowPolicy)
        ShadowDecorationLayer.resetBackendDiagnostics()
        when (engine) {
            PerformanceEngine.ViewCompose -> {
                setUiContent(debug = false) {
                    when (scenario) {
                        PerformanceScenario.List -> {
                            ViewComposeListPerformanceScreen(shadowsEnabled = false)
                        }
                        PerformanceScenario.ComplexLayout -> {
                            ViewComposeComplexLayoutPerformanceScreen(
                                shadowsEnabled = false,
                            )
                        }
                        PerformanceScenario.ShadowList -> {
                            ViewComposeListPerformanceScreen(shadowsEnabled = true)
                        }
                        PerformanceScenario.ShadowComplexLayout -> {
                            ViewComposeComplexLayoutPerformanceScreen(
                                shadowsEnabled = true,
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
                            when (scenario) {
                                PerformanceScenario.List -> {
                                    ComposeListPerformanceScreen(shadowsEnabled = false)
                                }
                                PerformanceScenario.ComplexLayout -> {
                                    ComposeComplexLayoutPerformanceScreen(
                                        shadowsEnabled = false,
                                    )
                                }
                                PerformanceScenario.ShadowList -> {
                                    ComposeListPerformanceScreen(shadowsEnabled = true)
                                }
                                PerformanceScenario.ShadowComplexLayout -> {
                                    ComposeComplexLayoutPerformanceScreen(
                                        shadowsEnabled = true,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
        super.onDestroy()
    }
}
