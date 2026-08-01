package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.CompositionDiagnostics
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * renderer 一帧绑定行为的汇总统计。
 * Summary statistics for renderer binding behavior in one frame.
 */
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

/**
 * 某一节点类型的绑定统计。
 * Binding statistics for one node type.
 */
data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

/**
 * VNode 树与 mounted tree 的结构规模统计。
 * Structure-size statistics for the VNode tree and mounted tree.
 */
data class RenderStructureStats(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
)

/**
 * 一帧完整渲染诊断结果。
 * Complete render diagnostics for one frame.
 */
data class RenderTreeResult(
    val stats: RenderStats = RenderStats(),
    val structure: RenderStructureStats = RenderStructureStats(),
    val warnings: List<String> = emptyList(),
    val tree: List<RenderTreeNode> = emptyList(),
    val patches: List<RenderPatchRecord> = emptyList(),
    val composition: CompositionDiagnostics = CompositionDiagnostics(),
)

/**
 * 诊断用渲染树节点，不暴露平台 View 实例。
 * Diagnostic render-tree node that does not expose platform View instances.
 */
data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val toolingMetadata: UiNodeToolingMetadata? = null,
    val children: List<RenderTreeNode> = emptyList(),
)

/**
 * renderer 对 mounted tree 执行的一条 patch 记录。
 * One patch record applied by the renderer to the mounted tree.
 */
data class RenderPatchRecord(
    val operation: RenderPatchOperation,
    val type: NodeType,
    val key: Any?,
    val parentKey: Any?,
    val index: Int,
    val moved: Boolean = false,
    val detail: String? = null,
    val toolingMetadata: UiNodeToolingMetadata? = null,
)

/**
 * renderer patch 操作类型。
 * Renderer patch operation type.
 */
enum class RenderPatchOperation {
    Insert,
    Remove,
    Rebind,
    Patch,
    SkipSelf,
    SkipSubtree,
}
