package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.NodeSpec
import com.viewcompose.ui.node.spec.RowNodeProps

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
        val imageLoaderChanged = hasImageLoaderIdentityChange(prevSpec, nextSpec)
        if (prevSpec == nextSpec && !imageLoaderChanged) {
            return if (modifierChanged) {
                NodeBindingPlan.ModifierOnly
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

    private fun List<VNode>.hasSameElementReferences(next: List<VNode>): Boolean {
        // Value equality can hide changed nested session callbacks. Only composer-produced
        // reference reuse proves that reconciling the complete child subtree is unnecessary.
        if (size != next.size) return false
        return indices.all { index -> this[index] === next[index] }
    }
}
