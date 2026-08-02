package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock

/**
 * Owns one mutable animated value without depending on composition.
 *
 * [AnimatableCore] publishes samples directly into [value] on the coroutine and frame context used
 * by [animateTo]. It deliberately provides no mutex or last-writer arbitration: concurrent calls to
 * [animateTo] or [snapTo] can overwrite each other. Higher-level owners must serialize mutations or
 * cancel and join the previous animation before starting another one.
 *
 * Cancellation leaves [value] at the most recently published sample. The instance does not own a
 * coroutine scope or frame clock and therefore has no independent disposal step.
 *
 * @sample com.viewcompose.animation.core.samples.animatableCoreSample
 *
 * @param T domain value converted into interpolated dimensions
 * @param initialValue value exposed before the first mutation
 * @param converter stable converter used for every animation on this instance
 */
class AnimatableCore<T>(
    initialValue: T,
    private val converter: AnimationConverter<T>,
) {
    /**
     * Returns the live value most recently assigned or published by an animation frame.
     *
     * Only this instance writes the property, but it provides no cross-thread synchronization.
     */
    var value: T = initialValue
        private set

    /**
     * Assigns [targetValue] synchronously without interpolation.
     *
     * This function does not suspend in its current implementation and does not cancel a concurrent
     * [animateTo]; that animation may publish another value after this call.
     *
     * @param targetValue value to expose immediately
     */
    suspend fun snapTo(targetValue: T) {
        value = targetValue
    }

    /**
     * Animates from the current [value] to [targetValue] using [frameClock].
     *
     * The start value is captured when this function begins. Samples are published on each frame;
     * successful finite completion publishes the exact terminal value. Coroutine cancellation or a
     * frame-clock/callback failure propagates and leaves the last published value in place. Infinite
     * specifications do not return normally.
     *
     * This low-level type does not arbitrate overlapping mutations. Callers that need retargeting or
     * last-writer-wins behavior must provide structured cancellation around this function.
     *
     * @param targetValue value reached by a successfully completed finite animation
     * @param animationSpec sampling and timing policy; defaults to [spring]
     * @param frameClock monotonic source that paces samples and controls their execution context
     */
    suspend fun animateTo(
        targetValue: T,
        animationSpec: AnimationSpec = spring(),
        frameClock: MonotonicFrameClock,
    ) {
        runAnimation(
            frameClock = frameClock,
            startValue = value,
            endValue = targetValue,
            animationSpec = animationSpec,
            converter = converter,
        ) { next ->
            value = next
        }
    }
}
