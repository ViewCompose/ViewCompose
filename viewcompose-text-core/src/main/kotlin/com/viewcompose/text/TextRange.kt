package com.viewcompose.text

/**
 * Directional UTF-16 range used by platform text-editing contracts.
 *
 * [start] and [end] preserve selection direction and are independently non-negative. Use [min] and
 * [max] for ordered document operations. A range is not bound to a document; document and field
 * constructors validate upper bounds when they accept one.
 *
 * @property start directional anchor offset in UTF-16 code units
 * @property end directional active offset in UTF-16 code units
 */
data class TextRange(
    val start: Int,
    val end: Int = start,
) {
    init {
        require(start >= 0) { "TextRange start must be non-negative." }
        require(end >= 0) { "TextRange end must be non-negative." }
    }

    /** Whether both directional offsets identify the same cursor position. */
    val collapsed: Boolean
        get() = start == end

    /** Smaller ordered offset. */
    val min: Int
        get() = minOf(start, end)

    /** Larger ordered offset. */
    val max: Int
        get() = maxOf(start, end)

    /** Ordered range length in UTF-16 code units. */
    val length: Int
        get() = max - min
}
