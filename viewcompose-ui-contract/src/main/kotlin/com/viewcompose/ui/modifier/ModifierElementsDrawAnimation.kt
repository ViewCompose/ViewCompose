package com.viewcompose.ui.modifier

import com.viewcompose.ui.graphics.DrawBlock
import com.viewcompose.ui.graphics.DrawCacheBuildBlock
import com.viewcompose.ui.graphics.DrawContentBlock

/**
 * Records commands drawn behind wrapped content.
 *
 * @property key semantic identity supplied by the modifier caller
 * @property onDraw callback invoked during each draw pass before wrapped content
 */
data class DrawBehindModifierElement(
    val key: Any,
    val onDraw: DrawBlock,
) : ModifierElement

/**
 * Records a draw callback that explicitly controls wrapped-content placement.
 *
 * @property key semantic identity supplied by the modifier caller
 * @property onDraw callback invoked during each draw pass with content-drawing access
 */
data class DrawWithContentModifierElement(
    val key: Any,
    val onDraw: DrawContentBlock,
) : ModifierElement

/**
 * Records a cache-aware draw-command builder.
 *
 * @property key semantic identity supplied by the modifier caller
 * @property onBuildDrawCache callback that obtains or builds replayable commands
 */
data class DrawWithCacheModifierElement(
    val key: Any,
    val onBuildDrawCache: DrawCacheBuildBlock,
) : ModifierElement

/**
 * Platform-neutral finite renderer model for content-size animation timing or physical motion.
 *
 * This sealed hierarchy is an inter-module transport contract. Applications normally create the
 * corresponding animation-core specification through `Modifier.animateContentSize` or
 * `Modifier.animateBounds`.
 */
sealed interface LayoutAnimationSpecModel

/**
 * Platform-neutral finite renderer model retained by `animateContentSize`.
 *
 * This compatibility subtype is also a [LayoutAnimationSpecModel], so renderers can share one
 * timing and physical-motion transport with real bounds animation.
 */
sealed interface ContentSizeAnimationSpecModel : LayoutAnimationSpecModel

/** Identifies a fixed-duration content-size model that may be repeated. */
sealed interface ContentSizeDurationBasedAnimationSpecModel : ContentSizeAnimationSpecModel

/** Defines easing curves that an Android content-size renderer can reproduce. */
sealed interface ContentSizeEasingModel {
    /** Linear interpolation without acceleration. */
    data object Linear : ContentSizeEasingModel

    /** Material-style acceleration followed by deceleration. */
    data object FastOutSlowIn : ContentSizeEasingModel

    /** Linear departure followed by deceleration. */
    data object LinearOutSlowIn : ContentSizeEasingModel

    /** Acceleration followed by a linear arrival. */
    data object FastOutLinearIn : ContentSizeEasingModel

    /**
     * Cubic Bézier easing with normalized time/value control points.
     *
     * @property x1 first control-point time fraction
     * @property y1 first control-point value fraction
     * @property x2 second control-point time fraction
     * @property y2 second control-point value fraction
     */
    data class CubicBezier(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : ContentSizeEasingModel
}

/**
 * Defines one content-size animation keyframe.
 *
 * @property timeMillis play time from animation start in milliseconds
 * @property valueFraction interpolation fraction at [timeMillis]
 */
data class ContentSizeKeyframeModel(
    val timeMillis: Int,
    val valueFraction: Float,
)

/** Selects restart-from-beginning or alternating reverse repeat behavior. */
enum class ContentSizeRepeatModeModel {
    Restart,
    Reverse,
}

/**
 * Describes a duration-based content-size transition.
 *
 * The Android renderer coerces duration to at least one millisecond and delay to zero or greater.
 *
 * @property durationMillis requested animation duration in milliseconds
 * @property delayMillis requested start delay in milliseconds
 * @property easing interpolation curve
 */
data class ContentSizeTweenSpecModel(
    val durationMillis: Int,
    val delayMillis: Int,
    val easing: ContentSizeEasingModel,
) : ContentSizeDurationBasedAnimationSpecModel

/**
 * Describes a normalized-mass physical spring content-size transition.
 *
 * @property dampingRatio dimensionless damping ratio
 * @property stiffness normalized-mass stiffness in `s⁻²`
 * @property maxDurationMillis safety guard in milliseconds rather than a nominal duration
 */
data class ContentSizeSpringSpecModel(
    val dampingRatio: Float,
    val stiffness: Float,
    val maxDurationMillis: Int,
) : ContentSizeAnimationSpecModel

/**
 * Describes a content-size transition interpolated through [keyframes].
 *
 * @property durationMillis requested total duration in milliseconds
 * @property keyframes ordered or renderer-normalized keyframe values
 */
data class ContentSizeKeyframesSpecModel(
    val durationMillis: Int,
    val keyframes: List<ContentSizeKeyframeModel>,
) : ContentSizeDurationBasedAnimationSpecModel

/** Applies a content-size change without an animated interval. */
data object ContentSizeSnapSpecModel : ContentSizeDurationBasedAnimationSpecModel

/**
 * Repeats a finite content-size [animation].
 *
 * The Android renderer coerces negative [iterations] to zero; zero iterations retain the starting
 * size instead of running the inner animation.
 *
 * @property iterations requested number of complete iterations
 * @property animation inner timing model
 * @property repeatMode restart or reverse behavior between iterations
 */
data class ContentSizeRepeatableSpecModel(
    val iterations: Int,
    val animation: ContentSizeDurationBasedAnimationSpecModel,
    val repeatMode: ContentSizeRepeatModeModel,
) : ContentSizeDurationBasedAnimationSpecModel

/**
 * Requests animation when a node's measured content size changes.
 *
 * @property animationSpec platform-neutral timing model consumed by the renderer
 */
data class AnimateContentSizeModifierElement(
    val animationSpec: ContentSizeAnimationSpecModel,
) : ModifierElement

/**
 * Requests animation of a node's real parent-local layout rectangle.
 *
 * Renderers must commit measured size, placement, hit testing, and accessibility geometry as one
 * physical rectangle. A draw-only translation or scale does not satisfy this contract.
 *
 * @property animationSpec finite timing or physical model for all four rectangle edges
 */
data class AnimateBoundsModifierElement(
    val animationSpec: LayoutAnimationSpecModel,
) : ModifierElement

/** Maps declarative visibility to visible, layout-retaining invisible, or layout-removing gone. */
enum class Visibility {
    Visible,
    Invisible,
    Gone,
}

/**
 * Requests platform visibility for the modified node.
 *
 * @property visibility desired layout and drawing visibility
 */
data class VisibilityModifierElement(
    val visibility: Visibility,
) : ModifierElement
