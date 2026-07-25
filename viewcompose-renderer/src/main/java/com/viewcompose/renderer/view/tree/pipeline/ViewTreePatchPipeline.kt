package com.viewcompose.renderer.view.tree

import android.content.Context
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

    fun execute(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        defaultRippleColor: Int,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>) -> RenderTreeResult,
    ): ExecutionResult {
        val mountContainer = resolveChildHost(container)
        var stats = RenderStats()
        val nextMounted = mutableListOf<MountedNode>()
        reconcileResult.patches.forEach { patch ->
            val patchResult = applyPatch(
                container = mountContainer,
                patch = patch,
                defaultRippleColor = defaultRippleColor,
                warningTag = warningTag,
                emittedModifierWarnings = emittedModifierWarnings,
                renderChildren = renderChildren,
            )
            stats = stats.mergeWith(patchResult.stats)
            nextMounted += patchResult.mountedNode
        }
        reconcileResult.removals.forEach { removal ->
            applyRemoval(
                container = mountContainer,
                removal = removal,
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
    ) {
        ViewTreeDisposer.disposeMountedNode(removal.payload)
        container.removeView(removal.payload.view)
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
        return MountedNode(
            vnode = node,
            view = view,
            children = children,
        )
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
