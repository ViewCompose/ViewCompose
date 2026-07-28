package com.viewcompose.widget.core

import com.viewcompose.ui.state.LazyListState

/**
 * 保存懒列表的首个可见项与滚动偏移，避免宿主重建后回到列表顶部。
 * Saves the first visible item and scroll offset so host recreation does not reset the list to the top.
 */
private val LazyListStateSaver = listSaver<LazyListState>(
    save = { state ->
        listOf(
            state.firstVisibleItemIndex,
            state.firstVisibleItemScrollOffset,
        )
    },
    restore = { saved ->
        require(saved.size == 2) {
            "LazyListState requires an item index and scroll offset."
        }
        LazyListState(
            initialFirstVisibleItemIndex = saved[0].requireSavedInt("item index"),
            initialFirstVisibleItemScrollOffset = saved[1].requireSavedInt("scroll offset"),
        )
    },
)

/**
 * Remembers a lazy collection state and restores its first visible item and pixel offset.
 */
/**
 * 创建可保存的懒列表状态，供 renderer 在实际 RecyclerView 滚动后回写当前位置。
 * Creates saveable lazy-list state that the renderer updates after real RecyclerView scrolling.
 */
fun rememberLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    return rememberSaveable(
        saver = LazyListStateSaver,
    ) {
        LazyListState(
            initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
        )
    }
}

private fun Any?.requireSavedInt(label: String): Int {
    require(this is Int) {
        "Saved LazyListState $label must be an Int, but was ${this?.javaClass?.name ?: "null"}."
    }
    return this
}
