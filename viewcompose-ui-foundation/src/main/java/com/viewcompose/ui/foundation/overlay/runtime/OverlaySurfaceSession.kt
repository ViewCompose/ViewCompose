package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.VNode

/**
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
 * Independent RenderSession wrapper for an overlay surface.
 */
class OverlaySurfaceSession internal constructor(
    container: RenderContainerHandle,
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
     * Updates overlay content and renders synchronously to avoid stale locals or stale overlay host.
     */
    fun update(content: OverlaySurfaceContent) {
        currentContent = content
        overlayHostDelegate.delegate = content.overlayHost()
        renderImmediately()
    }

    /**
     * Disposes the overlay surface session.
     */
    fun dispose() {
        renderSession.dispose()
    }

    private fun renderImmediately() {
        // Overlay surface updates stay synchronous to avoid first-frame blank content.
        renderSession.render()
    }
}

/**
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
 * Creates an independent surface session for a platform overlay container.
 */
fun createOverlaySurfaceSession(
    container: RenderContainerHandle,
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
