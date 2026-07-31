package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * 单次 render/reconcile 的操作统计。
 * Operation statistics for one render/reconcile pass.
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
) {
    /**
     * 记录一个新节点插入。
     * Records one newly inserted node.
     */
    fun withInsert(): RenderStats = copy(inserts = inserts + 1)

    /**
     * 记录一次节点复用及其绑定结果。
     * Records one node reuse and its binding result.
     */
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

    /**
     * 合并子树统计，用于递归 render 后向父层汇总。
     * Merges subtree statistics so recursive renders can aggregate into their parent.
     */
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

/**
 * 按 NodeType 聚合的绑定结果统计。
 * Binding result statistics grouped by NodeType.
 */
data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

/**
 * ViewTreeRenderer 对外返回的完整渲染结果。
 * Complete render result returned by ViewTreeRenderer.
 */
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

/**
 * 用于诊断展示的轻量 render tree 快照。
 * Lightweight render tree snapshot used for diagnostics display.
 */
data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val toolingMetadata: UiNodeToolingMetadata? = null,
    val children: List<RenderTreeNode> = emptyList(),
) {
    companion object {
        /**
         * 从 VNode 树构建诊断树，保留 type/key/children。
         * Builds a diagnostic tree from VNodes, preserving type/key/children.
         */
        fun from(nodes: List<com.viewcompose.ui.node.VNode>): List<RenderTreeNode> {
            return nodes.map { node ->
                RenderTreeNode(
                    type = node.type,
                    key = node.key,
                    toolingMetadata = UiNodeTooling.metadataOf(node),
                    children = from(node.children),
                )
            }
        }
    }
}

/**
 * 记录一次 patch 操作，供调试面板查看本轮 render 做了什么。
 * Records one patch operation for debug panels to inspect what the render pass did.
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

/**
 * 事务 commit 后执行的 AndroidView 生命周期回调。
 * AndroidView lifecycle callback executed after transaction commit.
 */
data class RenderTreeCommitEffect(
    val operation: AndroidViewOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

/**
 * commit/dispose 阶段失败信息，避免单个释放错误吞掉其他诊断。
 * Failure information from commit/dispose so one release error does not hide diagnostics.
 */
data class RenderTreeCommitFailure(
    val operation: AndroidViewOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)

/**
 * 已复用节点的绑定结果。
 * Binding result for a reused node.
 */
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
