package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * 指定 Animation sandbox 初始页签，便于单独压测某类动画。
 * Selects the initial Animation sandbox tab for focused stress testing of one animation category.
 */
internal const val EXTRA_ANIMATION_PAGE_INDEX = "animation_page_index"

/**
 * 控制无限动画示例是否进入脉冲模式，避免 benchmark 启动路径依赖手动点击。
 * Controls whether infinite animation samples start in pulse mode so benchmarks do not depend on manual taps.
 */
internal const val EXTRA_ANIMATION_INFINITE_PULSE = "animation_infinite_pulse"

/**
 * Animation sandbox 的 Activity 入口，覆盖可见性、过渡、动画规格和无限动画示例。
 * Activity entry for the Animation sandbox, covering visibility, transitions, animation specs, and infinite samples.
 */
class AnimationActivity : DemoRenderActivity() {
    override val demoTitle: String = "Animation"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.AnimationPage(
            initialPageIndex = intent?.getIntExtra(EXTRA_ANIMATION_PAGE_INDEX, 0) ?: 0,
            initialInfinitePulse = intent?.getBooleanExtra(EXTRA_ANIMATION_INFINITE_PULSE, true) ?: true,
        )
    }
}
