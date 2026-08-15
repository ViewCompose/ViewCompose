package com.viewcompose.ui.node

/**
 * Describes one stable option in a segmented control.
 *
 * @property key semantic option identity that remains stable across label and locale changes
 * @property label user-visible localized option label
 * @property enabled whether this option accepts selection input
 */
data class SegmentedControlItem(
    val key: Any,
    val label: String,
    val enabled: Boolean = true,
)
