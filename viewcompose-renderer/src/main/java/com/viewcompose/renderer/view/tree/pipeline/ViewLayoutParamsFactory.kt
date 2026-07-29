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
 * 将 VNode modifier/spec 转换为父容器可接受的 Android LayoutParams。
 * Converts VNode modifier/spec data into Android LayoutParams accepted by the parent container.
 *
 * 不同父容器的默认宽高和 parent-data 语义不同，因此转换必须以 parent 类型为上下文。
 * Default size and parent-data semantics differ by parent container, so conversion must use the parent type as context.
 */
internal object ViewLayoutParamsFactory {
    /**
     * 为 node 创建当前 parent 下的 LayoutParams。
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
        // FlowRow/FlowColumn 复用 LinearLayout 风格默认值，但 orientation 由容器类型推导。
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
                // LinearLayout 中带 weight 的主轴尺寸默认为 0，符合 Android 权重分配预期。
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

            is DeclarativeBoxLayout -> FrameLayout.LayoutParams(width, height).applyLayoutParams(
                margin = margin,
                node = node,
            ) {
                gravity = boxAlign?.alignment?.toGravity() ?: DeclarativeBoxLayout.UNSET_GRAVITY
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
        // parent-data modifier 只在特定父容器下有效；重复 warning 通过调用方 set 做去重。
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
        // 横向 LinearLayout 中 divider 表示竖线，否则默认填满父宽度。
        // In horizontal LinearLayout a divider is vertical; otherwise it fills parent width.
        return if ((parent as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL) {
            thickness
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun defaultDividerHeight(parent: ViewGroup, node: VNode): Int {
        val thickness = ContainerViewBinder.readDividerSpec(node).thickness
        // 横向 LinearLayout 中 divider 高度填满父容器，否则厚度就是高度。
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
        // ConstraintLayout 的 match-constraints 在 Android LayoutParams 中用 0 表达。
        // ConstraintLayout expresses match-constraints as 0 in Android LayoutParams.
        return when (this) {
            ConstraintDimension.WrapContent -> ViewGroup.LayoutParams.WRAP_CONTENT
            ConstraintDimension.FillToConstraints -> 0
            ConstraintDimension.MatchParent -> ViewGroup.LayoutParams.MATCH_PARENT
            is ConstraintDimension.Fixed -> node.environment.roundToPx(value)
        }
    }
}
