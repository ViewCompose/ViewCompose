package com.viewcompose.animation.core

/** Selects a semantic timing channel without naming a component or design system. */
enum class MotionRole {
    FastEffects,
    DefaultEffects,
    FastSpatial,
    DefaultSpatial,
    ExpressiveSpatial,
}

/** Defines how a component responds when an active animation is retargeted. */
enum class MotionInterruptionPolicy {
    /** Continues from the most recently published visual value toward the new target. */
    RetargetFromCurrent,

    /** Selects the new target immediately and skips interpolation. */
    SnapToTarget,
}

/** Selects how non-essential motion changes when reduced motion is requested. */
enum class ReducedMotionBehavior {
    /** Replaces non-essential motion with an immediate target selection. */
    Snap,

    /** Retains the transition with [ReducedMotionPolicy.nonEssentialDurationScale]. */
    Shorten,
}

/**
 * Defines deterministic reduced-motion substitution without changing component state or layout.
 *
 * @property nonEssentialBehavior treatment applied to decorative or spatially non-essential motion
 * @property nonEssentialDurationScale finite scale in `0f..1f` used by [ReducedMotionBehavior.Shorten]
 * @property essentialDurationScale finite scale in `0f..1f` applied to essential state transitions
 * @throws IllegalArgumentException if either duration scale is non-finite or outside `0f..1f`
 */
data class ReducedMotionPolicy(
    val nonEssentialBehavior: ReducedMotionBehavior = ReducedMotionBehavior.Snap,
    val nonEssentialDurationScale: Float = 0.25f,
    val essentialDurationScale: Float = 0.5f,
) {
    init {
        require(nonEssentialDurationScale.isFinite() && nonEssentialDurationScale in 0f..1f) {
            "Reduced-motion nonEssentialDurationScale must be finite and between 0 and 1."
        }
        require(essentialDurationScale.isFinite() && essentialDurationScale in 0f..1f) {
            "Reduced-motion essentialDurationScale must be finite and between 0 and 1."
        }
    }
}

/**
 * Groups resolved semantic motion specifications independently from component recipes.
 *
 * This Q2 value owns no clock, coroutine, or mutable animation state. Components select a
 * [MotionRole], declare whether movement is essential to understanding a state change, and pass
 * the resulting immutable [AnimationSpec] to an existing lifecycle-owned animation API.
 *
 * @property fastEffects short opacity, color, or state-layer transition
 * @property defaultEffects standard opacity, color, or effect transition
 * @property fastSpatial short position, size, or shape transition
 * @property defaultSpatial standard position, size, or shape transition
 * @property expressiveSpatial emphasized spatial transition, commonly a physical spring
 * @property interruptionPolicy default retargeting policy consumed by a component owner
 * @property reducedMotion deterministic substitution used when reduced motion is requested
 * @sample com.viewcompose.animation.core.samples.motionSchemeSample
 */
data class MotionScheme(
    val fastEffects: FiniteAnimationSpec,
    val defaultEffects: FiniteAnimationSpec,
    val fastSpatial: FiniteAnimationSpec,
    val defaultSpatial: FiniteAnimationSpec,
    val expressiveSpatial: FiniteAnimationSpec,
    val interruptionPolicy: MotionInterruptionPolicy = MotionInterruptionPolicy.RetargetFromCurrent,
    val reducedMotion: ReducedMotionPolicy = ReducedMotionPolicy(),
) {
    /**
     * Returns the resolved specification for a semantic role and motion environment.
     *
     * Reduced motion never changes the component's target state. Non-essential motion snaps or is
     * shortened according to [ReducedMotionPolicy.nonEssentialBehavior]; essential transitions are
     * duration-scaled so their state change remains perceivable.
     *
     * @param role semantic timing role selected by the component recipe
     * @param reducedMotionEnabled whether the current host requests reduced movement
     * @param essential whether interpolation is needed to communicate the state change
     * @return an immutable specification with the reduced-motion policy already applied
     */
    fun resolve(
        role: MotionRole,
        reducedMotionEnabled: Boolean,
        essential: Boolean = false,
    ): FiniteAnimationSpec {
        val spec = when (role) {
            MotionRole.FastEffects -> fastEffects
            MotionRole.DefaultEffects -> defaultEffects
            MotionRole.FastSpatial -> fastSpatial
            MotionRole.DefaultSpatial -> defaultSpatial
            MotionRole.ExpressiveSpatial -> expressiveSpatial
        }
        if (!reducedMotionEnabled) return spec
        if (!essential && reducedMotion.nonEssentialBehavior == ReducedMotionBehavior.Snap) {
            return SnapSpec
        }
        val scale = if (essential) {
            reducedMotion.essentialDurationScale
        } else {
            reducedMotion.nonEssentialDurationScale
        }
        return spec.scaledDuration(scale)
    }
}

private fun FiniteAnimationSpec.scaledDuration(scale: Float): FiniteAnimationSpec {
    if (scale <= 0f) return SnapSpec
    return when (this) {
        is TweenSpec -> copy(
            durationMillis = durationMillis.scaleMillis(scale),
            delayMillis = delayMillis.scaleMillis(scale),
        )
        is SpringSpec -> copy(
            stiffness = (stiffness.toDouble() / (scale.toDouble() * scale.toDouble()))
                .coerceAtMost(Float.MAX_VALUE.toDouble())
                .toFloat(),
            maxDurationMillis = maxDurationMillis.scaleMillis(scale).coerceIn(1, 60_000),
        )
        is KeyframesSpec -> copy(
            durationMillis = durationMillis.scaleMillis(scale),
            keyframes = keyframes.map { frame ->
                frame.copy(timeMillis = frame.timeMillis.scaleMillis(scale))
            },
        )
        is RepeatableSpec -> copy(animation = animation.scaledDuration(scale))
        SnapSpec -> SnapSpec
    }
}

private fun DurationBasedAnimationSpec.scaledDuration(scale: Float): DurationBasedAnimationSpec {
    if (scale <= 0f) return SnapSpec
    return when (this) {
        is TweenSpec -> copy(
            durationMillis = durationMillis.scaleMillis(scale),
            delayMillis = delayMillis.scaleMillis(scale),
        )
        is KeyframesSpec -> copy(
            durationMillis = durationMillis.scaleMillis(scale),
            keyframes = keyframes.map { frame ->
                frame.copy(timeMillis = frame.timeMillis.scaleMillis(scale))
            },
        )
        is RepeatableSpec -> copy(animation = animation.scaledDuration(scale))
        SnapSpec -> SnapSpec
    }
}

private fun Int.scaleMillis(scale: Float): Int {
    if (this <= 0 || scale <= 0f) return 0
    return (toDouble() * scale.toDouble())
        .coerceAtMost(Int.MAX_VALUE.toDouble())
        .toInt()
        .coerceAtLeast(1)
}
