package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewOperation

data class RenderStats(
    val inserts: Int = 0,
    val reuses: Int = 0,
    val removals: Int = 0,
    val reboundNodes: Int = 0,
    val patchedNodes: Int = 0,
    val skippedBindings: Int = 0,
    val skippedSubtrees: Int = 0,
    val bindingsByType: Map<NodeType, NodeTypeBindingStats> = emptyMap(),
) {
    fun withInsert(): RenderStats = copy(inserts = inserts + 1)

    fun withReuse(
        result: ReuseBindingResult,
        nodeType: NodeType,
    ): RenderStats {
        val existing = bindingsByType[nodeType] ?: NodeTypeBindingStats()
        val updated = when (result) {
            ReuseBindingResult.Rebound -> existing.copy(rebound = existing.rebound + 1)
            ReuseBindingResult.Patched -> existing.copy(patched = existing.patched + 1)
            ReuseBindingResult.Skipped,
            ReuseBindingResult.SkippedSubtree,
            -> existing.copy(skipped = existing.skipped + 1)
        }
        return copy(
            reuses = reuses + 1,
            reboundNodes = reboundNodes + if (result == ReuseBindingResult.Rebound) 1 else 0,
            patchedNodes = patchedNodes + if (result == ReuseBindingResult.Patched) 1 else 0,
            skippedBindings = skippedBindings + if (
                result == ReuseBindingResult.Skipped ||
                result == ReuseBindingResult.SkippedSubtree
            ) {
                1
            } else {
                0
            },
            skippedSubtrees = skippedSubtrees + if (result == ReuseBindingResult.SkippedSubtree) 1 else 0,
            bindingsByType = bindingsByType + (nodeType to updated),
        )
    }

    fun withRemoval(): RenderStats = copy(removals = removals + 1)

    fun mergeWith(other: RenderStats): RenderStats {
        return RenderStats(
            inserts = inserts + other.inserts,
            reuses = reuses + other.reuses,
            removals = removals + other.removals,
            reboundNodes = reboundNodes + other.reboundNodes,
            patchedNodes = patchedNodes + other.patchedNodes,
            skippedBindings = skippedBindings + other.skippedBindings,
            skippedSubtrees = skippedSubtrees + other.skippedSubtrees,
            bindingsByType = mergeBindingsByType(bindingsByType, other.bindingsByType),
        )
    }
}

data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

data class RenderTreeResult(
    val mountedNodes: List<MountedNode>,
    val reconcileResult: com.viewcompose.renderer.reconcile.ReconcileResult<MountedNode>,
    val stats: RenderStats,
    val structure: RenderStructureStats = RenderStructureStats(),
    val warnings: List<String> = emptyList(),
    val tree: List<RenderTreeNode> = emptyList(),
    val patches: List<RenderPatchRecord> = emptyList(),
    val commitEffects: List<RenderTreeCommitEffect> = emptyList(),
    val commitFailures: List<RenderTreeCommitFailure> = emptyList(),
)

data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val children: List<RenderTreeNode> = emptyList(),
) {
    companion object {
        fun from(nodes: List<com.viewcompose.ui.node.VNode>): List<RenderTreeNode> {
            return nodes.map { node ->
                RenderTreeNode(
                    type = node.type,
                    key = node.key,
                    children = from(node.children),
                )
            }
        }
    }
}

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

data class RenderTreeCommitEffect(
    val operation: AndroidViewOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

data class RenderTreeCommitFailure(
    val operation: AndroidViewOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)

enum class ReuseBindingResult {
    Rebound,
    Patched,
    Skipped,
    SkippedSubtree,
}

private fun mergeBindingsByType(
    a: Map<NodeType, NodeTypeBindingStats>,
    b: Map<NodeType, NodeTypeBindingStats>,
): Map<NodeType, NodeTypeBindingStats> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val result = a.toMutableMap()
    b.forEach { (type, stats) ->
        val existing = result[type]
        result[type] = if (existing == null) {
            stats
        } else {
            NodeTypeBindingStats(
                rebound = existing.rebound + stats.rebound,
                patched = existing.patched + stats.patched,
                skipped = existing.skipped + stats.skipped,
            )
        }
    }
    return result
}
