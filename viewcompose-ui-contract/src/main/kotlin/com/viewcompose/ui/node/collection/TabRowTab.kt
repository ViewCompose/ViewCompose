package com.viewcompose.ui.node.collection

import com.viewcompose.ui.node.LazyListItem

/**
 * TabRow 中单个 tab 的稳定描述。
 * Stable descriptor for one tab in a TabRow.
 */
class TabRowTab(
    val item: LazyListItem,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabRowTab) return false
        return item == other.item
    }

    override fun hashCode(): Int = item.hashCode()
}
