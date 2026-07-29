package com.viewcompose.ui.modifier

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.unit.UiDp

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
    val width: UiDp,
    val color: Int,
) : ModifierElement

data class CornerRadiusModifierElement(
    val topStart: UiDp,
    val topEnd: UiDp,
    val bottomEnd: UiDp,
    val bottomStart: UiDp,
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
    val elevation: UiDp,
) : ModifierElement

/**
 * 一组共享可选 shape 的有序外阴影。
 * An ordered group of drop shadows sharing an optional shape override.
 */
data class DropShadowModifierElement(
    val shadows: List<UiShadow>,
    val shape: UiShape? = null,
) : ModifierElement {
    init {
        require(shadows.isNotEmpty()) {
            "DropShadowModifierElement requires at least one shadow."
        }
    }
}

data class AlphaModifierElement(
    val alpha: Float,
) : ModifierElement

data class ZIndexModifierElement(
    val zIndex: Float,
) : ModifierElement {
    init {
        require(zIndex.isFinite()) {
            "ZIndexModifierElement.zIndex must be finite."
        }
    }
}

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
