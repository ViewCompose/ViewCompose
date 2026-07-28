package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 指定 Layouts demo 初始页签，复用在手工验收和自动化启动路径中。
 * Selects the initial Layouts demo tab, shared by manual acceptance and automated launch paths.
 */
internal const val EXTRA_LAYOUTS_PAGE_INDEX = "layouts_page_index"

/**
 * Layouts chapter 的 Activity 入口，展示线性、滚动、流式和约束布局能力。
 * Activity entry for the Layouts chapter, showcasing linear, scrollable, flow, and constraint layout capabilities.
 */
class LayoutsActivity : DemoRenderActivity() {
    override val demoTitle: String = "Layouts"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.LayoutPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_LAYOUTS_PAGE_INDEX, 0) ?: 0,
        )
    }
}
