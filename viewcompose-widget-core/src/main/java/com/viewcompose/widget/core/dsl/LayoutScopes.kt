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
 * Base layout DSL scope that extends UiTreeBuilder so children can keep emitting nodes.
 */
@UiDslMarker
open class LayoutScope internal constructor() : UiTreeBuilder()

/**
 * Row content scope that exposes horizontal-layout-specific parent-data modifiers.
 */
@UiDslMarker
class RowScope internal constructor() : LayoutScope() {
    /**
     * Sets the weight used by a Row child to consume remaining space.
     */
    fun Modifier.weight(weight: Float): Modifier = scopedWeight(weight)

    /**
     * Sets a Row child's cross-axis vertical alignment.
     */
    fun Modifier.align(alignment: VerticalAlignment): Modifier = then(VerticalAlignModifierElement(alignment))

    /**
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
 * Column content scope that exposes vertical-layout-specific parent-data modifiers.
 */
@UiDslMarker
class ColumnScope internal constructor() : LayoutScope() {
    /**
     * Sets the weight used by a Column child to consume remaining space.
     */
    fun Modifier.weight(weight: Float): Modifier = scopedWeight(weight)

    /**
     * Sets a Column child's cross-axis horizontal alignment.
     */
    fun Modifier.align(alignment: HorizontalAlignment): Modifier = then(HorizontalAlignModifierElement(alignment))

    /**
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
 * Box content scope that exposes box-alignment modifiers.
 */
@UiDslMarker
class BoxScope internal constructor() : LayoutScope() {
    /**
     * Sets a Box child's alignment inside its container.
     */
    fun Modifier.align(alignment: BoxAlignment): Modifier = then(BoxAlignModifierElement(alignment))
}

/**
 * Shared builder for weight parent data.
 */
private fun Modifier.scopedWeight(weight: Float): Modifier {
    require(weight > 0f) {
        "weight must be > 0"
    }
    return then(WeightModifierElement(weight))
}
