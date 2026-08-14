package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Collections demo 初始页签，支持列表/网格场景的定点回放。
 * Selects the initial Collections demo tab for targeted replay of list and grid scenarios.
 */
internal const val EXTRA_COLLECTIONS_PAGE_INDEX = "collections_page_index"

/**
 * Collections chapter 的 Activity 入口，承载懒列表、横向列表、网格和复用策略示例。
 * Activity entry for the Collections chapter, hosting lazy list, row, grid, and reuse-policy samples.
 */
class CollectionsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_collections_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.CollectionPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_COLLECTIONS_PAGE_INDEX, 0) ?: 0,
        )
    }
}
