package com.viewcompose.ui.shape

import com.viewcompose.ui.unit.UiDp

/**
 * 角形状族，描述圆角或切角。
 * Corner family describing rounded or cut corners.
 */
enum class UiCornerFamily {
    Rounded,
    Cut,
}

sealed interface UiCornerSize {
    data class Absolute(
        val size: UiDp,
    ) : UiCornerSize {
        init {
            require(size >= UiDp.Zero) { "Corner size must be non-negative." }
        }
    }

    data class Relative(
        val fraction: Float,
    ) : UiCornerSize {
        init {
            require(fraction in 0f..1f) { "Relative corner size must be between 0 and 1." }
        }
    }
}

data class UiCorner(
    val family: UiCornerFamily,
    val size: UiCornerSize,
)

data class UiShape(
    val topStart: UiCorner,
    val topEnd: UiCorner,
    val bottomEnd: UiCorner,
    val bottomStart: UiCorner,
) {
    val isUniform: Boolean
        get() = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart

    val uniformAbsoluteSizeOrNull: UiDp?
        get() = if (isUniform) {
            (topStart.size as? UiCornerSize.Absolute)?.size
        } else {
            null
        }

    fun inset(amount: UiDp): UiShape {
        val resolvedAmount = if (amount < UiDp.Zero) UiDp.Zero else amount
        fun UiCorner.insetCorner(): UiCorner {
            val nextSize = when (val current = size) {
                is UiCornerSize.Absolute -> UiCornerSize.Absolute(
                    if (current.size > resolvedAmount) current.size - resolvedAmount else UiDp.Zero,
                )
                is UiCornerSize.Relative -> current
            }
            return copy(size = nextSize)
        }
        return UiShape(
            topStart = topStart.insetCorner(),
            topEnd = topEnd.insetCorner(),
            bottomEnd = bottomEnd.insetCorner(),
            bottomStart = bottomStart.insetCorner(),
        )
    }

    companion object {
        fun rounded(size: UiDp): UiShape {
            return uniform(
                family = UiCornerFamily.Rounded,
                size = UiCornerSize.Absolute(size),
            )
        }

        fun roundedRelative(fraction: Float): UiShape {
            return uniform(
                family = UiCornerFamily.Rounded,
                size = UiCornerSize.Relative(fraction),
            )
        }

        fun cut(size: UiDp): UiShape {
            return uniform(
                family = UiCornerFamily.Cut,
                size = UiCornerSize.Absolute(size),
            )
        }

        fun uniform(
            family: UiCornerFamily,
            size: UiCornerSize,
        ): UiShape {
            val corner = UiCorner(family = family, size = size)
            return UiShape(
                topStart = corner,
                topEnd = corner,
                bottomEnd = corner,
                bottomStart = corner,
            )
        }

        fun rounded(
            topStart: UiDp = UiDp.Zero,
            topEnd: UiDp = UiDp.Zero,
            bottomEnd: UiDp = UiDp.Zero,
            bottomStart: UiDp = UiDp.Zero,
        ): UiShape {
            return UiShape(
                topStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(topStart)),
                topEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(topEnd)),
                bottomEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(bottomEnd)),
                bottomStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(bottomStart)),
            )
        }
    }
}
