package com.viewcompose.host.android

import android.view.ViewGroup
import com.viewcompose.host.android.runtime.ensureAndroidRenderSessionPlatformInstalled
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.RenderTreeResult
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Owns a retained ViewCompose render session and its mounted Android View tree.
 *
 * The session is main-thread confined. Call [dispose] when a custom host ends; Activity and Fragment
 * `setUiContent` integrations do this automatically. Operations after disposal follow the core
 * session's fail-fast lifecycle contract.
 */
class RenderSession internal constructor(
    private val delegate: com.viewcompose.ui.foundation.RenderSession,
) {
    /** Last render failure; later successful frames do not erase this historical failure. */
    val lastRenderFailure: RenderFailure?
        get() = delegate.lastRenderFailure

    /** Last attempted frame report, including commit status, statistics, and failures. */
    val lastFrameReport: RenderFrameReport?
        get() = delegate.lastFrameReport

    /**
     * Immediately evaluates and synchronously renders the current content.
     *
     * @throws IllegalStateException after [dispose] has been requested
     */
    fun render() {
        delegate.render()
    }

    /**
     * Enables or suspends frame-scheduled invalidation rendering.
     *
     * Explicit [render] calls still run while inactive. Invalidations received while inactive are
     * retained and coalesced into a frame after rendering becomes active again.
     *
     * @throws IllegalStateException after [dispose] has been requested
     */
    fun setRenderingActive(active: Boolean) {
        delegate.setRenderingActive(active)
    }

    /** Permanently disposes the session and releases its mounted Android View tree. */
    fun dispose() {
        delegate.dispose()
    }
}

/**
 * Creates a retained session that renders [content] into [container].
 *
 * The Android render platform is installed on demand and the first frame is rendered synchronously
 * before this function returns. This low-level entry does not provide lifecycle, ViewModel, saved
 * state, environment, theme, or frame-clock locals; custom hosts must provide and dispose those
 * services themselves, or use an Activity/Fragment `setUiContent` integration.
 *
 * In a debuggable application process, the first emitted node contributes one bounded source call
 * chain to an app-private device-locator report. The report contains source identifiers rather than
 * source text, follows [RenderSession.setRenderingActive] and [RenderSession.dispose], and is not
 * created for non-debuggable applications. Initial source capture allocates one stack trace per
 * session; normal frame rendering remains unchanged.
 *
 * @sample com.viewcompose.host.android.samples.renderIntoSample
 * @param container Android parent that owns all Views mounted by the returned session
 * @param debug enables render logging and full render-result collection
 * @param debugTag log tag used by debug rendering
 * @param overlayHost overlay implementation available to emitted overlay nodes
 * @param onRenderStats optional callback after every attempted frame
 * @param onRenderResult optional callback for collected diagnostics
 * @param onRenderFailure optional callback when a frame fails
 * @param content retained declarative content evaluated for each render
 * @return the active session after its first synchronous frame
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
    val session = com.viewcompose.ui.foundation.RenderSession(
        container = object : PlatformRenderContainerHandle, UiSourceSessionContainerHandle {
            override val container: Any = container
            override val sourceSessionRole: UiSourceSessionRole = UiSourceSessionRole.Host
        },
        content = content,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHost,
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
        onRenderFailure = onRenderFailure,
    )
    session.render()
    return RenderSession(delegate = session)
}
