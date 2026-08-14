package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict Modifier fixture selected by immutable scenario identity. */
class ModifiersActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_modifiers_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "ModifiersActivity requires a registered modifiers scenario"
        }
        builder.ModifiersPage(ModifiersFixture.from(scenario.id), scenario)
    }
}
