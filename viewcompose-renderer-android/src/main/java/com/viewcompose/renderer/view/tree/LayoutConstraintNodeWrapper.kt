package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.AspectRatioModifierElement
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
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
import com.viewcompose.ui.node.spec.LayoutConstraintHostNodeProps
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.unit.UiDimension

/** Promotes portable maximum-size and aspect-ratio modifiers into one measurement host. */
internal object LayoutConstraintNodeWrapper {
    fun wrapTree(nodes: List<VNode>): List<VNode> {
        var changed: MutableList<VNode>? = null
        nodes.forEachIndexed { index, node ->
            val wrapped = wrapNode(node)
            if (wrapped !== node && changed == null) {
                changed = ArrayList<VNode>(nodes.size).also { result ->
                    repeat(index) { previousIndex -> result += nodes[previousIndex] }
                }
            }
            changed?.add(wrapped)
        }
        return changed ?: nodes
    }

    private fun wrapNode(node: VNode): VNode {
        val wrappedChildren = wrapTree(node.children)
        if (node.type == NodeType.LayoutConstraintHost) {
            return copyWithChildren(node, wrappedChildren)
        }
        val resolved = resolveConstraints(node.modifier.elements) ?: return copyWithChildren(node, wrappedChildren)
        validateConstraints(resolved)

        val hostElements = mutableListOf<ModifierElement>()
        val childElements = mutableListOf<ModifierElement>()
        node.modifier.elements.forEach { element ->
            when {
                element is MaxWidthModifierElement ||
                    element is MaxHeightModifierElement ||
                    element is AspectRatioModifierElement ||
                    element is SizeModifierElement ||
                    element is WidthModifierElement ||
                    element is HeightModifierElement -> Unit
                element.isHostLayoutElement() -> hostElements += element
                else -> childElements += element
            }
        }
        addExactDimensions(hostElements, resolved.width, resolved.height)

        val child = UiNodeTooling.inheritCopy(
            target = node.copy(
                modifier = childElements.toModifier(),
                children = wrappedChildren,
            ),
            source = node,
        )
        return UiNodeTooling.inheritSynthetic(
            target = VNode(
                type = NodeType.LayoutConstraintHost,
                key = node.key?.let(::LayoutConstraintHostKey),
                spec = LayoutConstraintHostNodeProps(
                    maxWidth = resolved.maxWidth?.maxWidth,
                    maxHeight = resolved.maxHeight?.maxHeight,
                    aspectRatio = resolved.aspectRatio?.ratio,
                    matchHeightConstraintsFirst =
                        resolved.aspectRatio?.matchHeightConstraintsFirst ?: false,
                    fillWidth = resolved.width == UiDimension.MatchParent,
                    fillHeight = resolved.height == UiDimension.MatchParent,
                ),
                modifier = hostElements.toModifier(),
                children = listOf(child),
                environment = node.environment,
            ),
            source = node,
            discriminator = "layout-constraint",
        )
    }

    private fun copyWithChildren(node: VNode, children: List<VNode>): VNode =
        if (children === node.children) {
            node
        } else {
            UiNodeTooling.inheritCopy(node.copy(children = children), node)
        }

    private fun resolveConstraints(elements: List<ModifierElement>): ResolvedConstraints? {
        var size: SizeModifierElement? = null
        var width: WidthModifierElement? = null
        var height: HeightModifierElement? = null
        var maxWidth: MaxWidthModifierElement? = null
        var maxHeight: MaxHeightModifierElement? = null
        var aspectRatio: AspectRatioModifierElement? = null
        elements.forEach { element ->
            when (element) {
                is SizeModifierElement -> size = element
                is WidthModifierElement -> width = element
                is HeightModifierElement -> height = element
                is MaxWidthModifierElement -> maxWidth = element
                is MaxHeightModifierElement -> maxHeight = element
                is AspectRatioModifierElement -> aspectRatio = element
                else -> Unit
            }
        }
        if (maxWidth == null && maxHeight == null && aspectRatio == null) return null
        return ResolvedConstraints(
            width = width?.width ?: size?.width,
            height = height?.height ?: size?.height,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            aspectRatio = aspectRatio,
            minWidth = elements.filterIsInstance<MinWidthModifierElement>().lastOrNull(),
            minHeight = elements.filterIsInstance<MinHeightModifierElement>().lastOrNull(),
        )
    }

    private fun validateConstraints(constraints: ResolvedConstraints) {
        val maxWidth = constraints.maxWidth?.maxWidth?.value
        val maxHeight = constraints.maxHeight?.maxHeight?.value
        val minWidth = constraints.minWidth?.minWidth?.value
        val minHeight = constraints.minHeight?.minHeight?.value
        require(maxWidth == null || minWidth == null || minWidth <= maxWidth) {
            "minWidth cannot exceed maxWidth on the same node."
        }
        require(maxHeight == null || minHeight == null || minHeight <= maxHeight) {
            "minHeight cannot exceed maxHeight on the same node."
        }
        val exactWidth = (constraints.width as? UiDimension.Exact)?.value?.value
        val exactHeight = (constraints.height as? UiDimension.Exact)?.value?.value
        require(maxWidth == null || exactWidth == null || exactWidth <= maxWidth) {
            "Exact width cannot exceed maxWidth on the same node."
        }
        require(maxHeight == null || exactHeight == null || exactHeight <= maxHeight) {
            "Exact height cannot exceed maxHeight on the same node."
        }
    }

    private fun addExactDimensions(
        target: MutableList<ModifierElement>,
        width: UiDimension?,
        height: UiDimension?,
    ) {
        val exactWidth = width as? UiDimension.Exact
        val exactHeight = height as? UiDimension.Exact
        when {
            exactWidth != null && exactHeight != null -> target += SizeModifierElement(exactWidth, exactHeight)
            exactWidth != null -> target += WidthModifierElement(exactWidth)
            exactHeight != null -> target += HeightModifierElement(exactHeight)
        }
    }

    private fun ModifierElement.isHostLayoutElement(): Boolean =
        this is AnimateBoundsModifierElement ||
            this is AnimateContentSizeModifierElement ||
            this is MarginModifierElement ||
            this is RelativeMarginModifierElement ||
            this is MinWidthModifierElement ||
            this is MinHeightModifierElement ||
            this is WeightModifierElement ||
            this is BoxAlignModifierElement ||
            this is HorizontalAlignModifierElement ||
            this is VerticalAlignModifierElement ||
            this is OffsetModifierElement ||
            this is RelativeOffsetModifierElement ||
            this is ZIndexModifierElement ||
            this is VisibilityModifierElement ||
            this is LayoutIdModifierElement ||
            this is ConstraintModifierElement

    private fun List<ModifierElement>.toModifier(): Modifier {
        var result: Modifier = Modifier
        forEach { element -> result = result.then(element) }
        return result
    }

    private data class ResolvedConstraints(
        val width: UiDimension?,
        val height: UiDimension?,
        val maxWidth: MaxWidthModifierElement?,
        val maxHeight: MaxHeightModifierElement?,
        val aspectRatio: AspectRatioModifierElement?,
        val minWidth: MinWidthModifierElement?,
        val minHeight: MinHeightModifierElement?,
    )

    private data class LayoutConstraintHostKey(val childKey: Any)
}
