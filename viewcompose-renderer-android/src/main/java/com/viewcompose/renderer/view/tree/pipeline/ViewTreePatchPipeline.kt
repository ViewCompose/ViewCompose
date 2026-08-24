package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.layoutModifiersChanged
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.renderer.reconcile.InsertPatch
import com.viewcompose.renderer.reconcile.ReconcileResult
import com.viewcompose.renderer.reconcile.RemovePatch
import com.viewcompose.renderer.reconcile.RenderPatch
import com.viewcompose.renderer.reconcile.ReusePatch
import com.viewcompose.renderer.view.container.ConstraintRebuildReason
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.ChildHostViewGroup
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import java.util.IdentityHashMap

/**
 * Transactional pipeline that applies reconciliation patches to an Android View tree.
 * Transactional pipeline that applies reconcile patches to the Android View tree.
 *
 * Preflight resolves modifiers, binding plans, and LayoutParams before apply mutates the View tree.
 * The preflight phase resolves modifiers, binding plans, and layout params before the apply phase mutates the View tree.
 */
internal object ViewTreePatchPipeline {
    private val emptyStats = RenderStats()

    /**
     * Mounted nodes and statistics after patching one container.
     * Mounted nodes and statistics after applying patches for one container.
     */
    data class ExecutionResult(
        val mountedNodes: List<MountedNode>,
        val stats: RenderStats,
    )

    /** Applies a prevalidated exact-target property batch without reconciling tree structure. */
    fun executeObservedPropertyPatches(
        patches: List<ViewTreeObservedPropertyPatch>,
        defaultRippleColor: Int,
        transaction: RenderTransaction,
        collectStats: Boolean,
        timingCollector: RenderTreeTimingCollector? = null,
        nodeDepths: Map<MountedNode, Int> = emptyMap(),
    ): RenderStats {
        val ids = HashSet<Long>(patches.size)
        val targets = IdentityHashMap<MountedNode, Unit>(patches.size)
        val prepared = ArrayList<Pair<ViewTreeObservedPropertyPatch, NodeBindingPlan>>(patches.size)
        patches.forEach { patch ->
            timingCollector.measureRenderInterval(
                node = patch.next,
                depth = nodeDepths[patch.mountedNode] ?: 0,
                phase = RenderTreeTimingPhase.Reconciliation,
            ) {
                check(ids.add(patch.id)) {
                    "Observed-property patch ids must be unique within one batch."
                }
                check(targets.put(patch.mountedNode, Unit) == null) {
                    "A mounted node can be targeted only once in one observed-property batch."
                }
                check(!patch.mountedNode.disposed) {
                    "Observed property ${patch.id} targets a disposed mounted node."
                }
                check(patch.mountedNode.vnode === patch.previous) {
                    "Observed property ${patch.id} no longer targets the committed VNode."
                }
                check(patch.previous.type == patch.next.type && patch.previous.key == patch.next.key) {
                    "Observed property ${patch.id} cannot change node type or key."
                }
                check(patch.previous.modifier == patch.next.modifier) {
                    "Observed property ${patch.id} cannot change Modifier."
                }
                check(patch.previous.environment == patch.next.environment) {
                    "Observed property ${patch.id} cannot change environment."
                }
                check(
                    patch.previous.children.size == patch.next.children.size &&
                        patch.previous.children.indices.all { index ->
                            patch.previous.children[index] === patch.next.children[index]
                        },
                ) {
                    "Observed property ${patch.id} cannot change children."
                }
                check(patch.previous.observedPropertyId == patch.next.observedPropertyId) {
                    "Observed property ${patch.id} cannot change transaction identity."
                }
                check(
                    UiNodeTooling.metadataOf(patch.previous) ===
                        UiNodeTooling.metadataOf(patch.next),
                ) {
                    "Observed property ${patch.id} cannot change tooling identity."
                }
                check(patch.previous.spec::class == patch.next.spec::class) {
                    "Observed property ${patch.id} cannot change NodeSpec type."
                }
                if (patch.next.type == NodeType.AndroidView) {
                    check(
                        patch.previous.requireSpec<AndroidViewNodeProps>().constructionIdentity ==
                            patch.next.requireSpec<AndroidViewNodeProps>().constructionIdentity,
                    ) {
                        "Observed property ${patch.id} cannot change AndroidView construction " +
                            "identity; use a full tree render."
                    }
                }
                prepared += patch to NodeBindingDiffer.plan(patch.previous, patch.next)
            }
        }

        var stats = emptyStats
        prepared.forEach { (patch, bindingPlan) ->
            val mountedNode = patch.mountedNode
            // Even a native no-op advances MountedNode.vnode below. Checkpoint every target so a
            // later failure restores the renderer snapshot together with visible View state.
            captureNode(transaction, mountedNode)
            if (bindingPlan != NodeBindingPlan.SkipSubtree) {
                timingCollector.measureRenderInterval(
                    node = patch.next,
                    depth = nodeDepths[mountedNode] ?: 0,
                    phase = RenderTreeTimingPhase.Binding,
                ) {
                    when (bindingPlan) {
                        NodeBindingPlan.Rebind -> {
                            bindView(
                                view = mountedNode.view,
                                node = patch.next,
                                defaultRippleColor = defaultRippleColor,
                                resolved = patch.next.modifier.resolve(),
                                bindingMode = NodeBindingMode.Deferred,
                            )?.let(transaction.commitEffects::add)
                            scheduleAndroidViewCommit(transaction, mountedNode.view, patch.next)
                        }

                        NodeBindingPlan.ModifierOnly -> error(
                            "Observed property ${patch.id} produced a modifier-only binding plan.",
                        )

                        NodeBindingPlan.SkipSelfOnly -> error(
                            "Observed property ${patch.id} produced a child-only binding plan.",
                        )

                        NodeBindingPlan.SkipSubtree -> Unit
                        is NodeBindingPlan.Patch -> {
                            check(!bindingPlan.modifierChanged) {
                                "Observed property ${patch.id} produced a modifier patch."
                            }
                            NodeViewBinderRegistry.applyPatch(
                                view = mountedNode.view,
                                patch = bindingPlan.patch,
                                mode = NodeBindingMode.Deferred,
                                nodeKey = patch.next.key,
                            )?.let(transaction.commitEffects::add)
                        }
                    }
                }
            }
            mountedNode.vnode = patch.next
            // Tooling metadata is identity-stable by preflight, so the existing View association
            // remains valid without a synchronized weak-map write on every property update.
            if (collectStats) {
                val result = when (bindingPlan) {
                    NodeBindingPlan.Rebind -> ReuseBindingResult.Rebound
                    NodeBindingPlan.SkipSubtree -> ReuseBindingResult.SkippedSubtree
                    is NodeBindingPlan.Patch -> ReuseBindingResult.Patched
                    NodeBindingPlan.ModifierOnly,
                    NodeBindingPlan.SkipSelfOnly,
                    -> error("Invalid observed-property binding plan was not rejected.")
                }
                stats = stats.mergeWith(
                    emptyStats.withReuse(result = result, nodeType = patch.next.type),
                )
                transaction.recordPatch(
                    RenderPatchRecord(
                        operation = bindingPlan.toPatchOperation(),
                        type = patch.next.type,
                        key = patch.next.key,
                        parentKey = null,
                        index = (mountedNode.view.parent as? ViewGroup)
                            ?.indexOfChild(mountedNode.view)
                            ?: -1,
                        detail = (bindingPlan as? NodeBindingPlan.Patch)
                            ?.patch
                            ?.let { nodePatch -> nodePatch::class.simpleName },
                        toolingMetadata = UiNodeTooling.metadataOf(patch.next),
                    ),
                )
            }
        }
        return stats
    }

