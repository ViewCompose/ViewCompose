package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.collection.TabRowTab
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NodeSpec
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps

/**
 * Compares previous and next VNodes and chooses the smallest valid binding strategy.
 * Compares previous/next VNodes and chooses the smallest binding strategy.
 */
internal object NodeBindingDiffer {
    private val patchFactories by lazy { NodeBinderDescriptors.patchFactoriesBySpec() }

    /**
     * Builds the binding plan for a reused node.
     * Builds the binding plan for a reused node.
     */
    fun plan(
        previous: VNode,
        next: VNode,
    ): NodeBindingPlan {
        if (previous === next) {
            return NodeBindingPlan.SkipSubtree
        }
        if (previous.type != next.type) {
            return NodeBindingPlan.Rebind
        }
        if (previous.environment != next.environment) {
            // Environment affects every native boundary: layout units, text scaling, locale and direction.
            // Treat it as a full rebind even when spec/modifier objects are otherwise identical.
            return NodeBindingPlan.Rebind
        }
        val modifierChanged = previous.modifier != next.modifier
        val prevSpec = previous.spec
        val nextSpec = next.spec
        val sessionContentChanged = hasSessionSubmissionChange(prevSpec, nextSpec)
        val imageLoaderChanged = hasImageLoaderIdentityChange(prevSpec, nextSpec)
        if (prevSpec == nextSpec && !sessionContentChanged && !imageLoaderChanged) {
            return if (modifierChanged) {
                NodeBindingPlan.Rebind
            } else {
                if (previous.children.hasSameElementReferences(next.children)) {
                    NodeBindingPlan.SkipSubtree
                } else {
                    NodeBindingPlan.SkipSelfOnly
                }
            }
        }
        if (prevSpec::class != nextSpec::class) {
            return NodeBindingPlan.Rebind
        }
        val containerInteractionChanged = when {
            prevSpec is BoxNodeProps && nextSpec is BoxNodeProps -> {
                prevSpec.rippleColor != nextSpec.rippleColor ||
                    prevSpec.stateLayerColors != nextSpec.stateLayerColors
            }
            prevSpec is RowNodeProps && nextSpec is RowNodeProps -> {
                prevSpec.rippleColor != nextSpec.rippleColor ||
                    prevSpec.stateLayerColors != nextSpec.stateLayerColors
            }
            else -> false
        }
        if (containerInteractionChanged) {
            // Container ripple participates in style binding through NodeSpec, so changes require modifier and style rebinding.
            // Container ripple is resolved from NodeSpec, so this change must re-run modifier/style binding.
            return NodeBindingPlan.Rebind
        }
        val factory = patchFactories[prevSpec::class]
        if (factory != null) {
            return NodeBindingPlan.Patch(
                patch = factory(prevSpec, nextSpec),
                modifierChanged = modifierChanged,
            )
        }
        return NodeBindingPlan.Rebind
    }

    private fun hasImageLoaderIdentityChange(
        previous: NodeSpec,
        next: NodeSpec,
    ): Boolean {
        return previous is ImageNodeSpec &&
            next is ImageNodeSpec &&
            previous.imageLoader !== next.imageLoader
    }

    private fun hasSessionSubmissionChange(
        previous: NodeSpec,
        next: NodeSpec,
    ): Boolean {
        // A newly built immutable item snapshot is the submission signal. Callback identity is an
        // implementation detail and must not decide whether a child session renders.
        return when {
            previous is LazyColumnNodeProps && next is LazyColumnNodeProps -> previous.items.hasNewSnapshots(next.items)
            previous is LazyRowNodeProps && next is LazyRowNodeProps -> previous.items.hasNewSnapshots(next.items)
            previous is LazyVerticalGridNodeProps && next is LazyVerticalGridNodeProps -> previous.items.hasNewSnapshots(next.items)
            previous is HorizontalPagerNodeProps && next is HorizontalPagerNodeProps -> previous.pages.hasNewSnapshots(next.pages)
            previous is VerticalPagerNodeProps && next is VerticalPagerNodeProps -> previous.pages.hasNewSnapshots(next.pages)
            previous is TabRowNodeProps && next is TabRowNodeProps -> previous.tabs.hasNewTabSnapshots(next.tabs)
            else -> false
        }
    }

    private fun List<LazyListItem>.hasNewSnapshots(next: List<LazyListItem>): Boolean {
        if (size != next.size) return true
        for (index in indices) {
            if (this[index] !== next[index]) return true
        }
        return false
    }

    private fun List<TabRowTab>.hasNewTabSnapshots(next: List<TabRowTab>): Boolean {
        if (size != next.size) return true
        for (index in indices) {
            if (this[index].item !== next[index].item) return true
        }
        return false
    }

    private fun List<VNode>.hasSameElementReferences(next: List<VNode>): Boolean {
        // Value equality can hide changed nested session callbacks. Only composer-produced
        // reference reuse proves that reconciling the complete child subtree is unnecessary.
        if (size != next.size) return false
        return indices.all { index -> this[index] === next[index] }
    }
}
