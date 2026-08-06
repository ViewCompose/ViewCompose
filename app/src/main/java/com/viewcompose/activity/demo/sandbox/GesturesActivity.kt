package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Gestures sandbox 初始页签，便于回放点击、拖拽、滑动和变换手势。
 * Selects the initial Gestures sandbox tab for replaying tap, drag, swipe, and transform gestures.
 */
internal const val EXTRA_GESTURES_PAGE_INDEX = "gestures_page_index"

/**
 * Gestures sandbox 的 Activity 入口，隔离手势修饰符与状态模型的演示路径。
 * Activity entry for the Gestures sandbox, isolating demo paths for gesture modifiers and state models.
 */
class GesturesActivity : DemoRenderActivity() {
    override val demoTitle: String = "Gestures"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.GesturePage(
            initialPageIndex = intent?.getIntExtra(EXTRA_GESTURES_PAGE_INDEX, 0) ?: 0,
        )
    }
}
