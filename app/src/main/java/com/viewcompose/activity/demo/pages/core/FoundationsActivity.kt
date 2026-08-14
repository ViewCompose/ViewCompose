package com.viewcompose

import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.ui.foundation.UiTreeBuilder

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
    override val demoTitleRes: Int = R.string.demo_activity_foundations_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.OverviewPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_FOUNDATIONS_PAGE_INDEX, 0) ?: 0,
        ) { target ->
            if (target == CollectionsActivity::class.java) {
                val scenario = DemoScenarioRegistry.require(DemoScenarioIds.CollectionControls.value)
                startActivity(DemoScenarioRegistry.createLaunchIntent(this, scenario))
            } else {
                startActivity(Intent(this, target))
            }
        }
    }
}
