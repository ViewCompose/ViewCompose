package com.viewcompose.ui.state

/**
 * 分页容器的跨平台状态句柄，保存当前页快照并桥接 renderer 的滚动命令。
 * Cross-platform state handle for paged containers, storing page snapshots and bridging renderer scroll commands.
 */
class PagerState {
    private val listeners = linkedSetOf<(Int, Float) -> Unit>()
    private var connector: PagerConnector? = null

    var currentPage: Int = 0
        private set
    var pageOffset: Float = 0f
        private set

    /**
     * 请求 renderer 滚动到指定页。
     * Requests the renderer to scroll to the given page.
     */
    fun scrollToPage(page: Int) {
        connector?.scrollToPage(page)
    }

    /**
     * renderer 回传当前页和页内偏移时更新快照并通知监听者。
     * Updates the snapshot from renderer-reported page/offset values and notifies listeners.
     */
    fun updateFromPager(
        currentPage: Int,
        pageOffset: Float,
    ) {
        if (this.currentPage == currentPage && this.pageOffset == pageOffset) {
            return
        }
        this.currentPage = currentPage
        this.pageOffset = pageOffset
        listeners.forEach { listener ->
            listener(currentPage, pageOffset)
        }
    }

    /**
     * 监听分页快照变化；调用方负责在不需要时移除。
     * Listens for page snapshot changes; callers are responsible for removing the listener.
     */
    fun addOnPageSnapshotListener(
        listener: (Int, Float) -> Unit,
    ) {
        listeners += listener
    }

    fun removeOnPageSnapshotListener(
        listener: (Int, Float) -> Unit,
    ) {
        listeners -= listener
    }

    fun attach(connector: PagerConnector?) {
        this.connector = connector
    }
}

/**
 * 分页状态与平台 renderer 之间的命令桥接。
 * Command bridge between PagerState and the platform renderer.
 */
interface PagerConnector {
    fun scrollToPage(page: Int)
}
