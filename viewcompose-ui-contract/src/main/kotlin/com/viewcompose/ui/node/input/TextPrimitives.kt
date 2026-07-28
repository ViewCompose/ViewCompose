package com.viewcompose.ui.node

/**
 * 文本超出可用空间时的处理策略。
 * Strategy for text that exceeds its available space.
 */
enum class TextOverflow {
    Clip,
    Ellipsis,
}

enum class TextAlign {
    Start,
    Center,
    End,
}

enum class TextDecoration {
    None,
    Underline,
    LineThrough,
    UnderlineLineThrough,
}
