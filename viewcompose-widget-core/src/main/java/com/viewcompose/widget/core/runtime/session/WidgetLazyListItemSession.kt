package com.viewcompose.widget.core

import android.view.ViewGroup
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer

/**
 * 捕获懒加载 item 的内容 token 与 key，便于 item session 在复用时重新组合正确内容。
 * Captures a lazy item's content token and key so item sessions can recompose the correct content during reuse.
 */
internal data class CapturedLazyContentToken(
    val contentToken: Any?,
    val localSnapshot: LocalSnapshot,
)

/**
 * 从当前组合环境读取懒 item token，缺失时说明调用点不在懒内容捕获范围内。
 * Reads the lazy item token from the current composition; absence means the call site is outside lazy-content capture.
 */
internal fun capturedLazyContentToken(
    contentToken: Any?,
    localSnapshot: LocalSnapshot,
): CapturedLazyContentToken {
    return CapturedLazyContentToken(
        contentToken = contentToken,
        localSnapshot = localSnapshot,
    )
}

/**
 * 为单个懒列表 item 维护独立组合 session，使滚动复用不会污染父级组合状态。
 * Maintains an isolated composition session for one lazy-list item so scroll reuse does not pollute parent state.
 */
internal class WidgetLazyListItemSession(
    container: RenderContainerHandle,
    localSnapshot: LocalSnapshot,
    content: UiTreeBuilder.() -> Unit,
) : LazyListItemSession {
    private val hostContainer = container.nativeContainer as? ViewGroup
        ?: error("WidgetLazyListItemSession requires an Android ViewGroup container.")
    private var capturedLocals = localSnapshot
    private var renderContent = content
    private var diagnosticsListener = resolveDiagnosticsListener(localSnapshot)
    private val session = RenderSession(
        container = hostContainer,
        content = {
            LocalContext.withSnapshot(capturedLocals) {
                renderContent()
            }
        },
        onRenderResult = { result ->
            diagnosticsListener?.invoke(result)
        },
    )

    override fun render() {
        // Lazy item session bind path must keep immediate render semantics.
        session.render()
    }

    override fun dispose() {
        session.dispose()
    }

    fun updateContent(
        localSnapshot: LocalSnapshot,
        content: UiTreeBuilder.() -> Unit,
    ) {
        capturedLocals = localSnapshot
        renderContent = content
        // Keep the previously resolved listener if this snapshot does not carry it.
        // This avoids accidentally dropping diagnostics callbacks during partial recomposition paths.
        diagnosticsListener = resolveDiagnosticsListener(localSnapshot) ?: diagnosticsListener
    }

    private fun resolveDiagnosticsListener(
        snapshot: LocalSnapshot,
    ): ((RenderTreeResult) -> Unit)? {
        @Suppress("UNCHECKED_CAST")
        return snapshot.values[LocalRenderResultListener.holder] as? ((RenderTreeResult) -> Unit)
    }
}
