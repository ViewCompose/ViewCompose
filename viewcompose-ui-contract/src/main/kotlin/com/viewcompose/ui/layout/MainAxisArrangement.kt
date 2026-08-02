package com.viewcompose.ui.layout

/**
 * Distributes children on a row or column main axis.
 *
 * `Start`, `Center`, and `End` place the packed child group. `SpaceBetween` places space only
 * between children, `SpaceAround` assigns half-sized edge spaces, and `SpaceEvenly` uses equal
 * space at every gap and edge. Horizontal start/end placement follows layout direction.
 */
enum class MainAxisArrangement {
    Start,
    Center,
    End,
    SpaceBetween,
    SpaceAround,
    SpaceEvenly,
}
