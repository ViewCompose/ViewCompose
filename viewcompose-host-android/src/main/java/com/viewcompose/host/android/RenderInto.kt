package com.viewcompose.host.android

import android.view.ViewGroup
import com.viewcompose.host.android.runtime.ensureAndroidRenderSessionPlatformInstalled
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderTreeResult
import com.viewcompose.widget.core.RenderFailure
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Android 层公开的渲染会话包装器。
 * Public Android-side wrapper around the core render session.
 *
 * 它只暴露宿主需要的生命周期和诊断入口，隐藏 widget-core 的内部实现类型。
 * It exposes only the lifecycle and diagnostics surface needed by hosts while hiding widget-core internals.
 */
class RenderSession internal constructor(
    private val delegate: com.viewcompose.widget.core.RenderSession,
) {
    /**
     * 最近一次渲染失败，成功帧不会清除历史失败对象。
     * Last render failure; successful frames do not clear the historical failure object.
     */
    val lastRenderFailure: RenderFailure?
        get() = delegate.lastRenderFailure

    /**
     * 最近一次帧报告，包含提交状态、统计和失败信息。
     * Last frame report containing commit status, stats, and failure details.
     */
    val lastFrameReport: RenderFrameReport?
        get() = delegate.lastFrameReport

    /**
     * 立即同步渲染当前 content。
     * Immediately renders the current content synchronously.
     */
    fun render() {
        delegate.render()
    }

    /**
     * 控制保留会话是否响应异步渲染请求。
     * Controls whether a retained session responds to asynchronous render requests.
     */
    fun setRenderingActive(active: Boolean) {
        delegate.setRenderingActive(active)
    }

    /**
     * 释放渲染会话及其已挂载的 Android View 树。
     * Disposes the render session and its mounted Android View tree.
     */
    fun dispose() {
        delegate.dispose()
    }
}

/**
 * 将 UIFramework DSL 内容渲染到指定 Android ViewGroup。
 * Renders UIFramework DSL content into the given Android ViewGroup.
 *
 * 该入口会按需安装 Android 渲染平台，并立即提交第一帧。
 * This entry installs the Android render platform on demand and commits the first frame immediately.
 */
fun renderInto(
    container: ViewGroup,
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    onRenderFailure: ((RenderFailure) -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
): RenderSession {
    ensureAndroidRenderSessionPlatformInstalled()
    val session = com.viewcompose.widget.core.RenderSession(
        container = container,
        content = content,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHost,
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
        onRenderFailure = onRenderFailure,
    )
    session.render()
    return RenderSession(session)
}
