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
    ): List<CoreRenderCommitFailure>
}

data class CoreRenderFrame(
    val mountedNodes: List<Any>,
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
    val commitEffects: List<CoreRenderCommitEffect> = emptyList(),
    val commitFailures: List<CoreRenderCommitFailure> = emptyList(),
)

data class CoreRenderCommitEffect(
    val operation: RenderFailureOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

data class CoreRenderCommitFailure(
    val operation: RenderFailureOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)
