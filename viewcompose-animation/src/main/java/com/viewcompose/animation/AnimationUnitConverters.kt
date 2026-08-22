package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.ui.unit.UiDp

internal object AnimationUnitConverters {
    val Dp: AnimationConverter<UiDp, UiDp> = object : AnimationConverter<UiDp, UiDp> {
        override val vectorSize: Int = 1
        override val zeroVelocity: UiDp = UiDp(0f)
        override val visibilityThreshold: UiDp = UiDp(0.1f)

        override fun convertToVector(value: UiDp, destination: FloatArray) {
            require(destination.size == vectorSize)
            destination[0] = value.value
        }

        override fun convertFromVector(vector: FloatArray): UiDp {
            require(vector.size == vectorSize)
            return UiDp(vector[0])
        }

        override fun convertVelocityToVector(velocity: UiDp, destination: FloatArray) {
            require(destination.size == vectorSize)
            destination[0] = velocity.value
        }

        override fun convertVelocityFromVector(vector: FloatArray): UiDp {
            require(vector.size == vectorSize)
            return UiDp(vector[0])
        }
    }
}
