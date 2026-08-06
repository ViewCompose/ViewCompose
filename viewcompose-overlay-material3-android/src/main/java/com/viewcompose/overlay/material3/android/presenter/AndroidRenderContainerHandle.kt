package com.viewcompose.overlay.material3.android.presenter

import android.view.ViewGroup
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.RenderContainerHandle

/** Wraps an overlay-owned Android container without leaking ViewGroup into UI Foundation. */
internal fun ViewGroup.asRenderContainerHandle(): RenderContainerHandle {
    return object : PlatformRenderContainerHandle {
        override val container: Any = this@asRenderContainerHandle
    }
}
