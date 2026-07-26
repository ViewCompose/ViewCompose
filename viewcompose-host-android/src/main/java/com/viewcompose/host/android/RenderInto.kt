package com.viewcompose.host.android

import android.view.ViewGroup
import com.viewcompose.host.android.runtime.ensureAndroidRenderSessionPlatformInstalled
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderTreeResult
import com.viewcompose.widget.core.UiTreeBuilder

class RenderSession internal constructor(
    private val delegate: com.viewcompose.widget.core.RenderSession,
) {
    fun render() {
        delegate.render()
    }

    fun dispose() {
        delegate.dispose()
    }
}

fun renderInto(
    container: ViewGroup,
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
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
    )
    session.render()
    return RenderSession(session)
}
