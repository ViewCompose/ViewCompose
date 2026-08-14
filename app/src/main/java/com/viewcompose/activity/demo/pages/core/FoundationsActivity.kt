package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict foundations fixture selected by immutable scenario identity. */
class FoundationsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_foundations_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "FoundationsActivity requires a registered foundations scenario"
        }
        builder.FoundationsPage(FoundationsFixture.from(scenario.id), scenario)
    }
}
