package com.viewcompose.ui.node

/**
 * SegmentedControl 的单个选项描述。
 * Descriptor for one SegmentedControl option.
 */
data class SegmentedControlItem(
    val label: String,
    val key: Any? = label,
)
