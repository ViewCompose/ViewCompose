package com.viewcompose.widget.core

import android.view.ViewGroup
import com.viewcompose.ui.node.VNode

/**
 * 可在独立 overlay surface 中渲染的内容快照。
 * Content snapshot that can render inside an independent overlay surface.
 */
class OverlaySurfaceContent internal constructor(
    private val localSnapshot: LocalSnapshot,
    private val overlayHost: OverlayHost,
    private val content: UiTreeBuilder.() -> Unit,
) {
    internal fun renderInto(builder: UiTreeBuilder) {
        LocalContext.withSnapshot(localSnapshot) {
            with(builder) {
                content()
            }
        }
    }

    internal fun buildNodes(): List<VNode> {
        var nodes: List<VNode> = emptyList()
        LocalContext.withSnapshot(localSnapshot) {
            nodes = buildVNodeTree(content)
        }
        return nodes
    }

    internal fun overlayHost(): OverlayHost = overlayHost
}

/**
 * overlay surface 的独立 RenderSession 包装。
 * Independent RenderSession wrapper for an overlay surface.
 */
class OverlaySurfaceSession internal constructor(
    container: ViewGroup,
    initialContent: OverlaySurfaceContent,
) {
    private var currentContent = initialContent
    private val overlayHostDelegate = MutableOverlayHost(initialContent.overlayHost())
    private val renderSession = RenderSession(
        container = container,
        content = {
            currentContent.renderInto(this)
        },
        overlayHost = overlayHostDelegate,
    )

    init {
        renderImmediately()
    }

    /**
     * 更新 overlay 内容并同步渲染，避免显示旧 locals 或旧 overlay host。
     * Updates overlay content and renders synchronously to avoid stale locals or stale overlay host.
     */
    fun update(content: OverlaySurfaceContent) {
        currentContent = content
        overlayHostDelegate.delegate = content.overlayHost()
        renderImmediately()
    }

    /**
     * 释放 overlay surface session。
     * Disposes the overlay surface session.
     */
    fun dispose() {
        renderSession.dispose()
    }

    private fun renderImmediately() {
        // overlay surface 更新保持同步，避免首帧空白内容。
        // Overlay surface updates stay synchronous to avoid first-frame blank content.
        renderSession.render()
    }
}

/**
 * 捕获当前 locals 和 overlay host，生成可延迟渲染的 overlay content。
 * Captures current locals and overlay host to create overlay content that can render later.
 */
internal fun captureOverlaySurfaceContent(
    content: UiTreeBuilder.() -> Unit,
): OverlaySurfaceContent {
    return OverlaySurfaceContent(
        localSnapshot = LocalContext.snapshot(),
        overlayHost = OverlayHostContext.current,
        content = content,
    )
}

/**
 * 为平台 overlay 容器创建独立 surface session。
 * Creates an independent surface session for a platform overlay container.
 */
fun createOverlaySurfaceSession(
    container: ViewGroup,
    content: OverlaySurfaceContent,
): OverlaySurfaceSession {
    return OverlaySurfaceSession(
        container = container,
        initialContent = content,
    )
}

private class MutableOverlayHost(
    var delegate: OverlayHost,
) : OverlayHost {
    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        delegate.commit(sessionId, requests)
    }

    override fun clear(sessionId: OverlaySessionId) {
        delegate.clear(sessionId)
    }
}
