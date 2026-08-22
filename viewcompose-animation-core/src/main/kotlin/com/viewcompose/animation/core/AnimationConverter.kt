package com.viewcompose.animation.core

/**
 * Converts an animated value and its velocity to reusable floating-point vectors.
 *
 * The value domain [T] and velocity domain [V] are separate because a position-like value does not
 * always form a vector space. For example, a packed ARGB color uses [Int] as its value while its
 * signed per-channel tangent uses [ArgbChannels]. Linear domains may use the same type for both
 * parameters.
 *
 * Every conversion must write exactly [vectorSize] finite components into its `destination` without
 * retaining it. [convertFromVector] and [convertVelocityFromVector] must not retain the supplied
 * array. Conversions run on the animation coroutine's frame context and must be deterministic,
 * non-blocking, and allocation-conscious.
 *
 * [visibilityThreshold] is a positive per-component delta in [V]. The engine converts it once per
 * run and requires every component to be finite and greater than zero. [zeroVelocity] must convert
 * to an all-zero vector.
 *
 * @sample com.viewcompose.animation.core.samples.customAnimationConverterSample
 *
 * @param T immutable animated-value domain
 * @param V immutable tangent/delta domain; [AnimationVelocity] supplies the per-second meaning
 */
interface AnimationConverter<T, V> {
    /** Returns the positive, stable number of vector components used by every conversion. */
    val vectorSize: Int

    /** Returns the velocity value that converts to zero in every vector component. */
    val zeroVelocity: V

    /** Returns the positive finite per-component position threshold in domain units. */
    val visibilityThreshold: V

    /**
     * Writes [value] into the caller-owned [destination] vector.
     *
     * @param value animated value to decompose
     * @param destination mutable vector whose size is exactly [vectorSize]
     * @throws IllegalArgumentException if [destination] has an incompatible size
     */
    fun convertToVector(value: T, destination: FloatArray)

    /**
     * Reconstructs an animated value from [vector].
     *
     * @param vector complete value vector owned by the caller
     * @return immutable animated value without retaining [vector]
     * @throws IllegalArgumentException if [vector] has an incompatible size
     */
    fun convertFromVector(vector: FloatArray): T

    /**
     * Writes [velocity] into the caller-owned [destination] vector in units per second.
     *
     * @param velocity typed velocity or delta value to decompose
     * @param destination mutable vector whose size is exactly [vectorSize]
     * @throws IllegalArgumentException if [destination] has an incompatible size
     */
    fun convertVelocityToVector(velocity: V, destination: FloatArray)

    /**
     * Reconstructs a typed velocity from [vector] components measured per second.
     *
     * @param vector complete velocity vector owned by the caller
     * @return immutable typed velocity without retaining [vector]
     * @throws IllegalArgumentException if [vector] has an incompatible size
     */
    fun convertVelocityFromVector(vector: FloatArray): V
}

/**
 * Stores signed encoded-ARGB channel components for deltas and typed velocity payloads.
 *
 * This is a tangent/delta domain, not a packed color. Components are not clamped so overshoot and
 * reverse motion remain representable even though the animated color itself is clamped to
 * `0..255`. [AnimationVelocity] interprets an instance as channel units per second; the converter's
 * visibility threshold interprets the same shape as channel-unit deltas.
 *
 * @sample com.viewcompose.animation.core.samples.colorVelocityDomainSample
 *
 * @property alpha signed alpha-channel component
 * @property red signed red-channel component
 * @property green signed green-channel component
 * @property blue signed blue-channel component
 */
data class ArgbChannels(
    val alpha: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
)

/** Provides stateless converters for common scalar and Android-compatible packed values. */
object AnimationConverters {
    /** Converts a [kotlin.Float] value and velocity to one vector component. */
    val Float: AnimationConverter<kotlin.Float, kotlin.Float> =
        object : AnimationConverter<kotlin.Float, kotlin.Float> {
            override val vectorSize: Int = 1
            override val zeroVelocity: kotlin.Float = 0f
            override val visibilityThreshold: kotlin.Float = 0.01f

            override fun convertToVector(value: kotlin.Float, destination: FloatArray) {
                destination.requireSize(vectorSize)
                destination[0] = value
            }

