package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.widget.core.UiTreeBuilder

internal const val EXTRA_ANIMATION_PAGE_INDEX = "animation_page_index"
internal const val EXTRA_ANIMATION_INFINITE_PULSE = "animation_infinite_pulse"

class AnimationActivity : DemoRenderActivity() {
    override val demoTitle: String = "Animation"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.AnimationPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_ANIMATION_PAGE_INDEX, 0) ?: 0,
            initialInfinitePulse = intent?.getBooleanExtra(EXTRA_ANIMATION_INFINITE_PULSE, true) ?: true,
        )
    }
}
