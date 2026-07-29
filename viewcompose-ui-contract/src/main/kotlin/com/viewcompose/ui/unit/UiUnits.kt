package com.viewcompose.ui.unit

import kotlin.math.roundToInt

/**
 * Density-independent layout distance.
 *
 * The logical value is retained in the VNode tree and is converted to pixels only by a renderer.
 */
@JvmInline
value class UiDp(
    val value: Float,
) : Comparable<UiDp> {
    override fun compareTo(other: UiDp): Int = value.compareTo(other.value)

    operator fun plus(other: UiDp): UiDp = UiDp(value + other.value)

    operator fun minus(other: UiDp): UiDp = UiDp(value - other.value)

    operator fun times(scale: Float): UiDp = UiDp(value * scale)

    companion object {
        val Zero = UiDp(0f)
    }
}

/**
 * Scale-independent text size.
 *
 * The logical value is retained until a renderer applies the current density and font scale.
 */
@JvmInline
value class UiSp(
    val value: Float,
) : Comparable<UiSp> {
    override fun compareTo(other: UiSp): Int = value.compareTo(other.value)

    companion object {
        val Zero = UiSp(0f)
    }
}

val Int.dp: UiDp
    get() = UiDp(toFloat())

val Float.dp: UiDp
    get() = UiDp(this)

val Int.sp: UiSp
    get() = UiSp(toFloat())

val Float.sp: UiSp
    get() = UiSp(this)

/**
 * Density used at the renderer boundary.
 *
 * [fontScale] is deliberately stored separately from [density]. Android's scaled density is a
 * derived platform value, not a second independent logical input.
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

    val scaledDensity: Float
        get() = density * fontScale

    fun roundToPx(value: UiDp): Int = (value.value * density).roundToInt()

    fun toPx(value: UiDp): Float = value.value * density

    fun roundToPx(value: UiSp): Int = (value.value * scaledDensity).roundToInt()

    fun toPx(value: UiSp): Float = value.value * scaledDensity

    @Deprecated("Keep UiDp logical and resolve it only at the renderer boundary.")
    fun dp(value: Int): Int = roundToPx(UiDp(value.toFloat()))

    @Deprecated("Keep UiSp logical and resolve it only at the renderer boundary.")
    fun sp(value: Int): Int = roundToPx(UiSp(value.toFloat()))

    companion object {
        val Default = UiDensity(
            density = 1f,
            fontScale = 1f,
        )
    }
}
