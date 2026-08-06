package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Input demo 初始页签，便于直接验证文本输入、焦点和控件状态场景。
 * Selects the initial Input demo tab for direct verification of text input, focus, and control state cases.
 */
internal const val EXTRA_INPUT_PAGE_INDEX = "input_page_index"

/**
 * Input chapter 的 Activity 入口，承载文本编辑、选择控件和焦点跟随滚动示例。
 * Activity entry for the Input chapter, hosting text editing, selection controls, and focus-follow scrolling samples.
 */
class InputActivity : DemoRenderActivity() {
    override val demoTitle: String = "Input"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.InputPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_INPUT_PAGE_INDEX, 0) ?: 0,
        )
    }
}
