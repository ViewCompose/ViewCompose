package com.viewcompose.animation.core.samples

import com.viewcompose.animation.core.AnimatableCore
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.Easing
import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.MotionRole
import com.viewcompose.animation.core.MotionScheme
import com.viewcompose.animation.core.ReducedMotionBehavior
import com.viewcompose.animation.core.ReducedMotionPolicy
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.TransitionCore
import com.viewcompose.animation.core.cubicBezier
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.keyframe
import com.viewcompose.animation.core.keyframes
import com.viewcompose.animation.core.repeatable
import com.viewcompose.animation.core.runAnimation
import com.viewcompose.animation.core.sampleAnimationValue
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.frame.MonotonicFrameClock

/** Builds reusable finite and repeating timing specifications. */
fun animationSpecificationsSample(): List<AnimationSpec> {
    val emphasized = tween(
        durationMillis = 240,
        easing = Easing { fraction -> fraction * fraction },
    )
    val staged = keyframes(
        durationMillis = 400,
        keyframe(timeMillis = 120, valueFraction = 0.2f),
        keyframe(timeMillis = 280, valueFraction = 0.85f),
    )
    return listOf(
        emphasized,
        spring(dampingRatio = 0.9f, stiffness = 220f),
        staged,
        repeatable(iterations = 2, animation = emphasized, repeatMode = RepeatMode.Reverse),
        infiniteRepeatable(animation = staged),
    )
}

/** Resolves a semantic motion role after applying the host's reduced-motion request. */
fun motionSchemeSample(reducedMotionEnabled: Boolean): AnimationSpec {
    val scheme = MotionScheme(
        fastEffects = tween(durationMillis = 100),
        defaultEffects = tween(durationMillis = 200),
        fastSpatial = tween(durationMillis = 160),
        defaultSpatial = tween(durationMillis = 320),
        expressiveSpatial = spring(durationMillis = 600),
        reducedMotion = ReducedMotionPolicy(
            nonEssentialBehavior = ReducedMotionBehavior.Snap,
        ),
    )
    return scheme.resolve(
        role = MotionRole.ExpressiveSpatial,
        reducedMotionEnabled = reducedMotionEnabled,
        essential = true,
    )
}

/** Creates and directly evaluates a custom cubic Bézier easing. */
fun cubicBezierEasingSample(): Float {
    val easing = cubicBezier(
        x1 = 0.2f,
        y1 = 0f,
        x2 = 0f,
        y2 = 1f,
    )
    return easing.transform(0.5f)
}

/** Converts a two-dimensional application value for component-wise interpolation. */
fun customAnimationConverterSample(): Point {
    val pointConverter = object : AnimationConverter<Point> {
        override fun toVector(value: Point): FloatArray {
            return floatArrayOf(value.x, value.y)
        }

        override fun fromVector(vector: FloatArray): Point {
            return Point(
                x = vector.getOrElse(0) { 0f },
                y = vector.getOrElse(1) { 0f },
            )
        }
    }
    return sampleAnimationValue(
        startValue = Point(0f, 20f),
        endValue = Point(100f, 60f),
        animationSpec = tween(durationMillis = 200, easing = EasingDefaults.Linear),
        converter = pointConverter,
        playTimeNanos = 100_000_000L,
    )
}

/** Runs a frame-paced animation and receives every sampled value. */
suspend fun runAnimationSample(frameClock: MonotonicFrameClock): Float {
    var latest = 0f
    runAnimation(
        frameClock = frameClock,
        startValue = 0f,
        endValue = 1f,
        animationSpec = tween(durationMillis = 180),
        converter = AnimationConverters.Float,
    ) { sampledValue ->
        latest = sampledValue
    }
    return latest
}

/** Samples a timeline deterministically without running a coroutine or owning a clock. */
fun sampleAnimationValueSample(): Float {
    return sampleAnimationValue(
        startValue = 20f,
        endValue = 100f,
        animationSpec = tween(durationMillis = 400, easing = EasingDefaults.Linear),
        converter = AnimationConverters.Float,
        playTimeNanos = 200_000_000L,
    )
}

/** Owns one low-level animated value while the caller supplies structured coroutine ownership. */
suspend fun animatableCoreSample(frameClock: MonotonicFrameClock): Float {
    val value = AnimatableCore(
        initialValue = 0f,
        converter = AnimationConverters.Float,
    )
    value.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 180),
        frameClock = frameClock,
    )
    return value.value
}

/** Coordinates channel durations against one transition segment and shared play time. */
fun transitionCoreSample(): TransitionCore<PanelState> {
    val transition = TransitionCore(initialState = PanelState.Collapsed)
    transition.updateTarget(PanelState.Expanded)
    transition.registerDuration(180_000_000L)
    transition.registerDuration(240_000_000L)
    transition.updatePlayTime(120_000_000L)
    return transition
}

data class Point(
    val x: Float,
    val y: Float,
)

enum class PanelState {
    Collapsed,
    Expanded,
}