            override fun convertFromVector(vector: FloatArray): kotlin.Float {
                vector.requireSize(vectorSize)
                return vector[0]
            }

            override fun convertVelocityToVector(
                velocity: kotlin.Float,
                destination: FloatArray,
            ) {
                destination.requireSize(vectorSize)
                destination[0] = velocity
            }

            override fun convertVelocityFromVector(vector: FloatArray): kotlin.Float {
                vector.requireSize(vectorSize)
                return vector[0]
            }
        }

    /**
     * Converts an [kotlin.Int] value to one component and exposes velocity as [kotlin.Float].
     *
     * Reconstructed values truncate toward zero. Large integers may lose precision while
     * represented as a [kotlin.Float].
     */
    val Int: AnimationConverter<kotlin.Int, kotlin.Float> =
        object : AnimationConverter<kotlin.Int, kotlin.Float> {
            override val vectorSize: Int = 1
            override val zeroVelocity: kotlin.Float = 0f
            override val visibilityThreshold: kotlin.Float = 1f

            override fun convertToVector(value: kotlin.Int, destination: FloatArray) {
                destination.requireSize(vectorSize)
                destination[0] = value.toFloat()
            }

            override fun convertFromVector(vector: FloatArray): kotlin.Int {
                vector.requireSize(vectorSize)
                return vector[0].toInt()
            }

            override fun convertVelocityToVector(
                velocity: kotlin.Float,
                destination: FloatArray,
            ) {
                destination.requireSize(vectorSize)
                destination[0] = velocity
            }

            override fun convertVelocityFromVector(vector: FloatArray): kotlin.Float {
                vector.requireSize(vectorSize)
                return vector[0]
            }
        }

    /**
     * Converts a packed ARGB [kotlin.Int] and signed [ArgbChannels] by encoded channel.
     *
     * Value reconstruction truncates and clamps each channel to `0..255`. Interpolation is in
     * encoded channel space and is not gamma-correct or color-space aware.
     */
    val ColorInt: AnimationConverter<kotlin.Int, ArgbChannels> =
        object : AnimationConverter<kotlin.Int, ArgbChannels> {
            override val vectorSize: Int = 4
            override val zeroVelocity: ArgbChannels = ArgbChannels(0f, 0f, 0f, 0f)
            override val visibilityThreshold: ArgbChannels = ArgbChannels(1f, 1f, 1f, 1f)

            override fun convertToVector(value: kotlin.Int, destination: FloatArray) {
                destination.requireSize(vectorSize)
                destination[0] = ((value shr 24) and 0xFF).toFloat()
                destination[1] = ((value shr 16) and 0xFF).toFloat()
                destination[2] = ((value shr 8) and 0xFF).toFloat()
                destination[3] = (value and 0xFF).toFloat()
            }

            override fun convertFromVector(vector: FloatArray): kotlin.Int {
                vector.requireSize(vectorSize)
                val a = vector[0].toInt().coerceIn(0, 255)
                val r = vector[1].toInt().coerceIn(0, 255)
                val g = vector[2].toInt().coerceIn(0, 255)
                val b = vector[3].toInt().coerceIn(0, 255)
                return ((a and 0xFF) shl 24) or
                    ((r and 0xFF) shl 16) or
                    ((g and 0xFF) shl 8) or
                    (b and 0xFF)
            }

            override fun convertVelocityToVector(
                velocity: ArgbChannels,
                destination: FloatArray,
            ) {
                destination.requireSize(vectorSize)
                destination[0] = velocity.alpha
                destination[1] = velocity.red
                destination[2] = velocity.green
                destination[3] = velocity.blue
            }

            override fun convertVelocityFromVector(vector: FloatArray): ArgbChannels {
                vector.requireSize(vectorSize)
                return ArgbChannels(
                    alpha = vector[0],
                    red = vector[1],
                    green = vector[2],
                    blue = vector[3],
                )
            }
        }
}

private fun FloatArray.requireSize(expected: Int) {
    require(size == expected) {
        "Animation vector size $size does not match converter vectorSize $expected."
    }
}
