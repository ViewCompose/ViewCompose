package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one strict animation fixture selected by immutable scenario identity. */
class AnimationActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_animation_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "AnimationActivity requires a registered animation scenario"
        }
        builder.AnimationPage(
            fixture = AnimationFixture.from(scenario.id),
            scenario = scenario,
            boundsAnimated = intent.getBooleanExtra(EXTRA_ANIMATION_BOUNDS_ANIMATED, true),
        )
    }
}
