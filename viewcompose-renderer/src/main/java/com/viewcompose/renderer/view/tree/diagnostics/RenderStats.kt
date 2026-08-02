package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * Immutable reconciliation and binding counters for one render pass or merged subtree.
 *
 * @property inserts newly mounted nodes
 * @property reuses existing mounted nodes retained by identity
 * @property removals previously mounted nodes removed from the tree
 * @property reboundNodes reused nodes whose complete binding ran again
 * @property patchedNodes reused nodes updated through a targeted patch
 * @property skippedBindings reused nodes whose own binding was unchanged
 * @property skippedSubtrees reused roots whose complete subtree was omitted
 * @property bindingsByType binding outcomes grouped by declarative node type
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
    /** Returns a snapshot with one additional inserted node. */
    fun withInsert(): RenderStats = copy(inserts = inserts + 1)

    /**
     * Returns a snapshot with one additional reuse and its binding outcome.
     *
     * @param result binding work performed for the reused node
     * @param nodeType declarative type used for per-type aggregation
     * @return updated immutable counters
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

    /** Returns a snapshot with one additional removed node. */
    fun withRemoval(): RenderStats = copy(removals = removals + 1)

    /**
     * Adds [other] counters to this snapshot for recursive subtree aggregation.
     *
     * @param other independent subtree or sibling statistics
     * @return immutable sum with per-type maps merged by [NodeType]
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
 * Binding outcome counters for one declarative node type.
 *
 * @property rebound complete binding executions
 * @property patched targeted patch executions
 * @property skipped unchanged bindings, including complete subtree skips
 */
data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

/**
 * Complete committed result returned by [ViewTreeRenderer.renderInto].
 *
 * Collection properties are immutable snapshots. [mountedNodes] is the ownership token that must
 * be supplied to the next render or disposal call. Commit failures describe isolated post-commit
 * lifecycle errors; they do not imply that the visible tree was rolled back.
 *
 * @property mountedNodes committed renderer roots to retain for the next frame
 * @property reconcileResult parent-level insert, reuse, and removal plan that produced the roots
 * @property stats aggregate binding work, or zeroes when diagnostics were disabled
 * @property structure declarative and mounted tree size snapshot
 * @property warnings non-fatal structural and performance diagnostics
 * @property tree platform-independent diagnostic tree, empty when diagnostics were disabled
 * @property patches ordered reconciliation records, empty when diagnostics were disabled
 * @property commitEffects Android View lifecycle callbacks scheduled and attempted after commit
 * @property commitFailures isolated errors thrown by commit effects or deferred disposal
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
 * Platform-independent node in a diagnostic render-tree snapshot.
 *
 * @property type declarative node type
 * @property key stable declarative identity, if supplied
 * @property toolingMetadata source mapping captured while the VNode was built
 * @property children diagnostic children in render order
 */
data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val toolingMetadata: UiNodeToolingMetadata? = null,
    val children: List<RenderTreeNode> = emptyList(),
) {
    /** Creates diagnostic nodes from committed declarative snapshots. */
    companion object {
        /**
         * Builds a detached diagnostic tree from VNodes.
         *
         * @param nodes immutable declarative roots to snapshot
         * @return diagnostic roots in the same order
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
 * One ordered renderer operation for diagnostics and preview tooling.
 *
 * @property operation performed reconciliation or binding action
 * @property type affected declarative node type
 * @property key affected node identity, if supplied
 * @property parentKey committed parent identity, if supplied
 * @property index zero-based child index after the operation
 * @property moved whether a reused node changed sibling position
 * @property detail optional renderer-specific diagnostic description
 * @property toolingMetadata source mapping for the affected node
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

/** Operations emitted by the renderer's reconciliation and binding pipeline. */
enum class RenderPatchOperation {
    /** A new mounted node was inserted. */
    Insert,
    /** A previously mounted node was removed. */
    Remove,
    /** All bindings for a reused node ran again. */
    Rebind,
    /** A targeted subset of bindings changed. */
    Patch,
    /** The node binding was unchanged while descendants were still considered. */
    SkipSelf,
    /** The node and its complete subtree were unchanged. */
    SkipSubtree,
}

/**
 * Deferred Android View lifecycle callback executed after structural commit.
 *
 * @property operation lifecycle operation represented by the callback
 * @property nodeKey declarative identity used for diagnostics, if supplied
 * @property commit single-use UI-thread callback owned and invoked by the render transaction
 */
data class RenderTreeCommitEffect(
    val operation: AndroidViewOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

/**
 * Isolated lifecycle failure captured during commit or disposal.
 *
 * @property operation lifecycle operation that failed, or `null` when it could not be identified
 * @property nodeKey declarative identity associated with the failure, if supplied
 * @property cause original throwable, retained without preventing later cleanup attempts
 */
data class RenderTreeCommitFailure(
    val operation: AndroidViewOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)

/** Binding work performed for a mounted node selected for reuse. */
enum class ReuseBindingResult {
    /** Complete node binding ran again. */
    Rebound,
    /** A targeted patch updated only changed bindings. */
    Patched,
    /** This node was unchanged while descendants were still reconciled. */
    Skipped,
    /** This node and its entire subtree were proven unchanged. */
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
