package com.viewcompose.performance

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.viewcompose.host.android.setUiContent

class PerformanceComparisonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = PerformanceEngine.fromIntent(intent)
        val scenario = PerformanceScenario.fromIntent(intent)
        when (engine) {
            PerformanceEngine.ViewCompose -> {
                setUiContent(debug = false) {
                    when (scenario) {
                        PerformanceScenario.List -> ViewComposeListPerformanceScreen()
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
                                PerformanceScenario.List -> ComposeListPerformanceScreen()
                            }
                        }
                    },
                )
            }
        }
    }
}
