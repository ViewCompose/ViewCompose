package com.viewcompose.host.android

import android.view.ViewGroup
import com.viewcompose.host.android.runtime.ensureAndroidRenderSessionPlatformInstalled
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.foundation.UiLocalSnapshot
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
 * When optional source tooling is installed in a debuggable application, eligible Host,
 * navigation-destination, and pager-page sessions may contribute bounded source candidates to an
 * app-private, request-driven device-locator report. The report uses the same process-local trace
 * identity and role as [diagnostics], follows [RenderSession.setRenderingActive] and
 * [RenderSession.dispose], and is not created by ordinary rendering.
 *
 * @sample com.viewcompose.host.android.samples.renderIntoSample
 * @param container Android parent that owns all Views mounted by the returned session
 * @param debug enables render logging and slow-operation warnings
 * @param debugTag log tag used by debug rendering
 * @param overlayHost overlay implementation available to emitted overlay nodes
 * @param role logical ownership role used by diagnostics and source tooling
 * @param diagnostics explicit diagnostics root, or `null` to inherit from [parentLocalSnapshot]
 * @param parentLocalSnapshot optional captured parent used once for correlation and inheritance
 * @param content retained declarative content evaluated for each render
 * @return the active session after its first synchronous frame
 */
fun renderInto(
    container: ViewGroup,
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    role: RenderSessionRole = RenderSessionRole.Host,
    diagnostics: RenderDiagnostics? = null,
    parentLocalSnapshot: UiLocalSnapshot? = null,
    content: UiTreeBuilder.() -> Unit,
): RenderSession {
    ensureAndroidRenderSessionPlatformInstalled()
    val session = com.viewcompose.ui.foundation.RenderSession(
        container = object : PlatformRenderContainerHandle {
            override val container: Any = container
        },
        content = content,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHost,
        role = role,
        diagnostics = diagnostics,
        parentLocalSnapshot = parentLocalSnapshot,
    )
    session.render()
    return RenderSession(delegate = session)
}
