package com.viewcompose.ui.node.collection

import com.viewcompose.ui.node.LazyListItem

/**
 * Wraps a lazy-item descriptor as one stable tab-row entry.
 *
 * @property item semantic identity and child-session content for the tab
 */
class TabRowTab(
    val item: LazyListItem,
) {
    /**
     * Returns whether [other] wraps a semantically equal item.
     *
     * @param other value to compare
     * @return `true` for an equivalent tab descriptor
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabRowTab) return false
        return item == other.item
    }

    /**
     * Returns the semantic item hash.
     *
     * @return hash consistent with [equals]
     */
    override fun hashCode(): Int = item.hashCode()
}
