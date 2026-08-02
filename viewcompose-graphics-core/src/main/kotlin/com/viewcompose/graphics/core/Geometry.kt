package com.viewcompose.graphics.core

/**
 * Represents a point or vector in the current two-dimensional drawing coordinate space.
 *
 * Android renderers interpret values as physical pixels unless a preceding transform changes the
 * coordinate space.
 *
 * @property x horizontal coordinate, increasing toward the right in the default Android space
 * @property y vertical coordinate, increasing downward in the default Android space
 */
data class Offset(
    val x: Float,
    val y: Float,
) {
    /** Common offset constants. */
    companion object {
        /** Point at the coordinate-space origin. */
        val Zero = Offset(0f, 0f)
    }
}

/**
 * Represents width and height in the current drawing coordinate space.
 *
 * The model does not reject negative or non-finite dimensions; renderers define how invalid bounds
 * are handled.
 *
 * @property width horizontal extent, normally in physical pixels
 * @property height vertical extent, normally in physical pixels
 */
data class Size(
    val width: Float,
    val height: Float,
) {
    /** Common size constants. */
    companion object {
        /** Size whose width and height are both zero. */
        val Zero = Size(0f, 0f)
    }
}

/**
 * Represents an axis-aligned rectangle in the current drawing coordinate space.
 *
 * Edges are stored unchanged. Inverted or non-finite rectangles are not rejected, so [width] or
 * [height] can be negative or non-finite.
 *
 * @property left horizontal coordinate of the left edge
 * @property top vertical coordinate of the top edge
 * @property right horizontal coordinate of the right edge
 * @property bottom vertical coordinate of the bottom edge
 */
data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Signed horizontal extent computed as `right - left`. */
    val width: Float
        get() = right - left

    /** Signed vertical extent computed as `bottom - top`. */
    val height: Float
        get() = bottom - top

    /** Common rectangle constants. */
    companion object {
        /** Empty rectangle at the coordinate-space origin. */
        val Zero = Rect(0f, 0f, 0f, 0f)
    }
}

/**
 * Represents the horizontal and vertical radii of one rounded corner.
 *
 * Values are stored unchanged; renderers may clamp negative or oversized radii.
 *
 * @property x horizontal radius in drawing-coordinate units
 * @property y vertical radius in drawing-coordinate units
 */
data class Radius(
    val x: Float,
    val y: Float,
) {
    /** Common radius constants. */
    companion object {
        /** Corner with no horizontal or vertical curvature. */
        val Zero = Radius(0f, 0f)
    }
}

/**
 * Describes an axis-aligned rounded rectangle with independent elliptical corner radii.
 *
 * @property rect outer rectangle bounds
 * @property topLeft radius for the top-left corner
 * @property topRight radius for the top-right corner
 * @property bottomRight radius for the bottom-right corner
 * @property bottomLeft radius for the bottom-left corner
 */
data class RoundRect(
    val rect: Rect,
    val topLeft: Radius = Radius.Zero,
    val topRight: Radius = Radius.Zero,
    val bottomRight: Radius = Radius.Zero,
    val bottomLeft: Radius = Radius.Zero,
)

/**
 * Stores a row-major 3-by-3 matrix for platform-neutral canvas transformation.
 *
 * The constructor copies the input array. The public [values] property is also the independent copy
 * owned by this instance, but callers can mutate that array after construction; do not mutate a
 * matrix once it has been placed in an immutable draw command or used as a cache key.
 *
 * @param values exactly nine row-major matrix coefficients
 * @throws IllegalArgumentException if [values] does not contain exactly nine elements
 */
class Matrix3(
    values: FloatArray = identityValues(),
) {
    /** Mutable nine-element backing snapshot owned by this matrix instance. */
    val values: FloatArray = values.copyOf()

    init {
        require(this.values.size == 9) { "Matrix3 requires 9 values." }
    }

    /** Compares matrix coefficients by content rather than array identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix3) return false
        return values.contentEquals(other.values)
    }

    /** Computes a content hash from the current matrix coefficients. */
    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    /** Matrix factories. */
    companion object {
        /** Returns a new identity matrix on every call. */
        fun identity(): Matrix3 = Matrix3(identityValues())

        private fun identityValues(): FloatArray {
            return floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            )
        }
    }
}
