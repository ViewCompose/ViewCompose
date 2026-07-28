package com.viewcompose.widget.core

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement

/**
 * 布局 DSL scope 基类，继承 UiTreeBuilder 以继续发射子节点。
 * Base layout DSL scope that extends UiTreeBuilder so children can keep emitting nodes.
 */
@UiDslMarker
open class LayoutScope internal constructor() : UiTreeBuilder()

/**
 * Row 子内容 scope，提供横向布局专用 parent-data 修饰符。
 * Row content scope that exposes horizontal-layout-specific parent-data modifiers.
 */
@UiDslMarker
class RowScope internal constructor() : LayoutScope() {
    /**
     * 设置 Row 子节点占用剩余空间的权重。
     * Sets the weight used by a Row child to consume remaining space.
     */
    fun Modifier.weight(weight: Float): Modifier = scopedWeight(weight)

    /**
     * 设置 Row 子节点在纵轴上的对齐方式。
     * Sets a Row child's cross-axis vertical alignment.
     */
    fun Modifier.align(alignment: VerticalAlignment): Modifier = then(VerticalAlignModifierElement(alignment))

    /**
     * 发射一个带 weight 的 Spacer。
     * Emits a Spacer with weight.
     */
    fun FlexibleSpacer(
        weight: Float = 1f,
        key: Any? = null,
        modifier: Modifier = Modifier,
    ) {
        Spacer(
            key = key,
            modifier = modifier.weight(weight),
        )
    }
}

/**
 * Column 子内容 scope，提供纵向布局专用 parent-data 修饰符。
 * Column content scope that exposes vertical-layout-specific parent-data modifiers.
 */
@UiDslMarker
class ColumnScope internal constructor() : LayoutScope() {
    /**
     * 设置 Column 子节点占用剩余空间的权重。
     * Sets the weight used by a Column child to consume remaining space.
     */
    fun Modifier.weight(weight: Float): Modifier = scopedWeight(weight)

    /**
     * 设置 Column 子节点在横轴上的对齐方式。
     * Sets a Column child's cross-axis horizontal alignment.
     */
    fun Modifier.align(alignment: HorizontalAlignment): Modifier = then(HorizontalAlignModifierElement(alignment))

    /**
     * 发射一个带 weight 的 Spacer。
     * Emits a Spacer with weight.
     */
    fun FlexibleSpacer(
        weight: Float = 1f,
        key: Any? = null,
        modifier: Modifier = Modifier,
    ) {
        Spacer(
            key = key,
            modifier = modifier.weight(weight),
        )
    }
}

/**
 * Box 子内容 scope，提供盒模型对齐修饰符。
 * Box content scope that exposes box-alignment modifiers.
 */
@UiDslMarker
class BoxScope internal constructor() : LayoutScope() {
    /**
     * 设置 Box 子节点在容器中的对齐方式。
     * Sets a Box child's alignment inside its container.
     */
    fun Modifier.align(alignment: BoxAlignment): Modifier = then(BoxAlignModifierElement(alignment))
}

/**
 * 共享的 weight parent-data 构建逻辑。
 * Shared builder for weight parent data.
 */
private fun Modifier.scopedWeight(weight: Float): Modifier {
    require(weight > 0f) {
        "weight must be > 0"
    }
    return then(WeightModifierElement(weight))
}
