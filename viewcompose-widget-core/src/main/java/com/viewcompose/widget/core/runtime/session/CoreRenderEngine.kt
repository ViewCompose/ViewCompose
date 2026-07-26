package com.viewcompose.widget.core

import android.view.ViewGroup
import com.viewcompose.ui.node.VNode

/**
 * Android rendering engine contract registered by host-android at runtime.
 * widget-core keeps this contract to avoid a direct renderer dependency.
 */
interface CoreRenderEngine {
    fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame

    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<Any>,
    )
}

data class CoreRenderFrame(
    val mountedNodes: List<Any>,
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
)
