package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

class WidgetShowcaseActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_widget_showcase_title

    override fun buildDemoContent(root: ViewGroup, builder: UiTreeBuilder) {
        builder.WidgetShowcasePage()
    }
}
