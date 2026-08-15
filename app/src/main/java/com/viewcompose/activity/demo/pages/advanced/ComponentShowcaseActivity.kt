package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict component-showcase fixture selected by immutable scenario identity. */
class ComponentShowcaseActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_component_showcase_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "ComponentShowcaseActivity requires a registered component scenario"
        }
        builder.ComponentShowcasePage(ComponentShowcaseFixture.from(scenario.id), scenario)
    }
}
