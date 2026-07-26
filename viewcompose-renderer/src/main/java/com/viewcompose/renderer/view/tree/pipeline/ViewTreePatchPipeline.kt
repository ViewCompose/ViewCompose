package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.layoutModifiersChanged
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.renderer.reconcile.InsertPatch
import com.viewcompose.renderer.reconcile.ReconcileResult
import com.viewcompose.renderer.reconcile.RemovePatch
import com.viewcompose.renderer.reconcile.RenderPatch
import com.viewcompose.renderer.reconcile.ReusePatch
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.ChildHostViewGroup

internal object ViewTreePatchPipeline {
    private val emptyStats = RenderStats()

    data class ExecutionResult(
        val mountedNodes: List<MountedNode>,
        val stats: RenderStats,
    )

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
    )

    internal class RenderTransaction internal constructor() {
        internal val mountedCheckpoints = LinkedHashMap<MountedNode, MountedCheckpoint>()
        internal val containerCheckpoints = LinkedHashMap<ViewGroup, ContainerCheckpoint>()
        internal val insertedNodes = LinkedHashSet<MountedNode>()
        internal val deferredRemovals = LinkedHashSet<MountedNode>()
        internal val commitEffects = mutableListOf<RenderTreeCommitEffect>()
    }

    internal data class MountedCheckpoint(
        val mountedNode: MountedNode,
        val vnode: VNode,
        val children: List<MountedNode>,
        val layoutParams: ViewGroup.LayoutParams?,
        val disposed: Boolean,
    )

    internal data class ContainerCheckpoint(
        val container: ViewGroup,
        val children: List<View>,
    )

    internal fun beginTransaction(): RenderTransaction = RenderTransaction()

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

        transaction.insertedNodes.toList().asReversed().forEach { mountedNode ->
            bestEffort {
                (mountedNode.view.parent as? ViewGroup)?.removeView(mountedNode.view)
            }
        }

        transaction.mountedCheckpoints.values.forEach { checkpoint ->
            checkpoint.mountedNode.vnode = checkpoint.vnode
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
                        checkpoint.container.addView(
                            child,
                            index.coerceAtMost(checkpoint.container.childCount),
                        )
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
    }

    fun execute(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        defaultRippleColor: Int,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        transaction: RenderTransaction,
        collectStats: Boolean,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
    ): ExecutionResult {
        val mountContainer = resolveChildHost(container)
        val preparedPatches = preflight(
            container = mountContainer,
            reconcileResult = reconcileResult,
            warningTag = warningTag,
            emittedModifierWarnings = emittedModifierWarnings,
        )
        var stats = emptyStats
        val nextMounted = mutableListOf<MountedNode>()
        preparedPatches.forEach { preparedPatch ->
            val patchResult = applyPatch(
                container = mountContainer,
                preparedPatch = preparedPatch,
                defaultRippleColor = defaultRippleColor,
                transaction = transaction,
                collectStats = collectStats,
                renderChildren = renderChildren,
            )
            if (collectStats) {
                stats = stats.mergeWith(patchResult.stats)
            }
            nextMounted += patchResult.mountedNode
        }
        reconcileResult.removals.forEach { removal ->
            applyRemoval(
                container = mountContainer,
                removal = removal,
                transaction = transaction,
            )
            if (collectStats) {
                stats = stats.withRemoval()
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
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
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
                    renderChildren = renderChildren,
                )
                captureContainer(
                    transaction = transaction,
                    target = container,
                )
                container.addView(
                    mountedNode.view,
                    patch.targetIndex.coerceAtMost(container.childCount),
                    checkNotNull(preparedPatch.layoutParams),
                )
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = if (collectStats) RenderStats(inserts = 1) else emptyStats,
                )
            }

            is ReusePatch -> {
                val mountedNode = patch.payload
                val bindingPlan = checkNotNull(preparedPatch.bindingPlan)
                val reusesExactVNode = bindingPlan == NodeBindingPlan.SkipSubtree &&
                    mountedNode.vnode === patch.nextVNode
                val needsMove = container.indexOfChild(mountedNode.view) != patch.targetIndex
                if (reusesExactVNode && !needsMove) {
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
                if (patch.nextVNode.type == NodeType.AndroidView &&
                    mountedNode.vnode.spec != patch.nextVNode.spec
                ) {
                    patch.nextVNode.runAndroidViewOperation(AndroidViewOperation.Reset) {
                        patch.nextVNode
                            .requireSpec<AndroidViewNodeProps>()
                            .onReset
                            ?.invoke(mountedNode.view)
                    }
                }
                when (bindingPlan) {
                    NodeBindingPlan.Rebind -> {
                        bindView(
                            view = mountedNode.view,
                            node = patch.nextVNode,
                            defaultRippleColor = defaultRippleColor,
                            resolved = checkNotNull(preparedPatch.nextResolved),
                        )
                        scheduleAndroidViewCommit(
                            transaction = transaction,
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
                        )
                        if (bindingPlan.modifierChanged) {
                            ModifierInteractionApplier.applyNativeViewConfigs(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                            )
                        }
                    }
                }
                if (preparedPatch.updatesLayoutParams) {
                    mountedNode.view.layoutParams = checkNotNull(preparedPatch.layoutParams)
                    (container as? DeclarativeConstraintLayout)?.requestConstraintRebuild()
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
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = if (collectStats) {
                        childResult.stats.withReuse(
                            result = when (bindingPlan) {
                                NodeBindingPlan.Rebind -> ReuseBindingResult.Rebound
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
        transaction.deferredRemovals += removal.payload
    }

    private fun reconcileChildren(
        view: View,
        previousChildren: List<MountedNode>,
        node: VNode,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
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
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
    ): MountedNode {
        val view = ViewNodeFactory.createView(
            context = context,
            node = node,
            createAndroidView = when (node.type) {
                NodeType.AndroidView -> {
                    val factory = node.requireSpec<AndroidViewNodeProps>().factory
                    { rawContext ->
                        node.runAndroidViewOperation(AndroidViewOperation.Factory) {
                            factory(rawContext)
                        }
                    }
                }
                else -> null
            },
        )
        val mountedNode = MountedNode(
            vnode = node,
            view = view,
        )
        transaction.insertedNodes += mountedNode
        ViewModifierApplier.cacheOriginalBackground(view)
        ViewModifierApplier.cacheOriginalForeground(view)
        bindView(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
        )
        scheduleAndroidViewCommit(
            transaction = transaction,
            view = view,
            node = node,
        )
        val children = if (view is ViewGroup) {
            renderChildren(
                resolveChildHost(view),
                emptyList(),
                node.children,
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
    ): List<PreparedPatch> {
        return reconcileResult.patches.map { patch ->
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
                    val bindingPlan = NodeBindingDiffer.plan(
                        previous = previousNode,
                        next = nextNode,
                    )
                    if (nextNode.type == NodeType.AndroidView) {
                        nextNode.requireSpec<AndroidViewNodeProps>()
                    }
                    val nextResolved = when {
                        bindingPlan == NodeBindingPlan.Rebind -> nextNode.modifier.resolve()
                        bindingPlan is NodeBindingPlan.Patch && bindingPlan.modifierChanged -> {
                            nextNode.modifier.resolve()
                        }

                        else -> null
                    }
                    val updatesLayoutParams = when {
                        bindingPlan == NodeBindingPlan.Rebind -> true
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
                    )
                }
            }
        }
    }

    private fun bindView(
        view: View,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers,
    ) {
        ViewModifierApplier.bindView(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
        )
    }

    private fun scheduleAndroidViewCommit(
        transaction: RenderTransaction,
        view: View,
        node: VNode,
    ) {
        if (node.type != NodeType.AndroidView) return
        val onCommit = node.requireSpec<AndroidViewNodeProps>().onCommit ?: return
        transaction.commitEffects += RenderTreeCommitEffect(
            operation = AndroidViewOperation.Commit,
            nodeKey = node.key,
            commit = {
                node.runAndroidViewOperation(AndroidViewOperation.Commit) {
                    onCommit(view)
                }
            },
        )
    }

    private fun captureNode(
        transaction: RenderTransaction,
        mountedNode: MountedNode,
    ) {
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
    }

    private fun resolveChildHost(container: ViewGroup): ViewGroup {
        return (container as? ChildHostViewGroup)?.childHost ?: container
    }
}
