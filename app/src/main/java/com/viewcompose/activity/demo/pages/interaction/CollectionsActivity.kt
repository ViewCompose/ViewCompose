package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Hosts one strict collection fixture selected by immutable scenario identity.
 */
class CollectionsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_collections_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "CollectionsActivity requires a registered collection scenario"
        }
        builder.CollectionPage(
            fixture = CollectionFixture.from(scenario.id),
            scenario = scenario,
        )
    }
}
