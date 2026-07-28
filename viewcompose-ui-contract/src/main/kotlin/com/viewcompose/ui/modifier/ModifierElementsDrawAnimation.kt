package com.viewcompose.ui.modifier

import com.viewcompose.ui.graphics.DrawBlock
import com.viewcompose.ui.graphics.DrawCacheBuildBlock
import com.viewcompose.ui.graphics.DrawContentBlock

/**
 * 绘制与内容尺寸动画相关的 modifier 元素模型。
 * Modifier element models for drawing and content-size animation.
 */
data class DrawBehindModifierElement(
    val key: Any,
    val onDraw: DrawBlock,
) : ModifierElement

data class DrawWithContentModifierElement(
    val key: Any,
    val onDraw: DrawContentBlock,
) : ModifierElement

data class DrawWithCacheModifierElement(
    val key: Any,
    val onBuildDrawCache: DrawCacheBuildBlock,
) : ModifierElement

sealed interface ContentSizeAnimationSpecModel

sealed interface ContentSizeEasingModel {
    data object Linear : ContentSizeEasingModel
    data object FastOutSlowIn : ContentSizeEasingModel
    data object LinearOutSlowIn : ContentSizeEasingModel
    data object FastOutLinearIn : ContentSizeEasingModel

    data class CubicBezier(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : ContentSizeEasingModel
}

data class ContentSizeKeyframeModel(
    val timeMillis: Int,
    val valueFraction: Float,
)

enum class ContentSizeRepeatModeModel {
    Restart,
    Reverse,
}

data class ContentSizeTweenSpecModel(
    val durationMillis: Int,
    val delayMillis: Int,
    val easing: ContentSizeEasingModel,
) : ContentSizeAnimationSpecModel

data class ContentSizeSpringSpecModel(
    val durationMillis: Int,
    val dampingRatio: Float,
    val stiffness: Float,
) : ContentSizeAnimationSpecModel

data class ContentSizeKeyframesSpecModel(
    val durationMillis: Int,
    val keyframes: List<ContentSizeKeyframeModel>,
) : ContentSizeAnimationSpecModel

data object ContentSizeSnapSpecModel : ContentSizeAnimationSpecModel

data class ContentSizeRepeatableSpecModel(
    val iterations: Int,
    val animation: ContentSizeAnimationSpecModel,
    val repeatMode: ContentSizeRepeatModeModel,
) : ContentSizeAnimationSpecModel

data class ContentSizeInfiniteRepeatableSpecModel(
    val animation: ContentSizeAnimationSpecModel,
    val repeatMode: ContentSizeRepeatModeModel,
) : ContentSizeAnimationSpecModel

data class AnimateContentSizeModifierElement(
    val animationSpec: ContentSizeAnimationSpecModel,
) : ModifierElement

enum class Visibility {
    Visible,
    Invisible,
    Gone,
}

data class VisibilityModifierElement(
    val visibility: Visibility,
) : ModifierElement
