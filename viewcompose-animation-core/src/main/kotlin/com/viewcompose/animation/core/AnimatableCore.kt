package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock

/**
 * 不依赖组合层的可变动画值核心。
 * Composition-free core for a mutable animated value.
 */
class AnimatableCore<T>(
    initialValue: T,
    private val converter: AnimationConverter<T>,
) {
    var value: T = initialValue
        private set

    suspend fun snapTo(targetValue: T) {
        value = targetValue
    }

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
