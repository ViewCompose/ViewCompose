package com.viewcompose.ui.modifier

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.unit.UiDp

/**
 * 布局约束、尺寸、边距和对齐相关的 modifier 元素模型。
 * Modifier element models for layout constraints, sizing, margins, and alignment.
 */
data class PaddingModifierElement(
    val left: UiDp,
    val top: UiDp,
    val right: UiDp,
    val bottom: UiDp,
) : ModifierElement

data class SystemBarsInsetsPaddingModifierElement(
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean,
) : ModifierElement

data class ImeInsetsPaddingModifierElement(
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean,
) : ModifierElement

data class MarginModifierElement(
    val left: UiDp,
    val top: UiDp,
    val right: UiDp,
    val bottom: UiDp,
) : ModifierElement

data class SizeModifierElement(
    val width: UiDimension,
    val height: UiDimension,
) : ModifierElement

data class WidthModifierElement(
    val width: UiDimension,
) : ModifierElement

data class HeightModifierElement(
    val height: UiDimension,
) : ModifierElement

data class MinHeightModifierElement(
    val minHeight: UiDp,
) : ModifierElement

data class MinWidthModifierElement(
    val minWidth: UiDp,
) : ModifierElement

data class LayoutIdModifierElement(
    val layoutId: String,
) : ModifierElement

data class ConstraintModifierElement(
    val constraint: ConstraintItemSpec,
    val referenceId: String? = null,
) : ModifierElement

data class WeightModifierElement(
    val weight: Float,
) : ModifierElement

data class BoxAlignModifierElement(
    val alignment: BoxAlignment,
) : ModifierElement

data class HorizontalAlignModifierElement(
    val alignment: HorizontalAlignment,
) : ModifierElement

data class VerticalAlignModifierElement(
    val alignment: VerticalAlignment,
) : ModifierElement

data class OffsetModifierElement(
    val x: UiDp,
    val y: UiDp,
) : ModifierElement
