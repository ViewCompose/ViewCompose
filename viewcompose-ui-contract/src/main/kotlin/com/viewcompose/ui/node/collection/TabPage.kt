package com.viewcompose.ui.node

/**
 * Describes one stable page shared by pager and tab compositions.
 *
 * Equality uses [title] and the semantic fields of [item], excluding session callback identity.
 *
 * @property title user-visible localized tab title
 * @property item lazy item that owns page identity and child-session content
 */
class TabPage(
    val title: String,
    val item: LazyListItem,
) {
    /**
     * Returns whether [other] has an equal title and semantic item descriptor.
     *
     * @param other value to compare
     * @return `true` for an equivalent page descriptor
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabPage) return false
        return title == other.title && item == other.item
    }

    /**
     * Returns the combined hash of [title] and [item].
     *
     * @return hash consistent with [equals]
     */
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + item.hashCode()
        return result
    }
}
