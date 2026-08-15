package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.AspectRatioModifierElement
import com.viewcompose.ui.modifier.MaxHeightModifierElement
import com.viewcompose.ui.modifier.MaxWidthModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.ModifierElement
import com.viewcompose.ui.modifier.OffsetModifierElement
import com.viewcompose.ui.modifier.RelativeMarginModifierElement
import com.viewcompose.ui.modifier.RelativeOffsetModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.tooling.UiNodeTooling

/**
 * Lifts an animateContentSize modifier into an explicit AnimatedSizeHost node.
 * Promotes animateContentSize modifiers into explicit AnimatedSizeHost nodes.
 *
 * This lets ordinary container measurement and layout drive size animation while preserving the original DSL VNode semantics.
 * This lets the renderer host size animation through normal container measure/layout while preserving the original DSL semantics.
 */
internal object AnimatedSizeNodeWrapper {
    /**
     * Wraps a complete VNode subtree and returns the original list when unchanged to preserve referential-equality optimization.
     * Wraps a VNode subtree and returns the original list when unchanged to preserve referential optimizations.
     */
    fun wrapTree(nodes: List<VNode>): List<VNode> {
        var changedNodes: MutableList<VNode>? = null
        nodes.forEachIndexed { index, node ->
            val wrapped = wrapNode(node)
            if (wrapped !== node && changedNodes == null) {
                changedNodes = ArrayList<VNode>(nodes.size).also { result ->
                    repeat(index) { previousIndex ->
                        result += nodes[previousIndex]
                    }
                }
            }
            changedNodes?.add(wrapped)
        }
        return changedNodes ?: nodes
    }

    private fun wrapNode(node: VNode): VNode {
        val wrappedChildren = wrapTree(node.children)
        if (node.type == NodeType.AnimatedSizeHost) {
            return if (wrappedChildren === node.children) {
                node
            } else {
                UiNodeTooling.inheritCopy(
                    target = node.copy(children = wrappedChildren),
                    source = node,
                )
            }
        }
        val animateElement = node.modifier.elements
            .asReversed()
            .filterIsInstance<AnimateContentSizeModifierElement>()
            .firstOrNull()
            ?: return if (wrappedChildren === node.children) {
                node
            } else {
                UiNodeTooling.inheritCopy(
                    target = node.copy(children = wrappedChildren),
                    source = node,
                )
            }
        val withoutAnimate = node.modifier.elements.filterNot { it is AnimateContentSizeModifierElement }
        val (hostElements, childElements) = splitHostAndChildElements(withoutAnimate)
        // Size, margin, and parent-data modifiers stay on the outer host; content modifiers stay on the original node.
        // Layout modifiers such as size, margin, and parent data must stay on the outer host; content modifiers stay on the original node.
        val wrappedChild = UiNodeTooling.inheritCopy(
            target = node.copy(
                modifier = childElements.toModifier(),
                children = wrappedChildren,
            ),
            source = node,
        )
        return UiNodeTooling.inheritSynthetic(
            target = VNode(
                type = NodeType.AnimatedSizeHost,
                key = node.key?.let(::AnimatedSizeHostKey),
                spec = AnimatedSizeHostNodeProps(
                    animationSpec = animateElement.animationSpec,
                ),
                modifier = hostElements.toModifier(),
                children = listOf(wrappedChild),
            ),
            source = node,
            discriminator = "animated-size",
        )
    }

    private fun splitHostAndChildElements(elements: List<ModifierElement>): Pair<List<ModifierElement>, List<ModifierElement>> {
        val host = mutableListOf<ModifierElement>()
        val child = mutableListOf<ModifierElement>()
        elements.forEach { element ->
            if (element.isHostLayoutElement()) {
                host += element
            } else {
                child += element
            }
        }
        return host to child
    }

    private fun ModifierElement.isHostLayoutElement(): Boolean {
        return this is MarginModifierElement ||
            this is RelativeMarginModifierElement ||
            this is SizeModifierElement ||
            this is WidthModifierElement ||
            this is HeightModifierElement ||
            this is MaxWidthModifierElement ||
            this is MaxHeightModifierElement ||
            this is AspectRatioModifierElement ||
            this is WeightModifierElement ||
            this is BoxAlignModifierElement ||
            this is HorizontalAlignModifierElement ||
            this is VerticalAlignModifierElement ||
            this is OffsetModifierElement ||
            this is RelativeOffsetModifierElement ||
            this is ZIndexModifierElement
    }

    private fun List<ModifierElement>.toModifier(): Modifier {
        var modifier: Modifier = Modifier
        forEach { element ->
            modifier = modifier.then(element)
        }
        return modifier
    }

    /**
     * Derives a host key so the wrapper and wrapped child never share identity.
     * Derived host key preventing the wrapper node from sharing the same key as the wrapped child.
     */
    private data class AnimatedSizeHostKey(
        val childKey: Any,
    )
}
