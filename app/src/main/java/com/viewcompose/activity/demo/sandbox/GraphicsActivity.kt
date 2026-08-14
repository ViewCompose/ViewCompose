package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Graphics sandbox 的 Activity 入口，用于验证 Canvas、绘制 modifier 和缓存绘制管线。
 * Activity entry for the Graphics sandbox, validating Canvas, draw modifiers, and cached drawing pipelines.
 */
class GraphicsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_graphics_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.GraphicsPage(
            initialPageIndex = intent.getIntExtra(
                EXTRA_GRAPHICS_PAGE_INDEX,
                GRAPHICS_PAGE_DRAWING,
            ),
        )
    }

    companion object {
        internal const val EXTRA_GRAPHICS_PAGE_INDEX: String = "graphics_page_index"
    }
}
