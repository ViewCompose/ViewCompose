package com.viewcompose.animation.core

/**
 * Defines a target-free physical animation driven by an initial velocity.
 *
 * Decay specifications own equations and termination policy but no value, clock, coroutine, or
 * mutable state.
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 */
sealed interface DecayAnimationSpec

/**
 * Applies platform-neutral exponential velocity decay.
 *
 * The engine uses `v(t) = v₀e⁻λᵗ` and
 * `x(t) = x₀ + (v₀ / λ)(1 - e⁻λᵗ)`, where
 * `λ = 4.2 * frictionMultiplier s⁻¹`. [maxDurationMillis] is a safety guard, not a nominal
 * duration.
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @property frictionMultiplier positive finite multiplier applied to the base decay rate
 * @property maxDurationMillis safety guard in milliseconds, from `1` through `60_000`
 * @throws IllegalArgumentException if any parameter is outside its documented range
 */
data class ExponentialDecaySpec(
    val frictionMultiplier: Float = 1f,
    val maxDurationMillis: Int = 10_000,
) : DecayAnimationSpec {
    init {
        require(frictionMultiplier.isFinite() && frictionMultiplier > 0f) {
            "Decay frictionMultiplier must be finite and greater than zero."
        }
        require(maxDurationMillis in 1..60_000) {
            "Decay maxDurationMillis must be between 1 and 60000."
        }
    }
}

/**
 * Creates a platform-neutral exponential decay specification.
 *
 * @sample com.viewcompose.animation.core.samples.physicalAnimationSample
 *
 * @param frictionMultiplier positive finite multiplier applied to the base decay rate
 * @param maxDurationMillis safety guard in milliseconds, from `1` through `60_000`
 * @return immutable target-free decay specification
 * @throws IllegalArgumentException if any parameter is outside its documented range
 */
fun exponentialDecay(
    frictionMultiplier: Float = 1f,
    maxDurationMillis: Int = 10_000,
): ExponentialDecaySpec = ExponentialDecaySpec(
    frictionMultiplier = frictionMultiplier,
    maxDurationMillis = maxDurationMillis,
)
