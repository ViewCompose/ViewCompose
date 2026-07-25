package com.viewcompose.ui.state

/**
 * Controls a lazy collection and exposes the first visible item for state persistence.
 */
class LazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
) {
    private var connector: LazyListConnector? = null
    private var connectorIdentity: Any? = null
    private var position = LazyListPosition(
        index = initialFirstVisibleItemIndex.requireValidIndex(),
        scrollOffset = initialFirstVisibleItemScrollOffset.requireValidScrollOffset(),
    )

    val firstVisibleItemIndex: Int
        get() = currentPosition().index

    val firstVisibleItemScrollOffset: Int
        get() = currentPosition().scrollOffset

    fun scrollToPosition(index: Int) {
        scrollToPosition(
            index = index,
            scrollOffset = 0,
        )
    }

    fun scrollToPosition(
        index: Int,
        scrollOffset: Int,
    ) {
        position = LazyListPosition(
            index = index.requireValidIndex(),
            scrollOffset = scrollOffset.requireValidScrollOffset(),
        )
        connector?.scrollToPosition(
            index = index,
            scrollOffset = scrollOffset,
            smooth = false,
        )
    }

    fun smoothScrollToPosition(index: Int) {
        position = LazyListPosition(
            index = index.requireValidIndex(),
            scrollOffset = 0,
        )
        connector?.scrollToPosition(
            index = index,
            scrollOffset = 0,
            smooth = true,
        )
    }

    fun attach(connector: LazyListConnector?) {
        if (connector == null) {
            captureConnectorPosition()
            this.connector = null
            connectorIdentity = null
            return
        }
        if (connectorIdentity === connector.identity) {
            this.connector = connector
            return
        }
        captureConnectorPosition()
        this.connector = connector
        connectorIdentity = connector.identity
        connector.scrollToPosition(
            index = position.index,
            scrollOffset = position.scrollOffset,
            smooth = false,
        )
    }

    private fun currentPosition(): LazyListPosition {
        captureConnectorPosition()
        return position
    }

    private fun captureConnectorPosition() {
        connector?.currentPosition()?.let { current ->
            position = LazyListPosition(
                index = current.index.requireValidIndex(),
                scrollOffset = current.scrollOffset.requireValidScrollOffset(),
            )
        }
    }
}

interface LazyListConnector {
    val identity: Any
        get() = this

    fun scrollToPosition(
        index: Int,
        smooth: Boolean,
    )

    fun scrollToPosition(
        index: Int,
        scrollOffset: Int,
        smooth: Boolean,
    ) {
        scrollToPosition(
            index = index,
            smooth = smooth,
        )
    }

    fun currentPosition(): LazyListPosition? = null
}

data class LazyListPosition(
    val index: Int,
    val scrollOffset: Int,
)

private fun Int.requireValidIndex(): Int {
    require(this >= 0) {
        "Lazy list item index must be non-negative, but was $this."
    }
    return this
}

private fun Int.requireValidScrollOffset(): Int {
    require(this >= 0) {
        "Lazy list scroll offset must be non-negative, but was $this."
    }
    return this
}
