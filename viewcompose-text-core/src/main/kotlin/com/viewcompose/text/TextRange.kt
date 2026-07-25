package com.viewcompose.text

/**
 * A directional UTF-16 range used by platform text editors.
 *
 * [start] and [end] intentionally preserve selection direction. Use [min] and [max] when an
 * ordered range is required.
 */
data class TextRange(
    val start: Int,
    val end: Int = start,
) {
    init {
        require(start >= 0) { "TextRange start must be non-negative." }
        require(end >= 0) { "TextRange end must be non-negative." }
    }

    val collapsed: Boolean
        get() = start == end

    val min: Int
        get() = minOf(start, end)

    val max: Int
        get() = maxOf(start, end)

    val length: Int
        get() = max - min
}
