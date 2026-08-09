package com.viewcompose

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * demo Activity 的 ViewCompose 渲染基类。
 * Base Activity for rendering demo pages with ViewCompose.
 *
 * 子类只需要声明标题和内容；基类统一处理 edge-to-edge、overlay host、诊断收集和启动重定向。
 * Subclasses provide only title and content; this base handles edge-to-edge, overlay host,
 * diagnostics collection, and launch redirects.
 */
abstract class DemoRenderActivity : AppCompatActivity() {
    /**
     * 可选的启动重定向目标，用于 MainActivity 按 extra 转发到具体页面。
     * Optional launch redirect target used by MainActivity to forward extras to concrete pages.
     */
    protected open fun redirectTargetIntent(): Intent? = null

    protected abstract val demoTitle: String

    /**
     * 构建当前 demo 页面的实际内容。
     * Builds the concrete content for the current demo page.
     */
    protected abstract fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (consumeRedirectIfNeeded()) {
            return
        }
        enableEdgeToEdge()
        setMaterial3UiContent(
            debug = true,
            debugTag = "ViewComposeSample",
            // DemoThemeTokens are intentionally device-independent. Keep the native View context
            // on the same stable application theme instead of adding a wallpaper-derived overlay.
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            overlayHostFactory = ::AndroidOverlayHost,
            onRenderResult = DemoRenderDiagnosticsStore::record,
        ) { root ->
            buildRootScaffold(root)
        }
    }

    /**
     * 构建根脚手架；普通子页使用统一子页壳，首页可以覆盖为自己的多页结构。
     * Builds the root scaffold; normal sub-pages use the shared shell, while home can provide its own pager.
     */
    protected open fun UiTreeBuilder.buildRootScaffold(root: ViewGroup) {
        DemoSubPageScaffold(
            root = root,
            title = demoTitle,
        ) { builder ->
            buildDemoContent(root, builder)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRedirectIfNeeded()
    }

    private fun consumeRedirectIfNeeded(): Boolean {
        redirectTargetIntent()?.let { targetIntent ->
            // 重定向后结束当前 Activity，避免 benchmark 或 deep link 留下空壳页面。
            // Finish after redirect so benchmarks or deep links do not leave an empty shell activity.
            startActivity(targetIntent)
            finish()
            return true
        }
        return false
    }
}
