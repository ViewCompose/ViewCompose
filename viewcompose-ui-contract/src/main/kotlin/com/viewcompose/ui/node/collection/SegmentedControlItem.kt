package com.viewcompose.ui.node

/**
 * Describes one stable option in a segmented control.
 *
 * @property label user-visible localized option label
 * @property key semantic selection identity; defaults to [label]
 */
data class SegmentedControlItem(
    val label: String,
    val key: Any? = label,
)
