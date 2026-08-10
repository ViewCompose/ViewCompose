package com.viewcompose.renderer.view.tree

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.layout.LayoutParamDefaultsResolver
import com.viewcompose.renderer.layout.ModifierParentDataValidator
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.resolveLayoutDimension
import com.viewcompose.renderer.view.roundToPx

/**
 * Converts VNode modifiers and specs into Android LayoutParams accepted by the parent.
 * Converts VNode modifier/spec data into Android LayoutParams accepted by the parent container.
 *
 * Parent containers define different default dimensions and parent-data semantics, so conversion is parent-aware.
 * Default size and parent-data semantics differ by parent container, so conversion must use the parent type as context.
 */
internal object ViewLayoutParamsFactory {
    /**
     * Creates LayoutParams for a node under the current parent.
     * Creates LayoutParams for the node under the current parent.
     */
    fun createLayoutParams(
        parent: ViewGroup,
        node: VNode,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        resolved: ResolvedModifiers = node.modifier.resolve(),
    ): ViewGroup.LayoutParams {
        emitModifierWarnings(
            parent = parent,
            node = node,
            warningTag = warningTag,
            emittedModifierWarnings = emittedModifierWarnings,
        )
        val boxAlign = resolved.boxAlign
        val margin = resolved.margin
        val size = resolved.size
        val widthModifier = resolved.width
        val heightModifier = resolved.height
        val weight = resolved.weight
        val horizontalAlign = resolved.horizontalAlign
        val verticalAlign = resolved.verticalAlign
        val constraintSpec = resolved.constraint?.constraint
        // FlowRow and FlowColumn reuse LinearLayout-style defaults, with orientation inferred from the container.
        // FlowRow/FlowColumn reuse LinearLayout-style defaults, with orientation inferred from container type.
        val useLinearLikeDefaults = parent is DeclarativeLinearLayout ||
            parent is DeclarativeFlowRowLayout ||
            parent is DeclarativeFlowColumnLayout
        val linearLikeOrientation = when (parent) {
            is DeclarativeLinearLayout -> parent.orientation
            is DeclarativeFlowRowLayout -> LinearLayout.HORIZONTAL
            is DeclarativeFlowColumnLayout -> LinearLayout.VERTICAL
            else -> null
        }
        val defaultWidth = if (node.type == NodeType.Divider) {
            defaultDividerWidth(parent, node)
        } else {
            LayoutParamDefaultsResolver.defaultWidth(
                nodeType = node.type,
                useLinearLikeDefaults = useLinearLikeDefaults,
                linearOrientation = linearLikeOrientation,
            )
        }
        val defaultHeight = if (node.type == NodeType.Divider) {
            defaultDividerHeight(parent, node)
        } else {
            LayoutParamDefaultsResolver.defaultHeight(
                nodeType = node.type,
                useLinearLikeDefaults = useLinearLikeDefaults,
                linearOrientation = linearLikeOrientation,
            )
        }
        val width = constraintSpec?.width?.toLayoutParamValue(node)
            ?: widthModifier?.width?.let(node.environment::resolveLayoutDimension)
            ?: size?.width?.let(node.environment::resolveLayoutDimension)
            ?: defaultWidth
        val height = constraintSpec?.height?.toLayoutParamValue(node)
            ?: heightModifier?.height?.let(node.environment::resolveLayoutDimension)
            ?: size?.height?.let(node.environment::resolveLayoutDimension)
            ?: defaultHeight
        return when (parent) {
            is DeclarativeLinearLayout -> {
                // A weighted LinearLayout child defaults to zero on its main axis, matching Android weight allocation.
                // In LinearLayout, weighted main-axis size defaults to 0 to match Android weight allocation.
                val resolvedWidth = if (
                    weight != null &&
                    parent.orientation == LinearLayout.HORIZONTAL &&
                    widthModifier == null &&
                    size?.width == null
                ) {
                    0
                } else {
                    width
                }
                val resolvedHeight = if (
                    weight != null &&
                    parent.orientation == LinearLayout.VERTICAL &&
                    heightModifier == null &&
                    size?.height == null
                ) {
                    0
                } else {
                    height
                }
                android.widget.LinearLayout.LayoutParams(resolvedWidth, resolvedHeight).applyLayoutParams(
                    margin = margin,
                    node = node,
                ) {
                    this.weight = weight?.weight ?: 0f
                    gravity = when (parent.orientation) {
                        LinearLayout.HORIZONTAL -> verticalAlign?.alignment?.toGravity() ?: -1
                        else -> horizontalAlign?.alignment?.toGravity() ?: -1
                    }
                }
            }

            is DeclarativeBoxLayout -> {
                val inheritsContentGravity = boxAlign == null
                DeclarativeBoxLayout.LayoutParams(
                    width = width,
                    height = height,
                    inheritsContentGravity = inheritsContentGravity,
                ).applyLayoutParams(
                    margin = margin,
                    node = node,
                ) {
                    gravity = boxAlign?.alignment?.toGravity() ?: parent.contentGravity
                }
            }

            is DeclarativeConstraintLayout -> ConstraintLayout.LayoutParams(width, height).applyLayoutParams(
                margin = margin,
                node = node,
            )

            is FrameLayout -> FrameLayout.LayoutParams(width, height).applyLayoutParams(margin = margin, node = node)
            else -> ViewGroup.MarginLayoutParams(width, height).applyMargin(margin, node)
        }
    }

