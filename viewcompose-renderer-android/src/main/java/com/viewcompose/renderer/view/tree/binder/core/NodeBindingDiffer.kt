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
        val sessionContentChanged = hasSessionBackedContentChange(prevSpec, nextSpec)
        val imageLoaderChanged = hasImageLoaderIdentityChange(prevSpec, nextSpec)
        if (prevSpec == nextSpec && !sessionContentChanged && !imageLoaderChanged) {
            return if (modifierChanged) {
                NodeBindingPlan.Rebind
            } else {
                if (previous.children == next.children) {
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

    private fun hasSessionBackedContentChange(
        previous: NodeSpec,
        next: NodeSpec,
    ): Boolean {
        // Lazy, pager, and tab content retains session factories and updaters; reference changes require updates even when equal.
        // lazy/pager/tab content owns session factories/updaters; reference changes require updates even when equals matches.
        return when {
            previous is LazyColumnNodeProps && next is LazyColumnNodeProps -> previous.items.hasSessionIdentityChange(next.items)
            previous is LazyRowNodeProps && next is LazyRowNodeProps -> previous.items.hasSessionIdentityChange(next.items)
            previous is LazyVerticalGridNodeProps && next is LazyVerticalGridNodeProps -> previous.items.hasSessionIdentityChange(next.items)
            previous is HorizontalPagerNodeProps && next is HorizontalPagerNodeProps -> previous.pages.hasSessionIdentityChange(next.pages)
            previous is VerticalPagerNodeProps && next is VerticalPagerNodeProps -> previous.pages.hasSessionIdentityChange(next.pages)
            previous is TabRowNodeProps && next is TabRowNodeProps -> previous.tabs.hasTabSessionIdentityChange(next.tabs)
            else -> false
        }
    }

    private fun List<LazyListItem>.hasSessionIdentityChange(next: List<LazyListItem>): Boolean {
        if (size != next.size) return true
        for (index in indices) {
            val previous = this[index]
            val current = next[index]
            if (previous.sessionFactory !== current.sessionFactory) return true
            if (previous.sessionUpdater !== current.sessionUpdater) return true
        }
        return false
    }

    private fun List<TabRowTab>.hasTabSessionIdentityChange(next: List<TabRowTab>): Boolean {
        if (size != next.size) return true
        for (index in indices) {
            val previous = this[index].item
            val current = next[index].item
            if (previous.sessionFactory !== current.sessionFactory) return true
            if (previous.sessionUpdater !== current.sessionUpdater) return true
        }
        return false
    }
}
