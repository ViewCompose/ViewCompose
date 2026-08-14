package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Navigation demo 初始页签，便于回放系统导航、深链和多栈场景。
 * Selects the initial Navigation demo tab for replaying system navigation, deep link, and multi-stack cases.
 */
internal const val EXTRA_NAVIGATION_PAGE_INDEX = "navigation_page_index"

/**
 * Navigation chapter 的 Activity 入口，承载导航栏、导航图、保存状态和返回处理示例。
 * Activity entry for the Navigation chapter, hosting nav bar, graph, saved-state, and back-handling samples.
 */
class NavigationActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_navigation_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.NavigationPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_NAVIGATION_PAGE_INDEX, 0) ?: 0,
        )
    }
}
