package com.viewcompose.ui.node

/**
 * Pager/Tab 组合中一页的稳定内容描述。
 * Stable page descriptor used by pager/tab compositions.
 */
class TabPage(
    val title: String,
    val item: LazyListItem,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabPage) return false
        return title == other.title && item == other.item
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + item.hashCode()
        return result
    }
}
