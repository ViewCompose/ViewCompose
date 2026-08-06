package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.CompositionDiagnostics
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * Aggregate renderer binding statistics for one frame.
 *
 * @property inserts newly mounted nodes
 * @property reuses existing mounted nodes retained by identity
 * @property removals previously mounted nodes removed from the tree
 * @property reboundNodes nodes whose complete binding ran again
 * @property patchedNodes nodes updated through a targeted patch
 * @property skippedBindings nodes whose binding was proven unchanged
 * @property skippedSubtrees subtrees omitted because their structure and bindings were unchanged
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
)

/**
 * Binding statistics for one node type.
 *
 * @property rebound full bindings executed
 * @property patched targeted binding patches executed
 * @property skipped unchanged bindings omitted
 */
data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

/**
 * Size and depth statistics for declarative and mounted trees.
 *
 * @property vnodeCount total declarative nodes in the frame
 * @property mountedNodeCount total renderer nodes after reconciliation
 * @property maxVNodeDepth deepest declarative node, where root depth is one
 * @property maxMountedDepth deepest mounted node, where root depth is one
 */
data class RenderStructureStats(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
)

/**
 * Complete opt-in diagnostics for one render frame.
 *
 * @property stats aggregate reconciliation and binding counters
 * @property structure declarative and mounted tree size
 * @property warnings non-fatal renderer diagnostics
 * @property tree platform-independent snapshot of the rendered tree
 * @property patches ordered reconciliation operations performed for the frame
 * @property composition recomposition, invalidation, and skip diagnostics
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
 * Platform-independent node in a diagnostic render-tree snapshot.
 *
 * @property type declarative node type
 * @property key stable declarative identity, if supplied
 * @property toolingMetadata source mapping captured while the node was built
 * @property children diagnostic child nodes in render order
 */
data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val toolingMetadata: UiNodeToolingMetadata? = null,
    val children: List<RenderTreeNode> = emptyList(),
)

/**
 * One ordered reconciliation operation applied to the mounted tree.
 *
 * @property operation performed reconciliation action
 * @property type affected declarative node type
 * @property key affected node identity, if supplied
 * @property parentKey parent identity after the operation, if supplied
 * @property index child index after the operation
 * @property moved whether a reused node changed sibling position
 * @property detail optional renderer-specific diagnostic detail
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

/** Reconciliation operations reported by the renderer. */
enum class RenderPatchOperation {
    /** A new mounted node was inserted. */
    Insert,
    /** A mounted node was removed. */
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
