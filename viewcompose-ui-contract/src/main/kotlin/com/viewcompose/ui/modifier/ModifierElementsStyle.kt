package com.viewcompose.ui.modifier

import com.viewcompose.ui.shape.UiShape

/**
 * 背景、边框、裁剪、层级和图形变换相关的 modifier 元素模型。
 * Modifier element models for backgrounds, borders, clipping, layers, and graphics transforms.
 */
data class BackgroundColorModifierElement(
    val color: Int,
) : ModifierElement

data class BackgroundDrawableResModifierElement(
    val resId: Int,
) : ModifierElement

data class BorderModifierElement(
    val width: Int,
    val color: Int,
) : ModifierElement

data class CornerRadiusModifierElement(
    val topStart: Int,
    val topEnd: Int,
    val bottomEnd: Int,
    val bottomStart: Int,
) : ModifierElement {
    val isUniform: Boolean
        get() = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart
}

data class ShapeModifierElement(
    val shape: UiShape,
) : ModifierElement

data class ClipModifierElement(
    val clip: Boolean = true,
) : ModifierElement

data class ElevationModifierElement(
    val elevation: Int,
) : ModifierElement

data class AlphaModifierElement(
    val alpha: Float,
) : ModifierElement

data class ZIndexModifierElement(
    val zIndex: Float,
) : ModifierElement

data class TransformOrigin(
    val pivotFractionX: Float,
    val pivotFractionY: Float,
) {
    companion object {
        val Center = TransformOrigin(
            pivotFractionX = 0.5f,
            pivotFractionY = 0.5f,
        )
    }
}

data class GraphicsLayerModifierElement(
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val rotationZ: Float? = null,
    val rotationX: Float? = null,
    val rotationY: Float? = null,
    val translationX: Float? = null,
    val translationY: Float? = null,
    val alpha: Float? = null,
    val transformOrigin: TransformOrigin? = null,
    val clip: Boolean? = null,
) : ModifierElement
