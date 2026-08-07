package com.viewcompose.ui.shape

import com.viewcompose.ui.unit.UiDp

/** Defines whether a corner follows a circular arc, continuous curve, or straight cut. */
enum class UiCornerFamily {
    Rounded,
    Continuous,
    Cut,
}

/**
 * Defines a corner size independently of a platform outline implementation.
 *
 * Consumers should handle this sealed hierarchy exhaustively. [Absolute] uses dp and [Relative]
 * uses a fraction of the renderer-resolved bounds.
 */
sealed interface UiCornerSize {
    /**
     * Uses a fixed density-independent corner size.
     *
     * @property size non-negative corner distance
     * @throws IllegalArgumentException if [size] is negative
     */
    data class Absolute(
        val size: UiDp,
    ) : UiCornerSize {
        init {
            require(size >= UiDp.Zero) { "Corner size must be non-negative." }
        }
    }

    /**
     * Uses a fraction of the renderer-resolved shape bounds.
     *
     * @property fraction corner fraction in the inclusive range `0.0..1.0`
     * @throws IllegalArgumentException if [fraction] is outside `0.0..1.0`
     */
    data class Relative(
        val fraction: Float,
    ) : UiCornerSize {
        init {
            require(fraction in 0f..1f) { "Relative corner size must be between 0 and 1." }
        }
    }
}

/**
 * Describes one logical corner of a shape.
 *
 * @property family geometry used to construct the corner
 * @property size absolute or relative size consumed by the renderer
 */
data class UiCorner(
    val family: UiCornerFamily,
    val size: UiCornerSize,
)

/**
 * Describes four logical corners without binding them to left-to-right coordinates.
 *
 * Start/end corners are resolved using the node's layout direction. The value is immutable and
 * can be shared across VNodes and render passes.
 *
 * @property topStart top-start logical corner
 * @property topEnd top-end logical corner
 * @property bottomEnd bottom-end logical corner
 * @property bottomStart bottom-start logical corner
 */
data class UiShape(
    val topStart: UiCorner,
    val topEnd: UiCorner,
    val bottomEnd: UiCorner,
    val bottomStart: UiCorner,
) {
    /** Whether all four corners have equal family and size values. */
    val isUniform: Boolean
        get() = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart

    /**
     * Returns the absolute size shared by all corners, or `null` for a non-uniform or relative shape.
     */
    val uniformAbsoluteSizeOrNull: UiDp?
        get() = if (isUniform) {
            (topStart.size as? UiCornerSize.Absolute)?.size
        } else {
            null
        }

    /**
     * Returns a shape whose absolute corner sizes are reduced by [amount].
     *
     * Negative amounts are coerced to zero. Absolute corners never become negative; relative
     * corners are retained because their physical size depends on the final bounds.
     *
     * @param amount density-independent inset to subtract from absolute corners
     * @return a new inset shape
     */
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

    /** Creates common uniform and per-corner shapes. */
    companion object {
        /**
         * Creates a uniform rounded shape with an absolute [size].
         *
         * @param size non-negative radius for every corner
         * @return a uniform rounded shape
         * @throws IllegalArgumentException if [size] is negative
         */
        fun rounded(size: UiDp): UiShape {
            return uniform(
                family = UiCornerFamily.Rounded,
                size = UiCornerSize.Absolute(size),
            )
        }

        /**
         * Creates a uniform rounded shape sized relative to its rendered bounds.
         *
         * @param fraction corner fraction in the inclusive range `0.0..1.0`
         * @return a uniform relative rounded shape
         * @throws IllegalArgumentException if [fraction] is outside `0.0..1.0`
         */
        fun roundedRelative(fraction: Float): UiShape {
            return uniform(
                family = UiCornerFamily.Rounded,
                size = UiCornerSize.Relative(fraction),
            )
        }

        /**
         * Creates a uniform continuous-corner shape with an absolute [size].
         *
         * Continuous corners use a renderer-owned superellipse-like curve. Renderers that cannot
         * execute that geometry must fall back to the closest rounded corner without changing the
         * component bounds.
         *
         * @param size non-negative corner extent for every corner
         * @return a uniform continuous-corner shape
         * @throws IllegalArgumentException if [size] is negative
         */
        fun continuous(size: UiDp): UiShape {
            return uniform(
                family = UiCornerFamily.Continuous,
                size = UiCornerSize.Absolute(size),
            )
        }

        /**
         * Creates a uniform cut-corner shape with an absolute [size].
         *
         * @param size non-negative cut distance for every corner
         * @return a uniform cut-corner shape
         * @throws IllegalArgumentException if [size] is negative
         */
        fun cut(size: UiDp): UiShape {
            return uniform(
                family = UiCornerFamily.Cut,
                size = UiCornerSize.Absolute(size),
            )
        }

        /**
         * Creates a shape that applies one [family] and [size] to every corner.
         *
         * @param family geometry used for every corner
         * @param size size used for every corner
         * @return a shape with four equal corners
         */
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

        /**
         * Creates a rounded shape with independently sized logical corners.
         *
         * @param topStart non-negative top-start radius
         * @param topEnd non-negative top-end radius
         * @param bottomEnd non-negative bottom-end radius
         * @param bottomStart non-negative bottom-start radius
         * @return a rounded shape containing the supplied corner sizes
         * @throws IllegalArgumentException if any size is negative
         */
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