    private data class PatchApplicationResult(
        val mountedNode: MountedNode,
        val stats: RenderStats,
    )

    private data class PreparedPatch(
        val patch: RenderPatch<MountedNode>,
        val bindingPlan: NodeBindingPlan? = null,
        val nextResolved: ResolvedModifiers? = null,
        val layoutParams: ViewGroup.LayoutParams? = null,
        val updatesLayoutParams: Boolean = false,
        val replacesAndroidView: Boolean = false,
    )

    /**
     * State that one render transaction must roll back or commit later.
     * State that must be rolled back or deferred during one render transaction.
     */
    internal class RenderTransaction internal constructor() {
        internal val mountedCheckpoints = LinkedHashMap<MountedNode, MountedCheckpoint>()
        internal val containerCheckpoints = LinkedHashMap<ViewGroup, ContainerCheckpoint>()
        internal val insertedNodes = LinkedHashSet<MountedNode>()
        internal val deferredRemovals = LinkedHashSet<MountedNode>()
        internal val commitEffects = mutableListOf<RenderTreeCommitEffect>()
        internal val patchRecords = mutableListOf<RenderPatchRecord>()
    }

    /**
     * Rollback checkpoint for one mounted node.
     * Rollback checkpoint for a mounted node.
     */
    internal data class MountedCheckpoint(
        val mountedNode: MountedNode,
        val vnode: VNode,
        val children: List<MountedNode>,
        val layoutParams: ViewGroup.LayoutParams?,
        val disposed: Boolean,
    )

    /**
     * Rollback checkpoint for a container's child order.
     * Rollback checkpoint for a container's child order.
     */
    internal data class ContainerCheckpoint(
        val container: ViewGroup,
        val children: List<View>,
    )

    /**
     * Starts a new View-tree transaction.
     * Starts a new View tree transaction.
     */
    internal fun beginTransaction(): RenderTransaction = RenderTransaction()

    /**
     * Commits the transaction's deferred disposal phase.
     * Commits the transaction's deferred release phase.
     */
    internal fun commitTransaction(
        transaction: RenderTransaction,
        warningTag: String,
    ): List<RenderTreeCommitFailure> {
        val failures = mutableListOf<RenderTreeCommitFailure>()
        transaction.deferredRemovals.forEach { mountedNode ->
            try {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            } catch (error: Throwable) {
                failures += error.toRenderTreeCommitFailures(
                    fallbackNodeKey = mountedNode.vnode.key,
                )
                Log.e(
                    warningTag,
                    "Failed to release removed ${mountedNode.vnode.type} node after render commit.",
                    error,
                )
            }
        }
        transaction.deferredRemovals.clear()
        transaction.insertedNodes.clear()
        return failures
    }

