package com.viewcompose.overlay.android.samples

import android.view.View
import android.view.ViewGroup
import com.viewcompose.host.android.overlay.AndroidOverlayHostDefaults
import com.viewcompose.overlay.android.AndroidOverlayHost
import com.viewcompose.overlay.android.asOverlayRenderContainerHandle
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.node.RenderContainerHandle

/** Creates one neutral Android overlay transport for a custom render root. */
fun androidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHost(rootView)
}

/** Discovers the single neutral Android overlay transport for a low-level custom host. */
fun discoveredAndroidOverlayHostSample(rootView: View): OverlayHost {
    return AndroidOverlayHostDefaults.androidOrNoOp(rootView)
}

/** Adapts an overlay-owned Android container for a nested render session. */
fun overlayRenderContainerSample(container: ViewGroup): RenderContainerHandle {
    return container.asOverlayRenderContainerHandle()
}
