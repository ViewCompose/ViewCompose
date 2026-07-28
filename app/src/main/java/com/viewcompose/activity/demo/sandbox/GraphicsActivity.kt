package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Graphics sandbox 的 Activity 入口，用于验证 Canvas、绘制 modifier 和缓存绘制管线。
 * Activity entry for the Graphics sandbox, validating Canvas, draw modifiers, and cached drawing pipelines.
 */
class GraphicsActivity : DemoRenderActivity() {
    override val demoTitle: String = "Graphics"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.GraphicsPage()
    }
}
