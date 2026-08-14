package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Hosts one strict layout fixture selected by immutable scenario identity.
 */
class LayoutsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_layouts_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "LayoutsActivity requires a registered layout scenario"
        }
        builder.LayoutPage(
            fixture = LayoutFixture.from(scenario.id),
            scenario = scenario,
        )
    }
}
