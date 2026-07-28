package com.viewcompose

import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 指定 Foundations demo 初始页签，保持目录入口和测试入口的定位一致。
 * Selects the initial Foundations demo tab so catalog and test entry points land consistently.
 */
internal const val EXTRA_FOUNDATIONS_PAGE_INDEX = "foundations_page_index"

/**
 * Foundations chapter 的 Activity 入口，覆盖主题、token、图标和图片等基础能力示例。
 * Activity entry for the Foundations chapter, covering theme, token, icon, and image foundation samples.
 */
class FoundationsActivity : DemoRenderActivity() {
    override val demoTitle: String = "Foundations"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.OverviewPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_FOUNDATIONS_PAGE_INDEX, 0) ?: 0,
        ) { target ->
            startActivity(Intent(this, target))
        }
    }
}
