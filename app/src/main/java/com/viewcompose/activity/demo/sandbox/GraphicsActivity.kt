package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict graphics fixture selected by immutable scenario identity. */
class GraphicsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_graphics_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "GraphicsActivity requires a registered graphics scenario"
        }
        builder.GraphicsPage(
            fixture = GraphicsFixture.from(scenario.id),
            scenario = scenario,
        )
    }
}
