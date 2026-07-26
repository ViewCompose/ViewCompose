package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.ModifierElement
import com.viewcompose.ui.modifier.OffsetModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps

internal object AnimatedSizeNodeWrapper {
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
                node.copy(children = wrappedChildren)
            }
        }
        val animateElement = node.modifier.elements
            .asReversed()
            .filterIsInstance<AnimateContentSizeModifierElement>()
            .firstOrNull()
            ?: return if (wrappedChildren === node.children) {
                node
            } else {
                node.copy(children = wrappedChildren)
            }
        val withoutAnimate = node.modifier.elements.filterNot { it is AnimateContentSizeModifierElement }
        val (hostElements, childElements) = splitHostAndChildElements(withoutAnimate)
        val wrappedChild = node.copy(
            modifier = childElements.toModifier(),
            children = wrappedChildren,
        )
        return VNode(
            type = NodeType.AnimatedSizeHost,
            key = node.key?.let(::AnimatedSizeHostKey),
            spec = AnimatedSizeHostNodeProps(
                animationSpec = animateElement.animationSpec,
            ),
            modifier = hostElements.toModifier(),
            children = listOf(wrappedChild),
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
            this is SizeModifierElement ||
            this is WidthModifierElement ||
            this is HeightModifierElement ||
            this is WeightModifierElement ||
            this is BoxAlignModifierElement ||
            this is HorizontalAlignModifierElement ||
            this is VerticalAlignModifierElement ||
            this is OffsetModifierElement ||
            this is ZIndexModifierElement
    }

    private fun List<ModifierElement>.toModifier(): Modifier {
        var modifier: Modifier = Modifier
        forEach { element ->
            modifier = modifier.then(element)
        }
        return modifier
    }

    private data class AnimatedSizeHostKey(
        val childKey: Any,
    )
}
