package com.viewcompose.animation.core

/**
 * Converts a domain value to and from independently interpolated floating-point dimensions.
 *
 * Implementations must use a stable vector dimension for every value of [T]. [toVector] should
 * return an independently mutable array because the engine reads it as per-frame scratch data;
 * [fromVector] must not retain the supplied array. Conversion runs on the animation coroutine's
 * frame context and may occur once per endpoint per sample, so implementations should be fast,
 * deterministic, and free of blocking work.
 *
 * @sample com.viewcompose.animation.core.samples.customAnimationConverterSample
 *
 * @param T domain value represented by the converter
 */
interface AnimationConverter<T> {
    /**
     * Creates the interpolation vector for [value].
     *
     * @param value domain value to decompose
     * @return an independently mutable vector with the converter's stable dimension count
     */
    fun toVector(value: T): FloatArray

    /**
     * Reconstructs a domain value from an interpolated [vector].
     *
     * @param vector per-sample dimensions owned by the caller
     * @return a domain value reconstructed without retaining [vector]
     */
    fun fromVector(vector: FloatArray): T
}

/** Provides stateless converters for common scalar and Android-compatible packed values. */
object AnimationConverters {
    /**
     * Converts a [kotlin.Float] to one interpolation dimension.
     *
     * An empty vector converts to `0f`; extra dimensions are ignored.
     */
    val Float: AnimationConverter<kotlin.Float> = object : AnimationConverter<kotlin.Float> {
        override fun toVector(value: kotlin.Float): FloatArray = floatArrayOf(value)

        override fun fromVector(vector: FloatArray): kotlin.Float = vector.firstOrNull() ?: 0f
    }

    /**
     * Converts an [kotlin.Int] to one floating-point interpolation dimension.
     *
     * Reconstructed values truncate toward zero. An empty vector converts to `0`; extra dimensions
     * are ignored. Large integers may lose precision while represented as a [kotlin.Float].
     */
    val Int: AnimationConverter<kotlin.Int> = object : AnimationConverter<kotlin.Int> {
        override fun toVector(value: kotlin.Int): FloatArray = floatArrayOf(value.toFloat())

        override fun fromVector(vector: FloatArray): kotlin.Int = (vector.firstOrNull() ?: 0f).toInt()
    }

    /**
     * Converts a packed ARGB [kotlin.Int] into alpha, red, green, and blue dimensions.
     *
     * Reconstruction truncates and clamps each channel to `0..255`. Missing alpha defaults to 255;
     * missing color channels default to zero; extra dimensions are ignored. Interpolation is in
     * encoded channel space and is not gamma-correct or color-space aware.
     */
    val ColorInt: AnimationConverter<kotlin.Int> = object : AnimationConverter<kotlin.Int> {
        override fun toVector(value: kotlin.Int): FloatArray {
            val a = (value shr 24) and 0xFF
            val r = (value shr 16) and 0xFF
            val g = (value shr 8) and 0xFF
            val b = value and 0xFF
            return floatArrayOf(a.toFloat(), r.toFloat(), g.toFloat(), b.toFloat())
        }

        override fun fromVector(vector: FloatArray): kotlin.Int {
            val a = vector.getOrElse(0) { 255f }.toInt().coerceIn(0, 255)
            val r = vector.getOrElse(1) { 0f }.toInt().coerceIn(0, 255)
            val g = vector.getOrElse(2) { 0f }.toInt().coerceIn(0, 255)
            val b = vector.getOrElse(3) { 0f }.toInt().coerceIn(0, 255)
            return ((a and 0xFF) shl 24) or
                ((r and 0xFF) shl 16) or
                ((g and 0xFF) shl 8) or
                (b and 0xFF)
        }
    }
}
