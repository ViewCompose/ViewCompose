package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.ui.unit.UiDp

internal object AnimationUnitConverters {
    val Dp: AnimationConverter<UiDp> = object : AnimationConverter<UiDp> {
        override fun toVector(value: UiDp): FloatArray = floatArrayOf(value.value)

        override fun fromVector(vector: FloatArray): UiDp = UiDp(vector.firstOrNull() ?: 0f)
    }
}
