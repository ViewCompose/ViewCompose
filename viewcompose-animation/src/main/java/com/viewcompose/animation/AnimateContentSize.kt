package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.CubicBezierEasing
import com.viewcompose.animation.core.Easing
import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.InfiniteRepeatableSpec
import com.viewcompose.animation.core.KeyframesSpec
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.RepeatableSpec
import com.viewcompose.animation.core.SnapSpec
import com.viewcompose.animation.core.SpringSpec
import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.animation.core.spring
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeInfiniteRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeKeyframeModel
import com.viewcompose.ui.modifier.ContentSizeKeyframesSpecModel
import com.viewcompose.ui.modifier.ContentSizeRepeatModeModel
import com.viewcompose.ui.modifier.ContentSizeRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.Modifier

/**
 * 在布局内容尺寸变化时为测量结果添加动画。
 * Adds animation to measured content-size changes.
 */
fun Modifier.animateContentSize(
    animationSpec: AnimationSpec = spring(),
): Modifier {
    return then(
        AnimateContentSizeModifierElement(
            animationSpec = animationSpec.toContentSizeSpecModel(),
        ),
    )
}

/**
 * 将 animation-core 规格转换为跨 renderer 传输的 modifier 模型。
 * Converts animation-core specs into modifier models that can cross the renderer boundary.
 */
private fun AnimationSpec.toContentSizeSpecModel(): ContentSizeAnimationSpecModel {
    return when (this) {
        is TweenSpec -> ContentSizeTweenSpecModel(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing.toContentSizeEasingModel(),
        )

        is SpringSpec -> ContentSizeSpringSpecModel(
            durationMillis = durationMillis,
            dampingRatio = dampingRatio,
            stiffness = stiffness,
        )

        is KeyframesSpec -> ContentSizeKeyframesSpecModel(
            durationMillis = durationMillis,
            keyframes = keyframes.map { keyframe ->
                ContentSizeKeyframeModel(
                    timeMillis = keyframe.timeMillis,
                    valueFraction = keyframe.valueFraction,
                )
            },
        )

        is RepeatableSpec -> ContentSizeRepeatableSpecModel(
            iterations = iterations,
            animation = animation.toContentSizeSpecModel(),
            repeatMode = repeatMode.toContentSizeRepeatMode(),
        )

        is InfiniteRepeatableSpec -> ContentSizeInfiniteRepeatableSpecModel(
            animation = animation.toContentSizeSpecModel(),
            repeatMode = repeatMode.toContentSizeRepeatMode(),
        )
        SnapSpec -> ContentSizeSnapSpecModel
    }
}

private fun RepeatMode.toContentSizeRepeatMode(): ContentSizeRepeatModeModel {
    return when (this) {
        RepeatMode.Restart -> ContentSizeRepeatModeModel.Restart
        RepeatMode.Reverse -> ContentSizeRepeatModeModel.Reverse
    }
}

/**
 * 只保留 renderer 可识别的 easing；未知实现降级为默认曲线。
 * Keeps only renderer-known easing values; unknown implementations fall back to the default curve.
 */
private fun Easing.toContentSizeEasingModel(): ContentSizeEasingModel {
    return when (this) {
        EasingDefaults.Linear -> ContentSizeEasingModel.Linear
        EasingDefaults.FastOutSlowIn -> ContentSizeEasingModel.FastOutSlowIn
        EasingDefaults.LinearOutSlowIn -> ContentSizeEasingModel.LinearOutSlowIn
        EasingDefaults.FastOutLinearIn -> ContentSizeEasingModel.FastOutLinearIn
        is CubicBezierEasing -> ContentSizeEasingModel.CubicBezier(
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
        )

        else -> ContentSizeEasingModel.FastOutSlowIn
    }
}
