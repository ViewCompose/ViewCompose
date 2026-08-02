package com.viewcompose.widget.core

import android.view.ViewGroup
import com.viewcompose.ui.node.VNode

/**
 * Narrow Android render-engine contract installed by a host module.
 *
 * Widget core depends on this protocol instead of a concrete renderer, preserving the module
 * boundary and allowing a host to choose its renderer/runtime stack once per process.
 */
interface CoreRenderEngine {
    /**
     * Renders the VNode tree into the container and returns new mounted nodes plus optional diagnostics/commit work.
     */
    fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame

    /**
     * Disposes renderer nodes that are already mounted.
     */
    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<Any>,
    ): List<CoreRenderCommitFailure>
}

/**
 * Output prepared by a renderer for one frame.
 *
 * [commitEffects] run only after composition commit. [commitFailures] contains failures already
 * encountered while establishing [mountedNodes]; that native tree cannot be rolled back by core.
 *
 * @property mountedNodes opaque renderer nodes that become the previous tree for the next frame
 * @property renderStats aggregate binding statistics
 * @property renderResult optional detailed diagnostics when collection was requested
 * @property commitEffects native mutations deferred until composition commit
 * @property commitFailures native failures captured while rendering the tree
 */
data class CoreRenderFrame(
    val mountedNodes: List<Any>,
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
    val commitEffects: List<CoreRenderCommitEffect> = emptyList(),
    val commitFailures: List<CoreRenderCommitFailure> = emptyList(),
)

/**
 * Native mutation deferred by the renderer until the session commit phase.
 *
 * @property operation native interoperability operation represented by this effect
 * @property nodeKey declarative node identity used for diagnostics, if available
 * @property commit operation invoked once after composition commit
 */
data class CoreRenderCommitEffect(
    val operation: RenderFailureOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

/**
 * Native failure captured by the renderer while rendering or disposing mounted nodes.
 *
 * @property operation related native interoperability operation, if known
 * @property nodeKey declarative node identity used for diagnostics, if available
 * @property cause original platform failure
 */
data class CoreRenderCommitFailure(
    val operation: RenderFailureOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)
