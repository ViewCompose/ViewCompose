package com.viewcompose.host.android.runtime

import android.view.ViewGroup
import com.viewcompose.renderer.view.tree.MountedNode
import com.viewcompose.renderer.view.tree.ViewTreeRenderer
import com.viewcompose.ui.node.VNode
import com.viewcompose.widget.core.CoreRenderEngine
import com.viewcompose.widget.core.CoreRenderFrame
import com.viewcompose.widget.core.NodeTypeBindingStats
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderStructureStats
import com.viewcompose.widget.core.RenderTreeResult

class AndroidCoreRenderEngine : CoreRenderEngine {
    override fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame {
        val result = ViewTreeRenderer.renderInto(
            container = container,
            previous = previousMountedNodes.filterIsInstance<MountedNode>(),
            nodes = nodes,
            collectDiagnostics = collectDiagnostics,
        )
        return CoreRenderFrame(
            mountedNodes = result.mountedNodes,
            renderStats = result.stats.toCoreStats(),
            renderResult = if (collectDiagnostics) result.toCoreResult() else null,
        )
    }

    override fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<Any>,
    ) {
        ViewTreeRenderer.disposeMounted(
            container = container,
            mountedNodes = mountedNodes.filterIsInstance<MountedNode>(),
        )
    }

    private fun com.viewcompose.renderer.view.tree.RenderStats.toCoreStats(): RenderStats {
        return RenderStats(
            inserts = inserts,
            reuses = reuses,
            removals = removals,
            reboundNodes = reboundNodes,
            patchedNodes = patchedNodes,
            skippedBindings = skippedBindings,
            skippedSubtrees = skippedSubtrees,
            bindingsByType = bindingsByType.mapValues { (_, value) ->
                NodeTypeBindingStats(
                    rebound = value.rebound,
                    patched = value.patched,
                    skipped = value.skipped,
                )
            },
        )
    }

    private fun com.viewcompose.renderer.view.tree.RenderTreeResult.toCoreResult(): RenderTreeResult {
        return RenderTreeResult(
            stats = stats.toCoreStats(),
            structure = RenderStructureStats(
                vnodeCount = structure.vnodeCount,
                mountedNodeCount = structure.mountedNodeCount,
                maxVNodeDepth = structure.maxVNodeDepth,
                maxMountedDepth = structure.maxMountedDepth,
            ),
            warnings = warnings,
        )
    }
}
