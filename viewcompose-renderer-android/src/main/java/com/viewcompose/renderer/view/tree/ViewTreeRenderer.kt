package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.reconcile.ChildReconciler
import com.viewcompose.renderer.reconcile.ReconcileNode

/**
 * Transactional renderer that reconciles immutable VNodes into one Android ViewGroup.
 *
 * Calls are UI-thread confined. The caller owns the host and must pass the exact mounted-node list
 * returned by its previous successful render. Structural and binding mutations are rolled back when
 * preparation fails; deferred lifecycle callbacks run only after the new View tree commits.
 */
object ViewTreeRenderer {
    private const val DEFAULT_RIPPLE_COLOR: Int = 0x22000000
    private const val WARNING_TAG: String = "ViewCompose"
    private const val MAX_WARNING_ENTRIES: Int = 200
    private val emittedModifierWarnings = mutableSetOf<String>()
    private val emittedStructureWarnings = mutableSetOf<String>()

    @VisibleForTesting
    /** Clears process-local warning de-duplication state used by renderer tests. */
    fun resetWarnings() {
        emittedModifierWarnings.clear()
        emittedStructureWarnings.clear()
    }

    /**
     * Disposes mounted subtrees and removes their root Views from [container].
     *
     * Every root is attempted even when an earlier lifecycle callback fails. Failures are returned
     * after the corresponding root View has been removed, so disposal is not transactional.
     *
     * @param container direct parent that owns every root in [mountedNodes]
     * @param mountedNodes exact committed roots to release; already disposed nodes are ignored
     * @return lifecycle and cleanup failures in encounter order
     */
    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<MountedNode>,
    ): List<RenderTreeCommitFailure> {
        val failures = mutableListOf<RenderTreeCommitFailure>()
        mountedNodes.forEach { mountedNode ->
            try {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            } catch (error: Throwable) {
                failures += error.toRenderTreeCommitFailures(
                    fallbackNodeKey = mountedNode.vnode.key,
                )
                Log.e(
                    WARNING_TAG,
                    "Failed to dispose ${mountedNode.vnode.type} node.",
                    error,
                )
            } finally {
                container.removeView(mountedNode.view)
            }
        }
        return failures
    }

    /**
     * Reconciles [nodes] into [container] and commits one new mounted-tree snapshot.
     *
     * View structure changes enter a transaction first. A preparation failure restores the previous
     * structure and is rethrown. After structural commit, deferred lifecycle callbacks are isolated:
     * their errors are reported in [RenderTreeResult.commitFailures] and do not roll back the visible
     * tree. [onReconcile] runs last with the committed result and may re-enter application code.
     *
     * @sample com.viewcompose.renderer.samples.renderIntoViewGroupSample
     * @param container exclusive ViewGroup host for this mounted tree
     * @param previous exact roots returned by the previous successful call, or an empty list initially
     * @param nodes next immutable declarative root snapshot
     * @param collectDiagnostics whether to collect statistics, structure, patch, and warning snapshots
     * @param onReconcile optional callback invoked on the UI thread after commit and cleanup
     * @return committed mounted roots, reconciliation plan, diagnostics, effects, and isolated failures
     * @throws Throwable when reconciliation or platform mutation fails before structural commit
     */
    fun renderInto(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean = true,
        onReconcile: ((RenderTreeResult) -> Unit)? = null,
    ): RenderTreeResult {
        // Wrappers insert platform host nodes before reconciliation, turning modifier semantics into normal tree structure.
        val renderNodes = AnimatedSizeNodeWrapper.wrapTree(
            NestedScrollNodeWrapper.wrapTree(nodes),
        )
        val transaction = ViewTreePatchPipeline.beginTransaction()
        val result = try {
            renderIntoTransaction(
                container = container,
                previous = previous,
                nodes = renderNodes,
                transaction = transaction,
                collectStats = collectDiagnostics,
                collectStructure = collectDiagnostics,
                collectWarnings = collectDiagnostics && onReconcile != null,
                parentNodeKey = null,
            )
        } catch (error: Throwable) {
            ViewTreePatchPipeline.rollbackTransaction(
                transaction = transaction,
                cause = error,
                defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            )
            throw error
        }
        val commitEffects = transaction.commitEffects.toList()
        // Removed nodes are disposed after commit so the old tree can still be restored if patching fails.
        val commitFailures = ViewTreePatchPipeline.commitTransaction(
            transaction = transaction,
            warningTag = WARNING_TAG,
        )
        val committedResult = result.copy(
            commitEffects = commitEffects,
            commitFailures = commitFailures,
        )
        onReconcile?.invoke(committedResult)
        return committedResult
    }

    private fun renderIntoTransaction(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        transaction: ViewTreePatchPipeline.RenderTransaction,
        collectStats: Boolean,
        collectStructure: Boolean,
        collectWarnings: Boolean,
        parentNodeKey: Any?,
    ): RenderTreeResult {
        val reconcileResult = ChildReconciler.reconcile(
            previous = previous.map { mountedNode ->
                ReconcileNode(
                    vnode = mountedNode.vnode,
                    payload = mountedNode,
                )
            },
            nodes = nodes,
        )
        val pipelineResult = ViewTreePatchPipeline.execute(
            container = container,
            reconcileResult = reconcileResult,
            defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            warningTag = WARNING_TAG,
            emittedModifierWarnings = cappedModifierWarnings(),
            transaction = transaction,
            collectStats = collectStats,
            parentNodeKey = parentNodeKey,
            renderChildren = { childContainer, childPrevious, childNodes, childParentKey ->
                renderIntoTransaction(
                    container = childContainer,
                    previous = childPrevious,
                    nodes = childNodes,
                    transaction = transaction,
                    collectStats = collectStats,
                    collectStructure = false,
                    collectWarnings = false,
                    parentNodeKey = childParentKey,
                )
            },
        )
        val structure = if (collectStructure) {
            RenderStructureStats.from(
                nodes = nodes,
                mountedNodes = pipelineResult.mountedNodes,
            )
        } else {
            RenderStructureStats()
        }
        val warnings = if (!collectWarnings) {
            emptyList()
        } else {
            collectRenderWarnings(
                nodes = nodes,
                structure = structure,
                stats = pipelineResult.stats,
            )
        }
        return RenderTreeResult(
            mountedNodes = pipelineResult.mountedNodes,
            reconcileResult = reconcileResult,
            stats = pipelineResult.stats,
            structure = structure,
            warnings = warnings,
            tree = if (collectStructure) RenderTreeNode.from(nodes) else emptyList(),
            patches = if (collectStructure) transaction.patchRecords.toList() else emptyList(),
        )
    }

    private fun cappedModifierWarnings(): MutableSet<String> {
        if (emittedModifierWarnings.size >= MAX_WARNING_ENTRIES) {
            emittedModifierWarnings.clear()
        }
        return emittedModifierWarnings
    }

    private fun collectRenderWarnings(
        nodes: List<VNode>,
        structure: RenderStructureStats,
        stats: RenderStats,
    ): List<String> {
        val warnings = RenderWarningCollector.collect(
            nodes = nodes,
            structure = structure,
            stats = stats,
        )
        warnings.forEach { warning ->
            val key = "structure|$warning"
            if (emittedStructureWarnings.size >= MAX_WARNING_ENTRIES) {
                emittedStructureWarnings.clear()
            }
            if (emittedStructureWarnings.add(key)) {
                Log.w(WARNING_TAG, warning)
            }
        }
        return warnings
    }

}
