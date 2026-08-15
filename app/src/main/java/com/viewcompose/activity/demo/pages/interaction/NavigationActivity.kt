package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict navigation-component fixture selected by immutable scenario identity. */
class NavigationActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_navigation_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "NavigationActivity requires a registered navigation-component scenario"
        }
        builder.NavigationPage(NavigationFixture.from(scenario.id), scenario)
    }
}
