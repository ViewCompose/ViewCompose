package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.AspectRatioModifierElement
import com.viewcompose.ui.modifier.MaxHeightModifierElement
import com.viewcompose.ui.modifier.MaxWidthModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.modifier.RelativeMarginModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.ModifierElement
import com.viewcompose.ui.modifier.NestedScrollModifierElement
import com.viewcompose.ui.modifier.OffsetModifierElement
import com.viewcompose.ui.modifier.RelativeOffsetModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.tooling.UiNodeTooling

/**
 * Lifts nestedScroll modifiers into explicit NestedScrollHost nodes.
 * Promotes nestedScroll modifiers into explicit NestedScrollHost nodes.
 *
 * Multiple nestedScroll modifiers create nested hosts, preserving modifier order and event-dispatch order.
 * Multiple nestedScroll modifiers generate nested hosts, preserving modifier order and event dispatch chain.
 */
internal object NestedScrollNodeWrapper {
    /**
     * Wraps a complete VNode subtree and returns the original list when unchanged to reduce later diff work.
     * Wraps a VNode subtree and returns the original list when unchanged to reduce later diff cost.
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
        if (node.type == NodeType.NestedScrollHost) {
            return if (wrappedChildren === node.children) {
                node
            } else {
                UiNodeTooling.inheritCopy(
                    target = node.copy(children = wrappedChildren),
                    source = node,
                )
            }
        }
        val nestedElements = node.modifier.elements
            .filterIsInstance<NestedScrollModifierElement>()
        if (nestedElements.isEmpty()) {
            return if (wrappedChildren === node.children) {
                node
            } else {
                UiNodeTooling.inheritCopy(
                    target = node.copy(children = wrappedChildren),
                    source = node,
                )
            }
        }

        val withoutNested = node.modifier.elements
            .filterNot { element -> element is NestedScrollModifierElement }
        val (hostLayoutElements, childElements) = splitHostAndChildElements(withoutNested)
        // The outer host receives parent-layout modifiers while the original inner node retains drawing and interaction modifiers.
        // The outer host inherits parent-layout modifiers, while the inner original node keeps drawing/interaction modifiers.
        var wrapped = UiNodeTooling.inheritCopy(
            target = node.copy(
                modifier = childElements.toModifier(),
                children = wrappedChildren,
            ),
            source = node,
        )
        nestedElements.asReversed().forEachIndexed { reversedIndex, nestedElement ->
            val sourceIndex = nestedElements.lastIndex - reversedIndex
            val isOutermost = sourceIndex == 0
            val hostElements = buildList {
                if (isOutermost) {
                    addAll(hostLayoutElements)
                }
                add(nestedElement)
            }
            wrapped = UiNodeTooling.inheritSynthetic(
                target = VNode(
                    type = NodeType.NestedScrollHost,
                    key = node.key?.let { childKey ->
                        NestedScrollHostKey(
                            childKey = childKey,
                            modifierIndex = sourceIndex,
                        )
                    },
                    spec = EmptyNodeSpec,
                    modifier = hostElements.toModifier(),
                    children = listOf(wrapped),
                    environment = node.environment,
                ),
                source = node,
                discriminator = "nested-scroll-$sourceIndex",
            )
        }
        return wrapped
    }

    private fun splitHostAndChildElements(
        elements: List<ModifierElement>,
    ): Pair<List<ModifierElement>, List<ModifierElement>> {
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
            this is AnimateBoundsModifierElement ||
            this is AnimateContentSizeModifierElement ||
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
     * Derives a host key that keeps multiple nestedScroll modifiers on one child independently reusable.
     * Derived host key supporting stable reuse for multiple nestedScroll modifiers on one child.
     */
    private data class NestedScrollHostKey(
        val childKey: Any,
        val modifierIndex: Int,
    )
}
