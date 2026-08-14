package com.viewcompose

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

/**
 * Diagnostics chapter 的 Activity 入口，集中展示运行时、主题和 renderer 信息。
 * Activity entry for the Diagnostics chapter, surfacing runtime, theme, and renderer information.
 */
class DiagnosticsActivity : DemoRenderActivity() {
    override val demoTitle: String = "Diagnostics"

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val initialPage = intent.getIntExtra(EXTRA_PAGE, PAGE_RUNTIME)
        val autoRefreshRendererSnapshot = intent.getBooleanExtra(
            EXTRA_AUTO_REFRESH_RENDERER_SNAPSHOT,
            false,
        )
        val entryHint = intent.getStringExtra(EXTRA_ENTRY_HINT)
        with(builder) {
            val selectedPageState = remember { mutableStateOf(initialPage) }
            DiagnosticsPage(
                root = root,
                selectedPageState = selectedPageState,
                autoRefreshOnEnter = autoRefreshRendererSnapshot,
                entryHint = entryHint,
            )
        }
    }

    companion object {
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_AUTO_REFRESH_RENDERER_SNAPSHOT = "auto_refresh_renderer_snapshot"
        private const val EXTRA_ENTRY_HINT = "entry_hint"

        const val PAGE_RUNTIME = 0
        const val PAGE_THEME = 1
        const val PAGE_RENDERER = 2
        /**
         * 构建诊断页专用 Intent，允许测试直接跳转到指定诊断页并触发 renderer 快照刷新。
         * Builds a Diagnostics Intent so tests can jump to a target page and optionally refresh renderer snapshots.
         */
        fun newIntent(
            context: Context,
            page: Int = PAGE_RUNTIME,
            autoRefreshRendererSnapshot: Boolean = false,
            entryHint: String? = null,
        ): Intent {
            return Intent(context, DiagnosticsActivity::class.java)
                .putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_AUTO_REFRESH_RENDERER_SNAPSHOT, autoRefreshRendererSnapshot)
                .apply {
                    if (entryHint != null) {
                        putExtra(EXTRA_ENTRY_HINT, entryHint)
                    }
                }
        }
    }
}
