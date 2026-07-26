package com.viewcompose.widget.core

import com.viewcompose.ui.state.LazyListState

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
