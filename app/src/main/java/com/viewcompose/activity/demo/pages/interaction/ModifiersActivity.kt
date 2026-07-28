package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 指定 Modifiers demo 初始页签，确保 modifier 回归场景可被稳定启动。
 * Selects the initial Modifiers demo tab so modifier regression scenarios can be launched reliably.
 */
internal const val EXTRA_MODIFIERS_PAGE_INDEX = "modifiers_page_index"

/**
 * Modifiers chapter 的 Activity 入口，展示背景、边框、绘制和交互 modifier 的组合效果。
 * Activity entry for the Modifiers chapter, showcasing combined background, border, drawing, and interaction modifiers.
 */
class ModifiersActivity : DemoRenderActivity() {
    override val demoTitle: String = "Modifiers"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.ModifiersPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_MODIFIERS_PAGE_INDEX, 0) ?: 0,
        )
    }
}
