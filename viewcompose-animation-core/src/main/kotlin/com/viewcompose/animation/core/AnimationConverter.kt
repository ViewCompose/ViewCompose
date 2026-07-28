package com.viewcompose.animation.core

/**
 * 在业务值和可逐维插值 Float 向量之间转换。
 * Converts between domain values and Float vectors that can be interpolated component by component.
 */
interface AnimationConverter<T> {
    fun toVector(value: T): FloatArray

    fun fromVector(vector: FloatArray): T
}

/**
 * 框架内置的常用动画值 converter。
 * Built-in converters for common animated value types.
 */
object AnimationConverters {
    val Float: AnimationConverter<Float> = object : AnimationConverter<Float> {
        override fun toVector(value: Float): FloatArray = floatArrayOf(value)

        override fun fromVector(vector: FloatArray): Float = vector.firstOrNull() ?: 0f
    }

    val Int: AnimationConverter<Int> = object : AnimationConverter<Int> {
        override fun toVector(value: Int): FloatArray = floatArrayOf(value.toFloat())

        override fun fromVector(vector: FloatArray): Int = (vector.firstOrNull() ?: 0f).toInt()
    }

    /**
     * ARGB int converter，按 A/R/G/B 四个通道独立插值。
     * ARGB int converter that interpolates A/R/G/B channels independently.
     */
    val ColorInt: AnimationConverter<Int> = object : AnimationConverter<Int> {
        override fun toVector(value: Int): FloatArray {
            val a = (value shr 24) and 0xFF
            val r = (value shr 16) and 0xFF
            val g = (value shr 8) and 0xFF
            val b = value and 0xFF
            return floatArrayOf(a.toFloat(), r.toFloat(), g.toFloat(), b.toFloat())
        }

        override fun fromVector(vector: FloatArray): Int {
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
