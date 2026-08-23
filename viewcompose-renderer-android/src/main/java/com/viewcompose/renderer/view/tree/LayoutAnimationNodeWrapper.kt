package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.AspectRatioModifierElement
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.LayoutIdModifierElement
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.modifier.MaxHeightModifierElement
import com.viewcompose.ui.modifier.MaxWidthModifierElement
import com.viewcompose.ui.modifier.MinHeightModifierElement
import com.viewcompose.ui.modifier.MinWidthModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.ModifierElement
import com.viewcompose.ui.modifier.OffsetModifierElement
import com.viewcompose.ui.modifier.RelativeMarginModifierElement
import com.viewcompose.ui.modifier.RelativeOffsetModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.VisibilityModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedBoundsHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.tooling.UiNodeTooling

/** Promotes one layout-animation owner into an explicit outer host after structural wrapping. */
internal object LayoutAnimationNodeWrapper {
    fun wrapTree(nodes: List<VNode>): List<VNode> {
        var changedNodes: MutableList<VNode>? = null
        nodes.forEachIndexed { index, node ->
            val wrapped = wrapNode(node)
            if (wrapped !== node && changedNodes == null) {
                changedNodes = ArrayList<VNode>(nodes.size).also { result ->
                    repeat(index) { previousIndex -> result += nodes[previousIndex] }
                }
            }
            changedNodes?.add(wrapped)
        }
        return changedNodes ?: nodes
    }

    private fun wrapNode(node: VNode): VNode {
        val wrappedChildren = wrapTree(node.children)
        if (node.type == NodeType.AnimatedSizeHost || node.type == NodeType.AnimatedBoundsHost) {
            return copyWithChildren(node, wrappedChildren)
        }

        val boundsElements = node.modifier.elements.filterIsInstance<AnimateBoundsModifierElement>()
        val sizeElements = node.modifier.elements.filterIsInstance<AnimateContentSizeModifierElement>()
        require(boundsElements.isEmpty() || sizeElements.isEmpty()) {
            "animateBounds and animateContentSize cannot own the same node."
        }
        val boundsElement = boundsElements.lastOrNull()
        val sizeElement = sizeElements.lastOrNull()
        if (boundsElement == null && sizeElement == null) {
            return copyWithChildren(node, wrappedChildren)
        }

        val withoutAnimation = node.modifier.elements.filterNot { element ->
            element is AnimateBoundsModifierElement || element is AnimateContentSizeModifierElement
        }
        val (hostElements, childElements) = splitHostAndChildElements(withoutAnimation)
        val wrappedChild = UiNodeTooling.inheritCopy(
            target = node.copy(
                modifier = childElements.toModifier(),
                children = wrappedChildren,
            ),
            source = node,
        )
        val isBounds = boundsElement != null
        return UiNodeTooling.inheritSynthetic(
            target = VNode(
                type = if (isBounds) NodeType.AnimatedBoundsHost else NodeType.AnimatedSizeHost,
                key = node.key?.let { childKey ->
                    LayoutAnimationHostKey(
                        childKey = childKey,
                        kind = if (isBounds) LayoutAnimationKind.Bounds else LayoutAnimationKind.ContentSize,
                    )
                },
                spec = if (boundsElement != null) {
                    AnimatedBoundsHostNodeProps(animationSpec = boundsElement.animationSpec)
                } else {
                    AnimatedSizeHostNodeProps(animationSpec = checkNotNull(sizeElement).animationSpec)
                },
                modifier = hostElements.toModifier(),
                children = listOf(wrappedChild),
                environment = node.environment,
            ),
            source = node,
            discriminator = if (isBounds) "animated-bounds" else "animated-size",
        )
    }

    private fun copyWithChildren(node: VNode, children: List<VNode>): VNode =
        if (children === node.children) {
            node
        } else {
            UiNodeTooling.inheritCopy(node.copy(children = children), node)
        }

    private fun splitHostAndChildElements(
        elements: List<ModifierElement>,
    ): Pair<List<ModifierElement>, List<ModifierElement>> {
        val host = mutableListOf<ModifierElement>()
        val child = mutableListOf<ModifierElement>()
        elements.forEach { element ->
            if (element.isHostLayoutElement()) host += element else child += element
        }
        return host to child
    }

    private fun ModifierElement.isHostLayoutElement(): Boolean {
        return this is MarginModifierElement ||
            this is RelativeMarginModifierElement ||
            this is SizeModifierElement ||
            this is WidthModifierElement ||
            this is HeightModifierElement ||
            this is MinWidthModifierElement ||
            this is MinHeightModifierElement ||
            this is MaxWidthModifierElement ||
            this is MaxHeightModifierElement ||
            this is AspectRatioModifierElement ||
            this is LayoutIdModifierElement ||
            this is ConstraintModifierElement ||
            this is WeightModifierElement ||
            this is BoxAlignModifierElement ||
            this is HorizontalAlignModifierElement ||
            this is VerticalAlignModifierElement ||
            this is OffsetModifierElement ||
            this is RelativeOffsetModifierElement ||
            this is ZIndexModifierElement ||
            this is VisibilityModifierElement
    }

    private fun List<ModifierElement>.toModifier(): Modifier {
        var modifier: Modifier = Modifier
        forEach { element -> modifier = modifier.then(element) }
        return modifier
    }

    private enum class LayoutAnimationKind {
        ContentSize,
        Bounds,
    }

    private data class LayoutAnimationHostKey(
        val childKey: Any,
        val kind: LayoutAnimationKind,
    )
}
