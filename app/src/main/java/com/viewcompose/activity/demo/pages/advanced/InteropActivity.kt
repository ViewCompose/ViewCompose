package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Interop chapter 的 Activity 入口，用于验证 ViewCompose 与原生 Android View 的互操作边界。
 * Activity entry for the Interop chapter, validating boundaries between ViewCompose and native Android Views.
 */
class InteropActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_interop_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "InteropActivity requires the registered interop scenario"
        }
        builder.InteropPage(scenario)
    }
}
