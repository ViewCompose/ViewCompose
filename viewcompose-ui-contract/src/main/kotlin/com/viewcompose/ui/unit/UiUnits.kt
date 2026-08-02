package com.viewcompose.ui.unit

import kotlin.math.roundToInt

/**
 * Density-independent layout distance.
 *
 * The logical value is retained in the VNode tree and is converted to pixels only by a renderer.
 * Arithmetic does not clamp or validate finite values; callers that cross a rendering boundary
 * must supply values accepted by the consuming API.
 *
 * @property value logical density-independent pixel value
 */
@JvmInline
value class UiDp(
    val value: Float,
) : Comparable<UiDp> {
    /**
     * Compares this distance with [other] by their logical values.
     *
     * @param other distance to compare
     * @return a negative value, zero, or a positive value as this value is less than, equal to, or
     * greater than [other]
     */
    override fun compareTo(other: UiDp): Int = value.compareTo(other.value)

    /**
     * Returns the sum of this distance and [other].
     *
     * @param other distance to add
     * @return a new distance containing the arithmetic sum
     */
    operator fun plus(other: UiDp): UiDp = UiDp(value + other.value)

    /**
     * Returns this distance minus [other].
     *
     * @param other distance to subtract
     * @return a new distance containing the arithmetic difference
     */
    operator fun minus(other: UiDp): UiDp = UiDp(value - other.value)

    /**
     * Returns this distance multiplied by [scale].
     *
     * @param scale floating-point multiplier
     * @return the scaled distance without coercion
     */
    operator fun times(scale: Float): UiDp = UiDp(value * scale)

    /**
     * Returns this distance divided by [divisor] using floating-point arithmetic.
     *
     * @param divisor integer divisor; zero follows JVM floating-point infinity/NaN semantics
     * @return the quotient without coercion
     */
    operator fun div(divisor: Int): UiDp = UiDp(value / divisor)

    /**
     * Returns this distance divided by [divisor] using floating-point arithmetic.
     *
     * @param divisor floating-point divisor; zero follows JVM floating-point infinity/NaN semantics
     * @return the quotient without coercion
     */
    operator fun div(divisor: Float): UiDp = UiDp(value / divisor)

    /** Provides common density-independent distance values. */
    companion object {
        /** A distance of zero density-independent pixels. */
        val Zero = UiDp(0f)
    }
}

/**
 * Scale-independent text size.
 *
 * The logical value is retained until a renderer applies the current density and font scale.
 *
 * @property value logical scale-independent pixel value
 */
@JvmInline
value class UiSp(
    val value: Float,
) : Comparable<UiSp> {
    /**
     * Compares this text size with [other] by their logical values.
     *
     * @param other text size to compare
     * @return a negative value, zero, or a positive value as this value is less than, equal to, or
     * greater than [other]
     */
    override fun compareTo(other: UiSp): Int = value.compareTo(other.value)

    /** Provides common scale-independent text sizes. */
    companion object {
        /** A text size of zero scale-independent pixels. */
        val Zero = UiSp(0f)
    }
}

/**
 * Selects how a renderer resolves one layout dimension.
 *
 * Consumers should handle this sealed hierarchy exhaustively: [Exact] requests a logical size,
 * while [MatchParent] requests the maximum size offered by the parent.
 */
sealed interface UiDimension {
    /**
     * Requests an exact logical dimension.
     *
     * @property value requested density-independent size
     */
    data class Exact(
        val value: UiDp,
    ) : UiDimension

    /** Requests the maximum dimension offered by the parent. */
    data object MatchParent : UiDimension
}

/**
 * Converts this integer to a density-independent distance without applying device density.
 *
 * @receiver logical density-independent pixel count
 * @return this value represented as [UiDp]
 */
val Int.dp: UiDp
    get() = UiDp(toFloat())

/**
 * Converts this floating-point value to a density-independent distance.
 *
 * @receiver logical density-independent pixel value
 * @return this value represented as [UiDp]
 */
val Float.dp: UiDp
    get() = UiDp(this)

/**
 * Converts this integer to a scale-independent text size without applying font scale.
 *
 * @receiver logical scale-independent pixel count
 * @return this value represented as [UiSp]
 */
val Int.sp: UiSp
    get() = UiSp(toFloat())

/**
 * Converts this floating-point value to a scale-independent text size.
 *
 * @receiver logical scale-independent pixel value
 * @return this value represented as [UiSp]
 */
val Float.sp: UiSp
    get() = UiSp(this)

/**
 * Density used at the renderer boundary.
 *
 * [fontScale] is deliberately stored separately from [density]. Android's scaled density is a
 * derived platform value, not a second independent logical input.
 *
 * @property density positive finite physical-pixels-per-dp scale
 * @property fontScale positive finite multiplier applied to text scaling
 * @throws IllegalArgumentException if either scale is non-finite or not greater than zero
 */
data class UiDensity(
    val density: Float,
    val fontScale: Float,
) {
    init {
        require(density > 0f && density.isFinite()) {
            "UiDensity.density must be finite and greater than zero."
        }
        require(fontScale > 0f && fontScale.isFinite()) {
            "UiDensity.fontScale must be finite and greater than zero."
        }
    }

    /** Effective physical-pixels-per-sp scale derived from [density] and [fontScale]. */
    val scaledDensity: Float
        get() = density * fontScale

    /**
     * Converts [value] to physical pixels and rounds to the nearest integer.
     *
     * @param value density-independent distance to convert
     * @return rounded physical-pixel count
     */
    fun roundToPx(value: UiDp): Int = (value.value * density).roundToInt()

    /**
     * Converts [value] to physical pixels without rounding.
     *
     * @param value density-independent distance to convert
     * @return physical-pixel value
     */
    fun toPx(value: UiDp): Float = value.value * density

    /**
     * Converts [value] with [scaledDensity] and rounds to the nearest integer.
     *
     * @param value scale-independent text size to convert
     * @return rounded physical-pixel count
     */
    fun roundToPx(value: UiSp): Int = (value.value * scaledDensity).roundToInt()

    /**
     * Converts [value] with [scaledDensity] without rounding.
     *
     * @param value scale-independent text size to convert
     * @return physical-pixel value
     */
    fun toPx(value: UiSp): Float = value.value * scaledDensity

    /** Provides the fallback density used without a host environment. */
    companion object {
        /** Unit density and unit font scale. */
        val Default = UiDensity(
            density = 1f,
            fontScale = 1f,
        )
    }
}
