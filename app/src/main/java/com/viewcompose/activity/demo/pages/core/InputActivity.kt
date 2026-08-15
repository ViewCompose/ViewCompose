package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict input fixture selected by immutable scenario identity. */
class InputActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_input_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "InputActivity requires a registered input scenario"
        }
        builder.InputPage(
            fixture = InputFixture.from(scenario.id),
            scenario = scenario,
        )
    }
}
