package com.viewcompose.text

/**
 * 平台文本编辑器使用的有方向 UTF-16 范围。
 * A directional UTF-16 range used by platform text editors.
 *
 * [start] 和 [end] 会刻意保留选择方向；需要有序范围时使用 [min] 和 [max]。
 * [start] and [end] intentionally preserve selection direction. Use [min] and [max] when an ordered
 * range is required.
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
