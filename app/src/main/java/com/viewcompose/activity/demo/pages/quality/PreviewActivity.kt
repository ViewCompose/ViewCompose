package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Preview demo 初始页签，帮助预览宿主和截图流程定位同一示例。
 * Selects the initial Preview demo tab so preview hosts and screenshot flows target the same sample.
 */
internal const val EXTRA_PREVIEW_PAGE_INDEX = "preview_page_index"

/**
 * Preview chapter 的 Activity 入口，验证预览主题、设备配置和 overlay mock 场景。
 * Activity entry for the Preview chapter, validating preview themes, device configuration, and overlay mock cases.
 */
class PreviewActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_preview_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.PreviewPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_PREVIEW_PAGE_INDEX, 0) ?: 0,
        )
    }
}
