package com.viewcompose.overlay.android

import android.view.ViewGroup
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.RenderContainerHandle

/**
 * Adapts an overlay-owned Android [ViewGroup] for a nested ViewCompose render session.
 *
 * The returned handle retains [this] and exposes it only through the platform container contract.
 * Keep the handle and any render session created from it within the owning overlay window's
 * lifetime; dispose that session before the container is detached permanently.
 *
 * @sample com.viewcompose.overlay.android.samples.overlayRenderContainerSample
 * @receiver Android container owned by the current overlay surface
 * @return an opaque render-container handle backed by the receiver
 */
fun ViewGroup.asOverlayRenderContainerHandle(): RenderContainerHandle {
    return object : PlatformRenderContainerHandle {
        override val container: Any = this@asOverlayRenderContainerHandle
    }
}
