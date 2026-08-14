package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict action-component fixture selected by immutable scenario identity. */
class ActionsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_actions_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "ActionsActivity requires a registered action-component scenario"
        }
        builder.ActionsPage(ActionsFixture.from(scenario.id), scenario)
    }
}