    /**
     * Rolls back failed View-tree changes using recorded checkpoints.
     * Rolls back failed View tree mutations from checkpoints.
     */
    internal fun rollbackTransaction(
        transaction: RenderTransaction,
        cause: Throwable,
        defaultRippleColor: Int,
    ) {
        fun bestEffort(block: () -> Unit) {
            try {
                block()
            } catch (rollbackError: Throwable) {
                cause.addSuppressed(rollbackError)
            }
        }

        // Remove newly inserted Views first, then restore mounted-node state and container order.
        // Newly inserted Views are removed first, then previous mounted-node and container state is restored.
        transaction.insertedNodes.toList().asReversed().forEach { mountedNode ->
            bestEffort {
                (mountedNode.view.parent as? ViewGroup)?.let { parent ->
                    parent.removeView(mountedNode.view)
                    DecorationChildDrawingOrder.invalidate(parent)
                }
            }
        }

        transaction.mountedCheckpoints.values.forEach { checkpoint ->
            checkpoint.mountedNode.vnode = checkpoint.vnode
            ViewNodeToolingRegistry.bind(
                view = checkpoint.mountedNode.view,
                node = checkpoint.vnode,
            )
            checkpoint.mountedNode.children = checkpoint.children
            checkpoint.mountedNode.disposed = checkpoint.disposed
            checkpoint.mountedNode.view.layoutParams = checkpoint.layoutParams
        }

        transaction.mountedCheckpoints.values.forEach { checkpoint ->
            bestEffort {
                bindView(
                    view = checkpoint.mountedNode.view,
                    node = checkpoint.vnode,
                    defaultRippleColor = defaultRippleColor,
                    resolved = checkpoint.vnode.modifier.resolve(),
                    bindingMode = NodeBindingMode.Rollback,
                )
                checkpoint.mountedNode.view.layoutParams = checkpoint.layoutParams
            }
        }

        transaction.containerCheckpoints.values.toList().asReversed().forEach { checkpoint ->
            checkpoint.children.forEachIndexed { index, child ->
                bestEffort {
                    val currentParent = child.parent as? ViewGroup
                    if (currentParent !== checkpoint.container) {
                        currentParent?.removeView(child)
                        currentParent?.let(DecorationChildDrawingOrder::invalidate)
                        checkpoint.container.addView(
                            child,
                            index.coerceAtMost(checkpoint.container.childCount),
                        )
                        DecorationChildDrawingOrder.invalidate(checkpoint.container)
                    } else {
                        moveViewToIndex(
                            container = checkpoint.container,
                            view = child,
                            targetIndex = index,
                        )
                    }
                }
            }
        }

        transaction.insertedNodes.toList().asReversed().forEach { mountedNode ->
            bestEffort {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            }
        }
        transaction.deferredRemovals.clear()
        transaction.insertedNodes.clear()
        transaction.commitEffects.clear()
        transaction.patchRecords.clear()
    }

    /**
     * Abandons a failed tree adopted from another logical owner without rebinding the old owner.
     *
     * Cross-owner reuse has no previous frame in the receiving session. Checkpoint metadata is
     * restored only so physical release uses the factory owner's release contract; rollback binding
     * is intentionally skipped because it would invoke callbacks from an already disposed key.
     */
    internal fun abandonCrossOwnerTransaction(
        transaction: RenderTransaction,
        adoptedRoots: List<MountedNode>,
        cause: Throwable,
    ) {
        fun bestEffort(block: () -> Unit) {
            try {
                block()
            } catch (cleanupError: Throwable) {
                cause.addSuppressed(cleanupError)
            }
        }

        transaction.mountedCheckpoints.values.forEach { checkpoint ->
            checkpoint.mountedNode.vnode = checkpoint.vnode
            checkpoint.mountedNode.children = checkpoint.children
            checkpoint.mountedNode.disposed = checkpoint.disposed
            checkpoint.mountedNode.view.layoutParams = checkpoint.layoutParams
            ViewNodeToolingRegistry.bind(checkpoint.mountedNode.view, checkpoint.vnode)
        }
        val releaseCandidates = LinkedHashSet<MountedNode>().apply {
            addAll(adoptedRoots)
            addAll(transaction.insertedNodes)
            addAll(transaction.deferredRemovals)
        }
        releaseCandidates.forEach { node ->
            bestEffort {
                ViewTreeDisposer.disposeMountedNode(node)
                (node.view.parent as? ViewGroup)?.let { parent ->
                    parent.removeView(node.view)
                    DecorationChildDrawingOrder.invalidate(parent)
                }
            }
        }
        transaction.deferredRemovals.clear()
        transaction.insertedNodes.clear()
        transaction.commitEffects.clear()
        transaction.patchRecords.clear()
    }

