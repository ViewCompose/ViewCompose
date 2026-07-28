package com.viewcompose.widget.core

import android.view.ViewGroup
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer

internal data class CapturedLazyContentToken(
    val contentToken: Any?,
    val localSnapshot: LocalSnapshot,
)

internal fun capturedLazyContentToken(
    contentToken: Any?,
    localSnapshot: LocalSnapshot,
): CapturedLazyContentToken {
    return CapturedLazyContentToken(
        contentToken = contentToken,
        localSnapshot = localSnapshot,
    )
}

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
