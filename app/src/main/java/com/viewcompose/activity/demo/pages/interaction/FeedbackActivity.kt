package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Feedback chapter 的 Activity 入口，验证临时反馈与表面 overlay 的声明式 API。
 * Activity entry for the Feedback chapter, validating declarative APIs for transient feedback and surface overlays.
 */
class FeedbackActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_feedback_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "FeedbackActivity requires a registered overlay scenario"
        }
        builder.FeedbackPage(FeedbackFixture.from(scenario.id), scenario)
    }
}
