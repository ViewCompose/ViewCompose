package com.viewcompose.animation.core.samples

import com.viewcompose.animation.core.AnimatableCore
import com.viewcompose.animation.core.ArgbChannels
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.AnimationState
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.Easing
import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.MotionRole
import com.viewcompose.animation.core.MotionScheme
import com.viewcompose.animation.core.ReducedMotionBehavior
import com.viewcompose.animation.core.ReducedMotionPolicy
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.TransitionCore
import com.viewcompose.animation.core.TargetAnimation
import com.viewcompose.animation.core.cubicBezier
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.keyframe
import com.viewcompose.animation.core.keyframes
import com.viewcompose.animation.core.repeatable
import com.viewcompose.animation.core.runAnimation
import com.viewcompose.animation.core.exponentialDecay
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
        expressiveSpatial = spring(dampingRatio = 0.82f, stiffness = 210f),
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
    val pointConverter = object : AnimationConverter<Point, Point> {
        override val vectorSize: Int = 2
        override val zeroVelocity: Point = Point(0f, 0f)
        override val visibilityThreshold: Point = Point(0.01f, 0.01f)

        override fun convertToVector(value: Point, destination: FloatArray) {
            require(destination.size == vectorSize)
            destination[0] = value.x
            destination[1] = value.y
        }

        override fun convertFromVector(vector: FloatArray): Point {
            require(vector.size == vectorSize)
            return Point(
                x = vector[0],
                y = vector[1],
            )
        }

        override fun convertVelocityToVector(velocity: Point, destination: FloatArray) {
            require(destination.size == vectorSize)
            convertToVector(velocity, destination)
        }

        override fun convertVelocityFromVector(vector: FloatArray): Point {
            require(vector.size == vectorSize)
            return convertFromVector(vector)
        }
    }
    return TargetAnimation(
        initialValue = Point(0f, 20f),
        targetValue = Point(100f, 60f),
        animationSpec = tween(durationMillis = 200, easing = EasingDefaults.Linear),
        converter = pointConverter,
    ).stateAt(100_000_000L).value
}

/** Preserves fractional signed velocity while reconstructing integer positions. */
fun distinctIntegerVelocityDomainSample(): AnimationState<Int, Float> {
    return TargetAnimation(
        initialValue = 0,
        targetValue = 100,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
        converter = AnimationConverters.Int,
        initialVelocity = AnimationVelocity(42.5f),
    ).stateAt(16_000_000L)
}

/** Keeps signed four-channel velocity separate from its packed ARGB value. */
fun colorVelocityDomainSample(): AnimationState<Int, ArgbChannels> {
    return TargetAnimation(
        initialValue = 0xFF336699.toInt(),
        targetValue = 0xFFCC8844.toInt(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 220f),
        converter = AnimationConverters.ColorInt,
        initialVelocity = AnimationVelocity(
            ArgbChannels(alpha = 0f, red = 80f, green = -30f, blue = 15f),
        ),
    ).stateAt(16_000_000L)
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
    ) { state ->
        latest = state.value
    }
    return latest
}

/** Samples a timeline deterministically without running a coroutine or owning a clock. */
fun targetAnimationSamplingSample(): Float {
    return TargetAnimation(
        initialValue = 20f,
        targetValue = 100f,
        animationSpec = tween(durationMillis = 400, easing = EasingDefaults.Linear),
        converter = AnimationConverters.Float,
    ).stateAt(200_000_000L).value
}

/** Runs physical spring and decay mutations with typed velocity, bounds, and structured results. */
suspend fun physicalAnimationSample(frameClock: MonotonicFrameClock): Float {
    val value = AnimatableCore(
        initialValue = 0f,
        converter = AnimationConverters.Float,
    )
    value.updateBounds(lowerBound = -40f, upperBound = 140f)
    value.animateTo(
        targetValue = 100f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 240f),
        initialVelocity = AnimationVelocity(320f),
        frameClock = frameClock,
    )
    value.animateDecay(
        initialVelocity = AnimationVelocity(-180f),
        animationSpec = exponentialDecay(frictionMultiplier = 1.2f),
        frameClock = frameClock,
    )
    return value.value
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
