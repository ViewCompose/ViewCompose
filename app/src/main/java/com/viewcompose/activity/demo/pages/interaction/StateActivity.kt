package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

internal const val EXTRA_STATE_PAGE_INDEX = "state_page_index"

class StateActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_state_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.StatePage(
            initialPageIndex = intent?.getIntExtra(EXTRA_STATE_PAGE_INDEX, 0) ?: 0,
            scenario = currentScenario(),
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
