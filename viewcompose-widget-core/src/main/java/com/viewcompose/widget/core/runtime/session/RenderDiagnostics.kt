package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.CompositionDiagnostics
import com.viewcompose.ui.node.NodeType

data class RenderStats(
    val inserts: Int = 0,
    val reuses: Int = 0,
    val removals: Int = 0,
    val reboundNodes: Int = 0,
    val patchedNodes: Int = 0,
    val skippedBindings: Int = 0,
    val skippedSubtrees: Int = 0,
    val bindingsByType: Map<NodeType, NodeTypeBindingStats> = emptyMap(),
)

data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

data class RenderStructureStats(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
)

data class RenderTreeResult(
    val stats: RenderStats = RenderStats(),
    val structure: RenderStructureStats = RenderStructureStats(),
    val warnings: List<String> = emptyList(),
    val tree: List<RenderTreeNode> = emptyList(),
    val patches: List<RenderPatchRecord> = emptyList(),
    val composition: CompositionDiagnostics = CompositionDiagnostics(),
)

data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val children: List<RenderTreeNode> = emptyList(),
)

data class RenderPatchRecord(
    val operation: RenderPatchOperation,
    val type: NodeType,
    val key: Any?,
    val parentKey: Any?,
    val index: Int,
    val moved: Boolean = false,
    val detail: String? = null,
)

enum class RenderPatchOperation {
    Insert,
    Remove,
    Rebind,
    Patch,
    SkipSelf,
    SkipSubtree,
}
