package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.viewcompose.renderer.view.container.ReusableLayoutAnimationHost
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.reconcile.ChildReconciler
import com.viewcompose.renderer.reconcile.ReconcileResult
import com.viewcompose.renderer.reconcile.ReconcileNode

/**
 * Transactional renderer that reconciles immutable VNodes into one Android ViewGroup.
 *
 * Calls are UI-thread confined. The caller owns the host and must pass the exact mounted-node list
 * returned by its previous successful render. Structural and binding mutations are rolled back when
 * preparation fails. [renderInto] returns deferred native work for its caller to execute only after
 * the owning composition commits.
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
     * structure and is rethrown. After structural commit, [RenderTreeResult.commitEffects] contains
     * native work that a host must execute once, after its composition commit. Failures already
     * reported in [RenderTreeResult.commitFailures] do not roll back the visible tree. [onReconcile]
     * runs last with the committed result and may re-enter application code.
     *
     * @sample com.viewcompose.renderer.samples.renderIntoViewGroupSample
     * @param container exclusive ViewGroup host for this mounted tree
     * @param previous exact roots returned by the previous successful call, or an empty list initially
     * @param nodes next immutable declarative root snapshot
     * @param collectDiagnostics whether to collect structure, patch, and warning snapshots
     * @param collectStatistics whether to collect aggregate binding statistics
     * @param onReconcile optional callback invoked on the UI thread after commit and cleanup
     * @return committed mounted roots, reconciliation plan, diagnostics, deferred effects, and isolated failures
     * @throws Throwable when reconciliation or platform mutation fails before structural commit
     */
    fun renderInto(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean = true,
        collectStatistics: Boolean = collectDiagnostics,
        onReconcile: ((RenderTreeResult) -> Unit)? = null,
    ): RenderTreeResult = renderIntoInternal(
        container = container,
        previous = previous,
        nodes = nodes,
        collectDiagnostics = collectDiagnostics,
        collectStatistics = collectStatistics,
        onReconcile = onReconcile,
        timingCollector = null,
    )

    /**
     * Reconciles one tree while exposing bounded renderer intervals to [timingCollector].
     *
     * Rendering, rollback, commit-effect, diagnostics, and callback behavior are identical to
     * [renderInto]. The collector is used only for this synchronous call and is never installed as
     * process or renderer state. Collector failures are isolated from rendering.
     *
     * This is a Q3 host-integration API. It times reconciliation and direct native binding only;
     * measure/layout/draw, GPU, decoding, network, database, and external SDK work are unsupported.
     *
     * @sample com.viewcompose.renderer.samples.renderTreeTimingCollectorSample
     * @param container exclusive ViewGroup host for this mounted tree
     * @param previous exact roots returned by the previous successful call, or an empty list initially
     * @param nodes next immutable declarative root snapshot
     * @param timingCollector request-owned collector used only for this synchronous render
     * @param collectDiagnostics whether to collect structure, patch, and warning snapshots
     * @param collectStatistics whether to collect aggregate binding statistics
     * @param onReconcile optional callback invoked on the UI thread after commit and cleanup
     * @return the same committed result family as [renderInto]
     * @throws Throwable under the same renderer and platform failure conditions as [renderInto]
     */
    fun renderIntoWithTiming(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        timingCollector: RenderTreeTimingCollector,
        collectDiagnostics: Boolean = true,
        collectStatistics: Boolean = collectDiagnostics,
        onReconcile: ((RenderTreeResult) -> Unit)? = null,
    ): RenderTreeResult = renderIntoInternal(
        container = container,
        previous = previous,
        nodes = nodes,
        collectDiagnostics = collectDiagnostics,
        collectStatistics = collectStatistics,
        onReconcile = onReconcile,
        timingCollector = timingCollector,
    )

    private fun renderIntoInternal(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
        collectStatistics: Boolean,
        onReconcile: ((RenderTreeResult) -> Unit)?,
        timingCollector: RenderTreeTimingCollector?,
    ): RenderTreeResult {
        // Wrappers insert platform host nodes before reconciliation, turning modifier semantics into normal tree structure.
        val renderNodes = LayoutAnimationNodeWrapper.wrapTree(
            LayoutConstraintNodeWrapper.wrapTree(
                NestedScrollNodeWrapper.wrapTree(nodes),
            ),
        )
        val crossOwnerReuse = previous.any(MountedNode::requiresCrossOwnerRebind)
        val transaction = ViewTreePatchPipeline.beginTransaction()
        val result = try {
            renderIntoTransaction(
                container = container,
                previous = previous,
                nodes = renderNodes,
                transaction = transaction,
                collectStats = collectStatistics,
                collectStructure = collectDiagnostics,
                collectWarnings = collectDiagnostics && onReconcile != null,
                parentNodeKey = null,
                crossOwnerReuse = crossOwnerReuse,
                timingCollector = timingCollector,
                timingParentNode = null,
                timingParentDepth = 0,
            )
        } catch (error: Throwable) {
            if (crossOwnerReuse) {
                ViewTreePatchPipeline.abandonCrossOwnerTransaction(
                    transaction = transaction,
                    adoptedRoots = previous,
                    cause = error,
                )
            } else {
                ViewTreePatchPipeline.rollbackTransaction(
                    transaction = transaction,
                    cause = error,
                    defaultRippleColor = DEFAULT_RIPPLE_COLOR,
                )
            }
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

    /**
     * Applies an atomic batch to exact mounted targets without tree wrapping or reconciliation.
     *
     * Every [ViewTreeObservedPropertyPatch.next] node must differ from its committed
     * [ViewTreeObservedPropertyPatch.previous] node only by a same-concrete-type `NodeSpec`.
     * Validation and native binding share the normal renderer rollback transaction, so one failure
     * restores the whole batch.
     *
     * This is a Q3 host-integration API.
     *
     * @sample com.viewcompose.renderer.samples.patchObservedPropertySample
     * @param patches non-empty declaration-ordered batch with unique mounted targets and ids
     * @param collectDiagnostics whether to collect patch records
     * @param collectStatistics whether to collect aggregate binding statistics
     * @return property-only commit work and diagnostics; mounted roots remain unchanged
     * @throws IllegalArgumentException when [patches] is empty
     * @throws IllegalStateException when a patch violates exact-target or property-only invariants
     * @throws Throwable when native binding fails after the complete batch has been checkpointed;
     * every earlier mutation is restored before the failure is propagated
     */
    fun patchObservedProperties(
        patches: List<ViewTreeObservedPropertyPatch>,
        collectDiagnostics: Boolean = true,
        collectStatistics: Boolean = collectDiagnostics,
    ): ObservedPropertyRenderResult = patchObservedPropertiesInternal(
        patches = patches,
        collectDiagnostics = collectDiagnostics,
        collectStatistics = collectStatistics,
        timingCollector = null,
        nodeDepths = emptyMap(),
    )

    /**
     * Applies one property-only batch while exposing finite reconciliation-decision and binding timing.
     *
     * [mountedRoots] must be the committed roots that own every patch target and is traversed once
     * only for this explicit request to recover structural depth. All mutation and rollback behavior
     * is identical to [patchObservedProperties].
     *
     * @sample com.viewcompose.renderer.samples.renderTreeTimingCollectorSample
     * @param mountedRoots exact committed roots that own [patches]
     * @param patches non-empty exact-target batch
     * @param timingCollector request-owned collector used only for this synchronous call
     * @param collectDiagnostics whether to collect patch records
     * @param collectStatistics whether to collect aggregate binding statistics
     * @return property-only renderer result
     */
    fun patchObservedPropertiesWithTiming(
        mountedRoots: List<MountedNode>,
        patches: List<ViewTreeObservedPropertyPatch>,
        timingCollector: RenderTreeTimingCollector,
        collectDiagnostics: Boolean = true,
        collectStatistics: Boolean = collectDiagnostics,
    ): ObservedPropertyRenderResult = patchObservedPropertiesInternal(
        patches = patches,
        collectDiagnostics = collectDiagnostics,
        collectStatistics = collectStatistics,
        timingCollector = timingCollector,
        nodeDepths = mountedNodeDepths(mountedRoots),
    )

    private fun patchObservedPropertiesInternal(
        patches: List<ViewTreeObservedPropertyPatch>,
        collectDiagnostics: Boolean,
        collectStatistics: Boolean,
        timingCollector: RenderTreeTimingCollector?,
        nodeDepths: Map<MountedNode, Int>,
    ): ObservedPropertyRenderResult {
        require(patches.isNotEmpty()) { "Observed-property patch batches cannot be empty." }
        val transaction = ViewTreePatchPipeline.beginTransaction()
        val stats = try {
            ViewTreePatchPipeline.executeObservedPropertyPatches(
                patches = patches,
                defaultRippleColor = DEFAULT_RIPPLE_COLOR,
                transaction = transaction,
                collectStats = collectStatistics,
                timingCollector = timingCollector,
                nodeDepths = nodeDepths,
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
        val commitFailures = ViewTreePatchPipeline.commitTransaction(
            transaction = transaction,
            warningTag = WARNING_TAG,
        )
        return ObservedPropertyRenderResult(
            stats = stats,
            patches = if (collectDiagnostics) transaction.patchRecords.toList() else emptyList(),
            commitEffects = commitEffects,
            commitFailures = commitFailures,
        )
    }

    private fun mountedNodeDepths(roots: List<MountedNode>): Map<MountedNode, Int> {
        val depths = java.util.IdentityHashMap<MountedNode, Int>()
        fun visit(node: MountedNode, depth: Int) {
            depths[node] = depth
            node.children.forEach { child -> visit(child, depth + 1) }
        }
        roots.forEach { root -> visit(root, depth = 0) }
        return depths
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
        crossOwnerReuse: Boolean,
        timingCollector: RenderTreeTimingCollector?,
        timingParentNode: VNode?,
        timingParentDepth: Int,
    ): RenderTreeResult {
        val previousReconcileNodes = previous.map { mountedNode ->
            ReconcileNode(
                vnode = mountedNode.vnode,
                payload = mountedNode,
            )
        }
        val reconcileResult = timingCollector.measureRenderInterval(
            node = timingParentNode,
            depth = timingParentDepth,
            phase = RenderTreeTimingPhase.Reconciliation,
        ) {
            if (crossOwnerReuse) {
                ChildReconciler.reconcileForCrossOwnerReuse(
                    previous = previousReconcileNodes,
                    nodes = nodes,
                )
            } else {
                ChildReconciler.reconcile(
                    previous = previousReconcileNodes,
                    nodes = nodes,
                )
            }
        }
        val pipelineResult = ViewTreePatchPipeline.execute(
            container = container,
            reconcileResult = reconcileResult,
            defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            warningTag = WARNING_TAG,
            emittedModifierWarnings = cappedModifierWarnings(),
            transaction = transaction,
            collectStats = collectStats,
            parentNodeKey = parentNodeKey,
            timingCollector = timingCollector,
            nodeDepth = timingParentDepth + 1,
            renderChildren = { childContainer, childPrevious, childNodes, childParentKey, parentNode ->
                renderIntoTransaction(
                    container = childContainer,
                    previous = childPrevious,
                    nodes = childNodes,
                    transaction = transaction,
                    collectStats = collectStats,
                    collectStructure = false,
                    collectWarnings = false,
                    parentNodeKey = childParentKey,
                    crossOwnerReuse = childPrevious.any(MountedNode::requiresCrossOwnerRebind),
                    timingCollector = timingCollector,
                    timingParentNode = parentNode,
                    timingParentDepth = timingParentDepth + 1,
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

    /** Resets every interop node and detaches roots without releasing their physical Views. */
    fun detachMountedForReuse(
        container: ViewGroup,
        mountedNodes: List<MountedNode>,
    ): Boolean {
        if (!mountedNodes.all(::isReusableMountedNode)) return false
        mountedNodes.forEach(::resetMountedNode)
        mountedNodes.forEach { node ->
            container.removeView(node.view)
            markCrossOwnerRebind(node)
        }
        return true
    }

    /** Attaches reset roots to a new exclusive item container. */
    fun attachReusableMounted(
        container: ViewGroup,
        mountedNodes: List<MountedNode>,
    ) {
        mountedNodes.forEach { node ->
            (node.view.parent as? ViewGroup)?.removeView(node.view)
            container.addView(node.view)
        }
    }

    /** Permanently releases a detached reusable tree. */
    fun releaseReusableMounted(mountedNodes: List<MountedNode>): List<RenderTreeCommitFailure> {
        val failures = mutableListOf<RenderTreeCommitFailure>()
        mountedNodes.forEach { node ->
            try {
                ViewTreeDisposer.disposeMountedNode(node)
            } catch (error: Throwable) {
                failures += error.toRenderTreeCommitFailures(node.vnode.key)
                // A detached cache entry no longer has a logical RenderSession that can receive
                // failures. Report through renderer diagnostics without retaining the old owner.
                Log.e(
                    WARNING_TAG,
                    "Failed to release cached ${node.vnode.type} node.",
                    error,
                )
            } finally {
                (node.view.parent as? ViewGroup)?.removeView(node.view)
            }
        }
        return failures
    }

    private fun isReusableMountedNode(node: MountedNode): Boolean {
        if (node.vnode.type in sessionOwningNodeTypes) return false
        if (node.vnode.type == com.viewcompose.ui.node.NodeType.AndroidView &&
            node.vnode.requireSpec<com.viewcompose.ui.node.spec.AndroidViewNodeProps>().onReset == null
        ) {
            return false
        }
        return node.children.all(::isReusableMountedNode)
    }

    private fun resetMountedNode(node: MountedNode) {
        node.children.forEach(::resetMountedNode)
        (node.view as? ReusableLayoutAnimationHost)?.resetLayoutAnimationForReuse()
        if (node.vnode.type == com.viewcompose.ui.node.NodeType.AndroidView) {
            val reset = checkNotNull(
                node.vnode.requireSpec<com.viewcompose.ui.node.spec.AndroidViewNodeProps>().onReset,
            )
            node.vnode.runAndroidViewOperation(
                com.viewcompose.ui.node.spec.AndroidViewOperation.Reset,
            ) {
                reset(node.view)
            }
        }
    }

    private fun markCrossOwnerRebind(node: MountedNode) {
        node.requiresCrossOwnerRebind = true
        node.children.forEach(::markCrossOwnerRebind)
    }

    private val sessionOwningNodeTypes = setOf(
        com.viewcompose.ui.node.NodeType.LazyColumn,
        com.viewcompose.ui.node.NodeType.LazyRow,
        com.viewcompose.ui.node.NodeType.LazyVerticalGrid,
        com.viewcompose.ui.node.NodeType.HorizontalPager,
        com.viewcompose.ui.node.NodeType.VerticalPager,
    )

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

/**
 * One exact mounted-node property replacement consumed by [ViewTreeRenderer].
 *
 * This is a Q3 host-integration model.
 *
 * @sample com.viewcompose.renderer.samples.patchObservedPropertySample
 * @property id session-scoped observation identity that must be unique within one batch
 * @property mountedNode exact committed renderer target; the renderer rejects disposed or stale targets
 * @property previous committed node snapshot used for identity validation and transactional rollback
 * @property next candidate node snapshot whose only allowed change is a same-concrete-type `NodeSpec`
 */
data class ViewTreeObservedPropertyPatch(
    val id: Long,
    val mountedNode: MountedNode,
    val previous: VNode,
    val next: VNode,
)