    private fun emitModifierWarnings(
        parent: ViewGroup,
        node: VNode,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
    ) {
        // Parent-data modifiers are valid only under compatible parents; the caller's set de-duplicates warnings.
        // Parent-data modifiers are valid only under specific parents; the caller-provided set deduplicates warnings.
        ModifierParentDataValidator.validate(parent, node).forEach { warning ->
            val key = "${parent::class.java.name}|${node.type}|$warning"
            if (emittedModifierWarnings.add(key)) {
                Log.w(warningTag, warning)
            }
        }
    }

    private fun <T : ViewGroup.MarginLayoutParams> T.applyLayoutParams(
        margin: MarginModifierElement?,
        node: VNode,
        block: T.() -> Unit = {},
    ): T {
        applyMargin(margin, node)
        block()
        return this
    }

    private fun <T : ViewGroup.MarginLayoutParams> T.applyMargin(
        margin: MarginModifierElement?,
        node: VNode,
    ): T {
        if (margin == null) {
            setMargins(0, 0, 0, 0)
            return this
        }
        setMargins(
            node.environment.roundToPx(margin.left),
            node.environment.roundToPx(margin.top),
            node.environment.roundToPx(margin.right),
            node.environment.roundToPx(margin.bottom),
        )
        return this
    }

    private fun defaultDividerWidth(parent: ViewGroup, node: VNode): Int {
        val thickness = ContainerViewBinder.readDividerSpec(node).thickness
        // A divider in horizontal LinearLayout is vertical; otherwise it fills the parent width by default.
        // In horizontal LinearLayout a divider is vertical; otherwise it fills parent width.
        return if ((parent as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL) {
            thickness
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun defaultDividerHeight(parent: ViewGroup, node: VNode): Int {
        val thickness = ContainerViewBinder.readDividerSpec(node).thickness
        // In horizontal LinearLayout a divider fills parent height; otherwise its thickness is its height.
        // In horizontal LinearLayout divider height fills the parent; otherwise thickness becomes height.
        return if ((parent as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            thickness
        }
    }

    private fun VerticalAlignment.toGravity(): Int {
        return when (this) {
            VerticalAlignment.Top -> android.view.Gravity.TOP
            VerticalAlignment.Center -> android.view.Gravity.CENTER_VERTICAL
            VerticalAlignment.Bottom -> android.view.Gravity.BOTTOM
        }
    }

    private fun HorizontalAlignment.toGravity(): Int {
        return when (this) {
            HorizontalAlignment.Start -> android.view.Gravity.START
            HorizontalAlignment.Center -> android.view.Gravity.CENTER_HORIZONTAL
            HorizontalAlignment.End -> android.view.Gravity.END
        }
    }

    private fun BoxAlignment.toGravity(): Int {
        return when (this) {
            BoxAlignment.TopStart -> android.view.Gravity.TOP or android.view.Gravity.START
            BoxAlignment.TopCenter -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            BoxAlignment.TopEnd -> android.view.Gravity.TOP or android.view.Gravity.END
            BoxAlignment.CenterStart -> android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            BoxAlignment.Center -> android.view.Gravity.CENTER
            BoxAlignment.CenterEnd -> android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            BoxAlignment.BottomStart -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            BoxAlignment.BottomCenter -> android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            BoxAlignment.BottomEnd -> android.view.Gravity.BOTTOM or android.view.Gravity.END
        }
    }

    private fun ConstraintDimension.toLayoutParamValue(node: VNode): Int {
        // Android LayoutParams represent ConstraintLayout match-constraints with zero.
        // ConstraintLayout expresses match-constraints as 0 in Android LayoutParams.
        return when (this) {
            ConstraintDimension.WrapContent -> ViewGroup.LayoutParams.WRAP_CONTENT
            ConstraintDimension.FillToConstraints -> 0
            ConstraintDimension.MatchParent -> ViewGroup.LayoutParams.MATCH_PARENT
            is ConstraintDimension.Fixed -> node.environment.roundToPx(value)
        }
    }
}
