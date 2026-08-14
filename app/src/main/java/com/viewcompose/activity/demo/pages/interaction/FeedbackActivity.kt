package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Feedback demo 初始页签，便于直接进入 snackbar、dialog、popup 等覆盖层场景。
 * Selects the initial Feedback demo tab for direct entry into snackbar, dialog, popup, and overlay cases.
 */
internal const val EXTRA_FEEDBACK_PAGE_INDEX = "feedback_page_index"

/**
 * Feedback chapter 的 Activity 入口，验证临时反馈与表面 overlay 的声明式 API。
 * Activity entry for the Feedback chapter, validating declarative APIs for transient feedback and surface overlays.
 */
class FeedbackActivity : DemoRenderActivity() {
    override val demoTitle: String = "Feedback"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.FeedbackPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_FEEDBACK_PAGE_INDEX, 0) ?: 0,
            scenario = currentScenario(),
        )
    }
}
