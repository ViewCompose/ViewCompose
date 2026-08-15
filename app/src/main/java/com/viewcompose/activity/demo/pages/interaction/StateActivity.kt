package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict runtime fixture selected by immutable scenario identity. */
class StateActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_state_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "StateActivity requires a registered runtime scenario"
        }
        builder.StatePage(
            fixture = StateFixture.from(scenario.id),
            scenario = scenario,
            onOpenDiagnostics = {
                startActivity(
                    DiagnosticsActivity.newIntent(
                        context = this,
                        page = DiagnosticsActivity.PAGE_RENDERER,
                        autoRefreshRendererSnapshot = true,
                        entryHint = "来自 State -> Patch 压力测试，已自动刷新渲染器快照。",
                    ),
                )
            },
        )
    }
}
