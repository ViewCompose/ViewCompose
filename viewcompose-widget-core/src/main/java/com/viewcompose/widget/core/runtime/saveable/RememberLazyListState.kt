package com.viewcompose.widget.core

import com.viewcompose.ui.state.LazyListState

/**
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
 * Remembers lazy-collection state and restores its first visible item and pixel offset.
 *
 * The renderer writes actual scroll position back to the returned state. Both initial arguments
 * are used only when no saved value exists for this call site.
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