    fun execute(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        defaultRippleColor: Int,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        transaction: RenderTransaction,
        collectStats: Boolean,
        parentNodeKey: Any?,
        timingCollector: RenderTreeTimingCollector?,
        nodeDepth: Int,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?, VNode) -> RenderTreeResult,
    ): ExecutionResult {
        val mountContainer = resolveChildHost(container)
        // Preflight completes all potentially throwing resolution before structural mutation, reducing rollback complexity.
        // preflight completes potentially throwing resolution before structural mutations, reducing rollback complexity.
        val preparedPatches = preflight(
            container = mountContainer,
            reconcileResult = reconcileResult,
            warningTag = warningTag,
            emittedModifierWarnings = emittedModifierWarnings,
            timingCollector = timingCollector,
            nodeDepth = nodeDepth,
        )
        var stats = emptyStats
        val nextMounted = mutableListOf<MountedNode>()
        preparedPatches.forEach { preparedPatch ->
            val patchResult = timingCollector.measureRenderInterval(
                node = preparedPatch.patch.timingNode(),
                depth = nodeDepth,
                phase = RenderTreeTimingPhase.Reconciliation,
            ) {
                applyPatch(
                    container = mountContainer,
                    preparedPatch = preparedPatch,
                    defaultRippleColor = defaultRippleColor,
                    transaction = transaction,
                    collectStats = collectStats,
                    parentNodeKey = parentNodeKey,
                    timingCollector = timingCollector,
                    nodeDepth = nodeDepth,
                    renderChildren = renderChildren,
                )
            }
            if (collectStats) {
                stats = stats.mergeWith(patchResult.stats)
            }
            nextMounted += patchResult.mountedNode
        }
        reconcileResult.removals.forEach { removal ->
            // Detach removals immediately but defer disposal until commit so rollback can restore the old tree.
            // remove detaches from the parent immediately but defers disposal until commit so rollback can restore the old tree.
            timingCollector.measureRenderInterval(
                node = removal.payload.vnode,
                depth = nodeDepth,
                phase = RenderTreeTimingPhase.Reconciliation,
            ) {
                applyRemoval(
                    container = mountContainer,
                    removal = removal,
                    transaction = transaction,
                )
            }
            if (collectStats) {
                stats = stats.withRemoval()
                transaction.recordPatch(RenderPatchRecord(
                    operation = RenderPatchOperation.Remove,
                    type = removal.payload.vnode.type,
                    key = removal.payload.vnode.key,
                    parentKey = parentNodeKey,
                    index = removal.previousIndex,
                    toolingMetadata = UiNodeTooling.metadataOf(removal.payload.vnode),
                ))
            }
        }
        return ExecutionResult(
            mountedNodes = nextMounted,
            stats = stats,
        )
    }

    private fun applyPatch(
        container: ViewGroup,
        preparedPatch: PreparedPatch,
        defaultRippleColor: Int,
        transaction: RenderTransaction,
        collectStats: Boolean,
        parentNodeKey: Any?,
        timingCollector: RenderTreeTimingCollector?,
        nodeDepth: Int,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?, VNode) -> RenderTreeResult,
    ): PatchApplicationResult {
        val patch = preparedPatch.patch
        return when (patch) {
            is InsertPatch -> {
                val resolved = checkNotNull(preparedPatch.nextResolved)
                val mountedNode = mountNode(
                    context = container.context,
                    node = patch.nextVNode,
                    defaultRippleColor = defaultRippleColor,
                    resolved = resolved,
                    transaction = transaction,
                    timingCollector = timingCollector,
                    nodeDepth = nodeDepth,
                    renderChildren = renderChildren,
                )
                if (collectStats) {
                    transaction.recordPatch(RenderPatchRecord(
                        operation = RenderPatchOperation.Insert,
                        type = patch.nextVNode.type,
                        key = patch.nextVNode.key,
                        parentKey = parentNodeKey,
                        index = patch.targetIndex,
                        detail = mountedNode.androidViewDiagnosticDetail(),
                        toolingMetadata = UiNodeTooling.metadataOf(patch.nextVNode),
                    ))
                }
                captureContainer(
                    transaction = transaction,
                    target = container,
                )
                // Record container order before insertion so failure can restore the original child list.
                // Capture container order before insertion so failures can restore the original child list.
                container.addView(
                    mountedNode.view,
                    patch.targetIndex.coerceAtMost(container.childCount),
                    checkNotNull(preparedPatch.layoutParams),
                )
                DecorationChildDrawingOrder.invalidate(container)
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = if (collectStats) RenderStats(inserts = 1) else emptyStats,
                )
            }

            is ReusePatch -> {
                val mountedNode = patch.payload
                val bindingPlan = checkNotNull(preparedPatch.bindingPlan)
                if (preparedPatch.replacesAndroidView) {
                    return replaceAndroidView(
                        container = container,
                        patch = patch,
                        preparedPatch = preparedPatch,
                        defaultRippleColor = defaultRippleColor,
                        transaction = transaction,
                        collectStats = collectStats,
                        parentNodeKey = parentNodeKey,
                        timingCollector = timingCollector,
                        nodeDepth = nodeDepth,
                        renderChildren = renderChildren,
                    )
                }
                val reusesExactVNode = bindingPlan == NodeBindingPlan.SkipSubtree &&
                    mountedNode.vnode === patch.nextVNode
                val needsMove = container.indexOfChild(mountedNode.view) != patch.targetIndex
                if (reusesExactVNode && !needsMove) {
                    // An identical, unmoved VNode can skip both binding and child reconciliation for its full subtree.
                    // When the exact VNode is reused and no move is needed, binding and child reconciliation can be skipped.
                    if (collectStats) {
                        transaction.recordPatch(RenderPatchRecord(
                            operation = RenderPatchOperation.SkipSubtree,
                            type = patch.nextVNode.type,
                            key = patch.nextVNode.key,
                            parentKey = parentNodeKey,
                            index = patch.targetIndex,
                            toolingMetadata = UiNodeTooling.metadataOf(patch.nextVNode),
                        ))
                    }
                    return PatchApplicationResult(
                        mountedNode = mountedNode,
                        stats = if (collectStats) {
                            emptyStats.withReuse(
                                result = ReuseBindingResult.SkippedSubtree,
                                nodeType = patch.nextVNode.type,
                            )
                        } else {
                            emptyStats
                        },
                    )
                }
                if (!reusesExactVNode) {
                    captureNode(
                        transaction = transaction,
                        mountedNode = mountedNode,
                    )
                }
                if (
                    bindingPlan != NodeBindingPlan.SkipSelfOnly &&
                    bindingPlan != NodeBindingPlan.SkipSubtree
                ) timingCollector.measureRenderInterval(
                    node = patch.nextVNode,
                    depth = nodeDepth,
                    phase = RenderTreeTimingPhase.Binding,
                ) {
                    when (bindingPlan) {
                        NodeBindingPlan.Rebind -> {
                            bindView(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                                defaultRippleColor = defaultRippleColor,
                                resolved = checkNotNull(preparedPatch.nextResolved),
                                bindingMode = NodeBindingMode.Deferred,
                            )?.let(transaction.commitEffects::add)
                            scheduleAndroidViewCommit(
                                transaction = transaction,
                                view = mountedNode.view,
                                node = patch.nextVNode,
                            )
                        }

                        NodeBindingPlan.ModifierOnly -> {
                            ViewModifierApplier.applyModifier(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                                defaultRippleColor = defaultRippleColor,
                                resolved = checkNotNull(preparedPatch.nextResolved),
                            )
                            ModifierInteractionApplier.applyNativeViewConfigs(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                            )
                        }

                        NodeBindingPlan.SkipSelfOnly,
                        NodeBindingPlan.SkipSubtree,
                        -> Unit
                        is NodeBindingPlan.Patch -> {
                            if (bindingPlan.modifierChanged) {
                                ViewModifierApplier.applyModifier(
                                    view = mountedNode.view,
                                    node = patch.nextVNode,
                                    defaultRippleColor = defaultRippleColor,
                                    resolved = checkNotNull(preparedPatch.nextResolved),
                                )
                            }
                            NodeViewBinderRegistry.applyPatch(
                                view = mountedNode.view,
                                patch = bindingPlan.patch,
                                mode = NodeBindingMode.Deferred,
                                nodeKey = patch.nextVNode.key,
                            )?.let(transaction.commitEffects::add)
                            if (bindingPlan.modifierChanged) {
                                ModifierInteractionApplier.applyNativeViewConfigs(
                                    view = mountedNode.view,
                                    node = patch.nextVNode,
                                )
                            }
                        }
                    }
                    if (preparedPatch.updatesLayoutParams) {
                        // Layout modifier changes rebuild LayoutParams and request constraint regeneration.
                        mountedNode.view.layoutParams = checkNotNull(preparedPatch.layoutParams)
                        (container as? DeclarativeConstraintLayout)?.requestConstraintRebuild(
                            ConstraintRebuildReason.ScalarInput,
                        )
                    }
                }
                val childResult = if (shouldReconcileChildren(bindingPlan)) {
                    reconcileChildren(
                        view = mountedNode.view,
                        previousChildren = mountedNode.children,
                        node = patch.nextVNode,
                        renderChildren = renderChildren,
                    )
                } else {
                    emptyChildResult(mountedNode.children)
                }
                mountedNode.children = childResult.mountedNodes
                mountedNode.vnode = patch.nextVNode
                mountedNode.requiresCrossOwnerRebind = false
                ViewNodeToolingRegistry.bind(
                    view = mountedNode.view,
                    node = patch.nextVNode,
                )
                if (needsMove) {
                    captureContainer(
                        transaction = transaction,
                        target = container,
                    )
                }
                moveViewToIndex(
                    container = container,
                    view = mountedNode.view,
                    targetIndex = patch.targetIndex,
                )
                if (collectStats) {
                    transaction.recordPatch(RenderPatchRecord(
                        operation = bindingPlan.toPatchOperation(),
                        type = patch.nextVNode.type,
                        key = patch.nextVNode.key,
                        parentKey = parentNodeKey,
                        index = patch.targetIndex,
                        moved = needsMove,
                        detail = (bindingPlan as? NodeBindingPlan.Patch)
                            ?.patch
                            ?.let { nodePatch -> nodePatch::class.simpleName }
                            ?: if (bindingPlan == NodeBindingPlan.ModifierOnly) {
                                "ModifierOnly"
                            } else {
                                mountedNode.androidViewDiagnosticDetail()
                            },
                        toolingMetadata = UiNodeTooling.metadataOf(patch.nextVNode),
                    ))
                }
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = if (collectStats) {
                        childResult.stats.withReuse(
                            result = when (bindingPlan) {
                                NodeBindingPlan.Rebind -> ReuseBindingResult.Rebound
                                NodeBindingPlan.ModifierOnly -> ReuseBindingResult.Patched
                                NodeBindingPlan.SkipSelfOnly -> ReuseBindingResult.Skipped
                                NodeBindingPlan.SkipSubtree -> ReuseBindingResult.SkippedSubtree
                                is NodeBindingPlan.Patch -> ReuseBindingResult.Patched
                            },
                            nodeType = patch.nextVNode.type,
                        )
                    } else {
                        emptyStats
                    },
                )
            }
        }
    }

    private fun replaceAndroidView(
        container: ViewGroup,
        patch: ReusePatch<MountedNode>,
        preparedPatch: PreparedPatch,
        defaultRippleColor: Int,
        transaction: RenderTransaction,
        collectStats: Boolean,
        parentNodeKey: Any?,
        timingCollector: RenderTreeTimingCollector?,
        nodeDepth: Int,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?, VNode) -> RenderTreeResult,
    ): PatchApplicationResult {
        val displaced = patch.payload
        val candidate = mountNode(
            context = container.context,
            node = patch.nextVNode,
            defaultRippleColor = defaultRippleColor,
            resolved = checkNotNull(preparedPatch.nextResolved),
            transaction = transaction,
            timingCollector = timingCollector,
            nodeDepth = nodeDepth,
            renderChildren = renderChildren,
        )
        candidate.view.setTag(
            R.id.viewcompose_android_view_construction_generation,
            displaced.androidViewConstructionGeneration() + 1L,
        )

        // Keep the committed View untouched until candidate creation and replay-safe binding have
        // both succeeded. Container order then provides the rollback checkpoint while disposal is
        // deferred until structural commit, exactly like an ordinary removal.
        captureContainer(transaction = transaction, target = container)
        check(container.indexOfChild(displaced.view) >= 0) {
            "AndroidView construction replacement requires the committed View in its container."
        }
        container.removeView(displaced.view)
        container.addView(
            candidate.view,
            patch.targetIndex.coerceAtMost(container.childCount),
            checkNotNull(preparedPatch.layoutParams),
        )
        DecorationChildDrawingOrder.invalidate(container)
        transaction.deferredRemovals += displaced

        if (collectStats) {
            transaction.recordPatch(
                RenderPatchRecord(
                    operation = RenderPatchOperation.Rebind,
                    type = patch.nextVNode.type,
                    key = patch.nextVNode.key,
                    parentKey = parentNodeKey,
                    index = patch.targetIndex,
                    detail = candidate.androidViewDiagnosticDetail(replacement = true),
                    toolingMetadata = UiNodeTooling.metadataOf(patch.nextVNode),
                ),
            )
        }
        return PatchApplicationResult(
            mountedNode = candidate,
            stats = if (collectStats) {
                emptyStats.withReuse(
                    result = ReuseBindingResult.Rebound,
                    nodeType = patch.nextVNode.type,
                )
            } else {
                emptyStats
            },
        )
    }

    private fun applyRemoval(
        container: ViewGroup,
        removal: RemovePatch<MountedNode>,
        transaction: RenderTransaction,
    ) {
        captureContainer(
            transaction = transaction,
            target = container,
        )
        container.removeView(removal.payload.view)
        DecorationChildDrawingOrder.invalidate(container)
        transaction.deferredRemovals += removal.payload
    }

    private fun reconcileChildren(
        view: View,
        previousChildren: List<MountedNode>,
        node: VNode,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?, VNode) -> RenderTreeResult,
    ): RenderTreeResult {
        val viewGroup = view as? ViewGroup ?: return RenderTreeResult(
            mountedNodes = emptyList(),
            reconcileResult = ReconcileResult(
                patches = emptyList(),
                removals = emptyList(),
            ),
            stats = emptyStats,
        )
        return renderChildren(
            resolveChildHost(viewGroup),
            previousChildren,
            node.children,
            node.key,
            node,
        )
    }

    internal fun shouldReconcileChildren(bindingPlan: NodeBindingPlan): Boolean {
        return bindingPlan != NodeBindingPlan.SkipSubtree
    }

    private fun emptyChildResult(children: List<MountedNode>): RenderTreeResult {
        return RenderTreeResult(
            mountedNodes = children,
            reconcileResult = ReconcileResult(
                patches = emptyList(),
                removals = emptyList(),
            ),
            stats = emptyStats,
        )
    }

    private fun mountNode(
        context: Context,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers,
        transaction: RenderTransaction,
        timingCollector: RenderTreeTimingCollector?,
        nodeDepth: Int,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?, VNode) -> RenderTreeResult,
    ): MountedNode {
        val mountedNode = timingCollector.measureRenderInterval(
            node = node,
            depth = nodeDepth,
            phase = RenderTreeTimingPhase.Binding,
        ) {
            val view = ViewNodeFactory.createView(
                context = context,
                node = node,
                createAndroidView = when (node.type) {
                    NodeType.AndroidView -> {
                        val factory = node.requireSpec<AndroidViewNodeProps>().factory
                        { rawContext ->
                            node.runAndroidViewOperation(AndroidViewOperation.Factory) {
                                factory(rawContext, node.environment)
                            }
                        }
                    }
                    else -> null
                },
            )
            val mounted = MountedNode(
                vnode = node,
                view = view,
            )
            ViewNodeToolingRegistry.bind(
                view = view,
                node = node,
            )
            transaction.insertedNodes += mounted
            ViewModifierApplier.cacheOriginalBackground(view)
            ViewModifierApplier.cacheOriginalForeground(view)
            bindView(
                view = view,
                node = node,
                defaultRippleColor = defaultRippleColor,
                resolved = resolved,
                bindingMode = NodeBindingMode.Deferred,
            )?.let(transaction.commitEffects::add)
            scheduleAndroidViewCommit(
                transaction = transaction,
                view = view,
                node = node,
            )
            mounted
        }
        val children = if (mountedNode.view is ViewGroup) {
            renderChildren(
                resolveChildHost(mountedNode.view as ViewGroup),
                emptyList(),
                node.children,
                node.key,
                node,
            ).mountedNodes
        } else {
            emptyList()
        }
        mountedNode.children = children
        return mountedNode
    }

    private fun preflight(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        timingCollector: RenderTreeTimingCollector?,
        nodeDepth: Int,
    ): List<PreparedPatch> {
        return reconcileResult.patches.map { patch ->
            timingCollector.measureRenderInterval(
                node = patch.timingNode(),
                depth = nodeDepth,
                phase = RenderTreeTimingPhase.Reconciliation,
            ) {
                when (patch) {
                    is InsertPatch -> {
                        val nextNode = patch.nextVNode
                        val resolved = nextNode.modifier.resolve()
                        if (nextNode.type == NodeType.AndroidView) {
                            nextNode.requireSpec<AndroidViewNodeProps>()
                        }
                        PreparedPatch(
                            patch = patch,
                            nextResolved = resolved,
                            layoutParams = ViewLayoutParamsFactory.createLayoutParams(
                                parent = container,
                                node = nextNode,
                                warningTag = warningTag,
                                emittedModifierWarnings = emittedModifierWarnings,
                                resolved = resolved,
                            ),
                            updatesLayoutParams = true,
                        )
                    }

                    is ReusePatch -> {
                        val nextNode = patch.nextVNode
                        val previousNode = patch.payload.vnode
                        val replacesAndroidView = previousNode.type == NodeType.AndroidView &&
                            nextNode.type == NodeType.AndroidView &&
                            previousNode.requireSpec<AndroidViewNodeProps>().constructionIdentity !=
                            nextNode.requireSpec<AndroidViewNodeProps>().constructionIdentity
                        val bindingPlan = if (patch.payload.requiresCrossOwnerRebind) {
                            NodeBindingPlan.Rebind
                        } else {
                            NodeBindingDiffer.plan(
                                previous = previousNode,
                                next = nextNode,
                            )
                        }
                        if (nextNode.type == NodeType.AndroidView) {
                            nextNode.requireSpec<AndroidViewNodeProps>()
                        }
                        val nextResolved = when {
                            bindingPlan == NodeBindingPlan.Rebind -> nextNode.modifier.resolve()
                            bindingPlan == NodeBindingPlan.ModifierOnly -> nextNode.modifier.resolve()
                            bindingPlan is NodeBindingPlan.Patch && bindingPlan.modifierChanged -> {
                                nextNode.modifier.resolve()
                            }

                            else -> null
                        }
                        val updatesLayoutParams = when {
                            bindingPlan == NodeBindingPlan.Rebind -> true
                            bindingPlan == NodeBindingPlan.ModifierOnly -> {
                                layoutModifiersChanged(
                                    previous = previousNode.modifier.resolve(),
                                    next = checkNotNull(nextResolved),
                                )
                            }
                            bindingPlan is NodeBindingPlan.Patch && bindingPlan.modifierChanged -> {
                                layoutModifiersChanged(
                                    previous = previousNode.modifier.resolve(),
                                    next = checkNotNull(nextResolved),
                                )
                            }

                            else -> false
                        }
                        PreparedPatch(
                            patch = patch,
                            bindingPlan = bindingPlan,
                            nextResolved = nextResolved,
                            layoutParams = if (updatesLayoutParams) {
                                ViewLayoutParamsFactory.createLayoutParams(
                                    parent = container,
                                    node = nextNode,
                                    warningTag = warningTag,
                                    emittedModifierWarnings = emittedModifierWarnings,
                                    resolved = checkNotNull(nextResolved),
                                )
                            } else {
                                null
                            },
                            updatesLayoutParams = updatesLayoutParams,
                            replacesAndroidView = replacesAndroidView,
                        )
                    }
                }
            }
        }
    }

    private fun bindView(
        view: View,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers,
        bindingMode: NodeBindingMode = NodeBindingMode.Immediate,
    ): RenderTreeCommitEffect? {
        return ViewModifierApplier.bindView(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
            bindingMode = bindingMode,
        )
    }

    private fun RenderPatch<MountedNode>.timingNode(): VNode = when (this) {
        is InsertPatch -> nextVNode
        is ReusePatch -> nextVNode
    }

    private fun scheduleAndroidViewCommit(
        transaction: RenderTransaction,
        view: View,
        node: VNode,
    ) {
        if (node.type != NodeType.AndroidView) return
        val onCommit = node.requireSpec<AndroidViewNodeProps>().onCommit ?: return
        // Defer onCommit until success so application code never receives a commit signal for rolled-back work.
        // onCommit is deferred until transaction success so business code is not notified after a rolled-back render.
        transaction.commitEffects += RenderTreeCommitEffect(
            operation = AndroidViewOperation.Commit,
            nodeKey = node.key,
            commit = {
                node.runAndroidViewOperation(AndroidViewOperation.Commit) {
                    onCommit(view, node.environment)
                }
            },
        )
    }

    private fun MountedNode.androidViewConstructionGeneration(): Long {
        if (vnode.type != NodeType.AndroidView) return 0L
        return (view.getTag(R.id.viewcompose_android_view_construction_generation) as? Long) ?: 0L
    }

    private fun MountedNode.androidViewDiagnosticDetail(replacement: Boolean = false): String? {
        if (vnode.type != NodeType.AndroidView) return null
        val spec = vnode.requireSpec<AndroidViewNodeProps>()
        val adapterName = spec.adapterName.take(MAX_ANDROID_VIEW_ADAPTER_NAME_LENGTH)
        val reuse = if (spec.onReset == null) "Never" else "Resettable"
        return "AndroidView(adapter=$adapterName,generation=${androidViewConstructionGeneration()}," +
            "reuse=$reuse,replacement=$replacement)"
    }

    private fun captureNode(
        transaction: RenderTransaction,
        mountedNode: MountedNode,
    ) {
        // Capture each node only once so rollback always returns to its transaction-start state.
        // Capture each node only once per transaction so rollback returns to the transaction start state.
        transaction.mountedCheckpoints.getOrPut(mountedNode) {
            MountedCheckpoint(
                mountedNode = mountedNode,
                vnode = mountedNode.vnode,
                children = mountedNode.children.toList(),
                layoutParams = mountedNode.view.layoutParams,
                disposed = mountedNode.disposed,
            )
        }
    }

    private fun captureContainer(
        transaction: RenderTransaction,
        target: ViewGroup,
    ) {
        val container = resolveChildHost(target)
        // Capture a ChildHostViewGroup's actual child host separately or restoration targets the outer wrapper.
        // ChildHostViewGroup's actual child host must be captured or order restoration would target the shell.
        transaction.containerCheckpoints.getOrPut(container) {
            ContainerCheckpoint(
                container = container,
                children = List(container.childCount, container::getChildAt),
            )
        }
    }

    private fun moveViewToIndex(
        container: ViewGroup,
        view: View,
        targetIndex: Int,
    ) {
        val currentIndex = container.indexOfChild(view)
        if (currentIndex == -1 || currentIndex == targetIndex) {
            return
        }
        container.removeViewAt(currentIndex)
        container.addView(
            view,
            targetIndex.coerceAtMost(container.childCount),
        )
        DecorationChildDrawingOrder.invalidate(container)
    }

    private fun NodeBindingPlan.toPatchOperation(): RenderPatchOperation {
        return when (this) {
            NodeBindingPlan.Rebind -> RenderPatchOperation.Rebind
            NodeBindingPlan.ModifierOnly -> RenderPatchOperation.Patch
            NodeBindingPlan.SkipSelfOnly -> RenderPatchOperation.SkipSelf
            NodeBindingPlan.SkipSubtree -> RenderPatchOperation.SkipSubtree
            is NodeBindingPlan.Patch -> RenderPatchOperation.Patch
        }
    }

    private fun RenderTransaction.recordPatch(record: RenderPatchRecord) {
        if (patchRecords.size < MAX_PATCH_RECORDS) {
            patchRecords += record
        }
    }

    private fun resolveChildHost(container: ViewGroup): ViewGroup {
        return (container as? ChildHostViewGroup)?.childHost ?: container
    }

    private const val MAX_PATCH_RECORDS = 5_000
    private const val MAX_ANDROID_VIEW_ADAPTER_NAME_LENGTH = 160
}
