package com.viewcompose.ui.modifier

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.spec.ConstraintItemSpec

/**
 * 布局约束、尺寸、边距和对齐相关的 modifier 元素模型。
 * Modifier element models for layout constraints, sizing, margins, and alignment.
 */
data class PaddingModifierElement(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
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
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) : ModifierElement

data class SizeModifierElement(
    val width: Int,
    val height: Int,
) : ModifierElement

data class WidthModifierElement(
    val width: Int,
) : ModifierElement

data class HeightModifierElement(
    val height: Int,
) : ModifierElement

data class MinHeightModifierElement(
    val minHeight: Int,
) : ModifierElement

data class MinWidthModifierElement(
    val minWidth: Int,
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
    val x: Float,
    val y: Float,
) : ModifierElement
