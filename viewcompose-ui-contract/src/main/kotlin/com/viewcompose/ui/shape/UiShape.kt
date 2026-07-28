package com.viewcompose.ui.shape

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
        val pixels: Int,
    ) : UiCornerSize {
        init {
            require(pixels >= 0) { "Corner size must be non-negative." }
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

    val uniformAbsoluteSizeOrNull: Int?
        get() = if (isUniform) {
            (topStart.size as? UiCornerSize.Absolute)?.pixels
        } else {
            null
        }

    fun inset(pixels: Int): UiShape {
        val amount = pixels.coerceAtLeast(0)
        fun UiCorner.insetCorner(): UiCorner {
            val nextSize = when (val current = size) {
                is UiCornerSize.Absolute -> UiCornerSize.Absolute(
                    (current.pixels - amount).coerceAtLeast(0),
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
        fun rounded(size: Int): UiShape {
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

        fun cut(size: Int): UiShape {
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
            topStart: Int = 0,
            topEnd: Int = 0,
            bottomEnd: Int = 0,
            bottomStart: Int = 0,
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
