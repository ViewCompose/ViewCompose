package com.viewcompose.ui.node

/**
 * 文本超出可用空间时的处理策略。
 * Strategy for text that exceeds its available space.
 */
enum class TextOverflow {
    Clip,
    Ellipsis,
}

/** Logical horizontal alignment of text inside its laid-out bounds. */
enum class TextAlign {
    Start,
    Center,
    End,
}

/** Decoration lines applied to rendered text. */
enum class TextDecoration {
    None,
    Underline,
    LineThrough,
    UnderlineLineThrough,
}
