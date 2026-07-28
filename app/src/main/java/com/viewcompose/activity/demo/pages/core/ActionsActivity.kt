package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 指定 Actions demo 初始页签，供首页跳转、benchmark 和深链复现同一路径。
 * Selects the initial Actions demo tab so home navigation, benchmarks, and deep links replay the same path.
 */
internal const val EXTRA_ACTIONS_PAGE_INDEX = "actions_page_index"

/**
 * Actions chapter 的 Activity 入口，只负责把 Intent extra 转交给声明式页面。
 * Activity entry for the Actions chapter; it only forwards Intent extras to the declarative page.
 */
class ActionsActivity : DemoRenderActivity() {
    override val demoTitle: String = "Actions"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.ActionsPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_ACTIONS_PAGE_INDEX, 0) ?: 0,
        )
    }
}
