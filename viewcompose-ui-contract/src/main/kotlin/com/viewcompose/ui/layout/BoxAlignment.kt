package com.viewcompose.ui.layout

/**
 * Selects one of the nine logical alignment positions available to a box child.
 *
 * Start/end positions are resolved from the current layout direction; center positions are
 * direction-independent.
 */
enum class BoxAlignment {
    TopStart,
    TopCenter,
    TopEnd,
    CenterStart,
    Center,
    CenterEnd,
    BottomStart,
    BottomCenter,
    BottomEnd,
}
