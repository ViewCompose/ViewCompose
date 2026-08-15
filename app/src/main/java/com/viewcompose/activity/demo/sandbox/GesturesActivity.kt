package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict gesture fixture selected by immutable scenario identity. */
class GesturesActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_gestures_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "GesturesActivity requires a registered gesture scenario"
        }
        builder.GesturePage(
            fixture = GestureFixture.from(scenario.id),
            scenario = scenario,
        )
    }
}
