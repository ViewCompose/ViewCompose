package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
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
    data class ExecutionResult(
        val mountedNodes: List<MountedNode>,
        val stats: RenderStats,
    )

    private data class PatchApplicationResult(
        val mountedNode: MountedNode,
        val stats: RenderStats,
    )

    internal class RenderTransaction internal constructor(
        internal val mountedCheckpoints: List<MountedCheckpoint>,
        internal val containerCheckpoints: List<ContainerCheckpoint>,
    ) {
        internal val insertedNodes = LinkedHashSet<MountedNode>()
        internal val deferredRemovals = LinkedHashSet<MountedNode>()
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

    internal fun beginTransaction(
        container: ViewGroup,
        previous: List<MountedNode>,
    ): RenderTransaction {
        val mountedCheckpoints = mutableListOf<MountedCheckpoint>()
        val containerCheckpoints = LinkedHashMap<ViewGroup, ContainerCheckpoint>()

        fun captureContainer(target: ViewGroup) {
            val childHost = resolveChildHost(target)
            if (containerCheckpoints.containsKey(childHost)) return
            containerCheckpoints[childHost] = ContainerCheckpoint(
                container = childHost,
                children = List(childHost.childCount, childHost::getChildAt),
            )
        }

        fun captureNode(mountedNode: MountedNode) {
            mountedCheckpoints += MountedCheckpoint(
                mountedNode = mountedNode,
                vnode = mountedNode.vnode,
                children = mountedNode.children.toList(),
                layoutParams = mountedNode.view.layoutParams,
                disposed = mountedNode.disposed,
            )
            (mountedNode.view as? ViewGroup)?.let(::captureContainer)
            mountedNode.children.forEach(::captureNode)
        }

        captureContainer(container)
        previous.forEach(::captureNode)
        return RenderTransaction(
            mountedCheckpoints = mountedCheckpoints,
            containerCheckpoints = containerCheckpoints.values.toList(),
        )
    }

    internal fun commitTransaction(
        transaction: RenderTransaction,
        warningTag: String,
    ) {
        transaction.deferredRemovals.forEach { mountedNode ->
            try {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            } catch (error: Throwable) {
                Log.e(
                    warningTag,
                    "Failed to release removed ${mountedNode.vnode.type} node after render commit.",
                    error,
                )
            }
        }
        transaction.deferredRemovals.clear()
        transaction.insertedNodes.clear()
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

        transaction.mountedCheckpoints.forEach { checkpoint ->
            checkpoint.mountedNode.vnode = checkpoint.vnode
            checkpoint.mountedNode.children = checkpoint.children
            checkpoint.mountedNode.disposed = checkpoint.disposed
            checkpoint.mountedNode.view.layoutParams = checkpoint.layoutParams
        }

        transaction.mountedCheckpoints.forEach { checkpoint ->
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

        transaction.containerCheckpoints.asReversed().forEach { checkpoint ->
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
    }

    fun execute(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        defaultRippleColor: Int,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        transaction: RenderTransaction,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
    ): ExecutionResult {
        val mountContainer = resolveChildHost(container)
        preflight(
            container = mountContainer,
            reconcileResult = reconcileResult,
            warningTag = warningTag,
            emittedModifierWarnings = emittedModifierWarnings,
        )
        var stats = RenderStats()
        val nextMounted = mutableListOf<MountedNode>()
        reconcileResult.patches.forEach { patch ->
            val patchResult = applyPatch(
                container = mountContainer,
                patch = patch,
                defaultRippleColor = defaultRippleColor,
                warningTag = warningTag,
                emittedModifierWarnings = emittedModifierWarnings,
                transaction = transaction,
                renderChildren = renderChildren,
            )
            stats = stats.mergeWith(patchResult.stats)
            nextMounted += patchResult.mountedNode
        }
        reconcileResult.removals.forEach { removal ->
            applyRemoval(
                container = mountContainer,
                removal = removal,
                transaction = transaction,
            )
            stats = stats.withRemoval()
        }
        return ExecutionResult(
            mountedNodes = nextMounted,
            stats = stats,
        )
    }

    private fun applyPatch(
        container: ViewGroup,
        patch: RenderPatch<MountedNode>,
        defaultRippleColor: Int,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        transaction: RenderTransaction,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
    ): PatchApplicationResult {
        return when (patch) {
            is InsertPatch -> {
                val resolved = patch.nextVNode.modifier.resolve()
                val mountedNode = mountNode(
                    context = container.context,
                    node = patch.nextVNode,
                    defaultRippleColor = defaultRippleColor,
                    resolved = resolved,
                    transaction = transaction,
                    renderChildren = renderChildren,
                )
                container.addView(
                    mountedNode.view,
                    patch.targetIndex.coerceAtMost(container.childCount),
                    ViewLayoutParamsFactory.createLayoutParams(
                        parent = container,
                        node = patch.nextVNode,
                        warningTag = warningTag,
                        emittedModifierWarnings = emittedModifierWarnings,
                        resolved = resolved,
                    ),
                )
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = RenderStats(inserts = 1),
                )
            }

            is ReusePatch -> {
                val mountedNode = patch.payload
                val nextResolved = patch.nextVNode.modifier.resolve()
                val bindingPlan = NodeBindingDiffer.plan(mountedNode.vnode, patch.nextVNode)
                if (patch.nextVNode.type == NodeType.AndroidView &&
                    mountedNode.vnode.spec != patch.nextVNode.spec
                ) {
                    patch.nextVNode
                        .requireSpec<AndroidViewNodeProps>()
                        .onReset
                        ?.invoke(mountedNode.view)
                }
                when (bindingPlan) {
                    NodeBindingPlan.Rebind -> bindView(
                        view = mountedNode.view,
                        node = patch.nextVNode,
                        defaultRippleColor = defaultRippleColor,
                        resolved = nextResolved,
                    )

                    NodeBindingPlan.SkipSelfOnly,
                    NodeBindingPlan.SkipSubtree,
                    -> Unit
                    is NodeBindingPlan.Patch -> {
                        if (bindingPlan.modifierChanged) {
                            ViewModifierApplier.applyModifier(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                                defaultRippleColor = defaultRippleColor,
                                resolved = nextResolved,
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
                val previousResolved = mountedNode.vnode.modifier.resolve()
                if (bindingPlan is NodeBindingPlan.Rebind ||
                    layoutModifiersChanged(previousResolved, nextResolved)
                ) {
                    mountedNode.view.layoutParams = ViewLayoutParamsFactory.createLayoutParams(
                        parent = container,
                        node = patch.nextVNode,
                        warningTag = warningTag,
                        emittedModifierWarnings = emittedModifierWarnings,
                        resolved = nextResolved,
                    )
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
                moveViewToIndex(
                    container = container,
                    view = mountedNode.view,
                    targetIndex = patch.targetIndex,
                )
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = childResult.stats.withReuse(
                        result = when (bindingPlan) {
                            NodeBindingPlan.Rebind -> ReuseBindingResult.Rebound
                            NodeBindingPlan.SkipSelfOnly -> ReuseBindingResult.Skipped
                            NodeBindingPlan.SkipSubtree -> ReuseBindingResult.SkippedSubtree
                            is NodeBindingPlan.Patch -> ReuseBindingResult.Patched
                        },
                        nodeType = patch.nextVNode.type,
                    ),
                )
            }
        }
    }

    private fun applyRemoval(
        container: ViewGroup,
        removal: RemovePatch<MountedNode>,
        transaction: RenderTransaction,
    ) {
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
            stats = RenderStats(),
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
            stats = RenderStats(),
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
                NodeType.AndroidView -> node.requireSpec<AndroidViewNodeProps>().factory
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
    ) {
        reconcileResult.patches.forEach { patch ->
            val nextNode = when (patch) {
                is InsertPatch -> patch.nextVNode
                is ReusePatch -> patch.nextVNode
            }
            val resolved = nextNode.modifier.resolve()
            ViewLayoutParamsFactory.createLayoutParams(
                parent = container,
                node = nextNode,
                warningTag = warningTag,
                emittedModifierWarnings = emittedModifierWarnings,
                resolved = resolved,
            )
            if (nextNode.type == NodeType.AndroidView) {
                nextNode.requireSpec<AndroidViewNodeProps>()
            }
            if (patch is ReusePatch) {
                NodeBindingDiffer.plan(
                    previous = patch.payload.vnode,
                    next = nextNode,
                )
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
