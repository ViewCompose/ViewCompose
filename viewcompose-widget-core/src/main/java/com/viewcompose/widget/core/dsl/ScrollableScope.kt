package com.viewcompose.widget.core

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.state.PagerState

@UiDslMarker
class ScrollableScope internal constructor() {
    private val delegate = UiTreeBuilder()

    // ── 纵向滚动 ──

    fun <T> LazyColumn(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentPadding: Int = 0,
        spacing: Int = 0,
        state: LazyListState? = null,
        reverseLayout: Boolean = false,
        userScrollEnabled: Boolean = true,
        prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        focusFollowKeyboard: Boolean = false,
        modifier: Modifier = Modifier,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        with(delegate) {
            LazyColumn(
                items = items,
                key = key,
                contentType = contentType,
                contentPadding = contentPadding,
                spacing = spacing,
                state = state,
                reverseLayout = reverseLayout,
                userScrollEnabled = userScrollEnabled,
                prefetchPolicy = prefetchPolicy,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = focusFollowKeyboard,
                modifier = modifier,
                itemContent = itemContent,
            )
        }
    }

    fun LazyColumn(
        contentPadding: LazyContentPadding = LazyContentPadding.None,
        spacing: Int = 0,
        state: LazyListState? = null,
        reverseLayout: Boolean = false,
        userScrollEnabled: Boolean = true,
        prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        focusFollowKeyboard: Boolean = false,
        modifier: Modifier = Modifier,
        content: LazyListScope.() -> Unit,
    ) {
        with(delegate) {
            LazyColumn(
                contentPadding = contentPadding,
                spacing = spacing,
                state = state,
                reverseLayout = reverseLayout,
                userScrollEnabled = userScrollEnabled,
                prefetchPolicy = prefetchPolicy,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = focusFollowKeyboard,
                modifier = modifier,
                content = content,
            )
        }
    }

    fun ScrollableColumn(
        key: Any? = null,
        spacing: Int = 0,
        arrangement: MainAxisArrangement = MainAxisArrangement.Start,
        horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
        focusFollowKeyboard: Boolean = false,
        modifier: Modifier = Modifier,
        content: ColumnScope.() -> Unit,
    ) {
        with(delegate) {
            ScrollableColumn(
                key = key,
                spacing = spacing,
                arrangement = arrangement,
                horizontalAlignment = horizontalAlignment,
                focusFollowKeyboard = focusFollowKeyboard,
                modifier = modifier,
                content = content,
            )
        }
    }

    // ── 横向滚动 ──

    fun <T> LazyRow(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentPadding: Int = 0,
        spacing: Int = 0,
        state: LazyListState? = null,
        reverseLayout: Boolean = false,
        userScrollEnabled: Boolean = true,
        prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        modifier: Modifier = Modifier,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        with(delegate) {
            LazyRow(
                items = items,
                key = key,
                contentType = contentType,
                contentPadding = contentPadding,
                spacing = spacing,
                state = state,
                reverseLayout = reverseLayout,
                userScrollEnabled = userScrollEnabled,
                prefetchPolicy = prefetchPolicy,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                modifier = modifier,
                itemContent = itemContent,
            )
        }
    }

    fun LazyRow(
        contentPadding: LazyContentPadding = LazyContentPadding.None,
        spacing: Int = 0,
        state: LazyListState? = null,
        reverseLayout: Boolean = false,
        userScrollEnabled: Boolean = true,
        prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        modifier: Modifier = Modifier,
        content: LazyListScope.() -> Unit,
    ) {
        with(delegate) {
            LazyRow(
                contentPadding = contentPadding,
                spacing = spacing,
                state = state,
                reverseLayout = reverseLayout,
                userScrollEnabled = userScrollEnabled,
                prefetchPolicy = prefetchPolicy,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                modifier = modifier,
                content = content,
            )
        }
    }

    fun ScrollableRow(
        key: Any? = null,
        spacing: Int = 0,
        arrangement: MainAxisArrangement = MainAxisArrangement.Start,
        verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
        modifier: Modifier = Modifier,
        content: RowScope.() -> Unit,
    ) {
        with(delegate) { ScrollableRow(key, spacing, arrangement, verticalAlignment, modifier, content) }
    }

    // ── 网格 ──

    fun <T> LazyVerticalGrid(
        items: List<T>,
        spanCount: Int = 2,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        span: (T) -> Int = { 1 },
        contentPadding: Int = 0,
        horizontalSpacing: Int = 0,
        verticalSpacing: Int = 0,
        state: LazyListState? = null,
        reverseLayout: Boolean = false,
        userScrollEnabled: Boolean = true,
        prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        focusFollowKeyboard: Boolean = false,
        modifier: Modifier = Modifier,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        with(delegate) {
            LazyVerticalGrid(
                items = items,
                spanCount = spanCount,
                key = key,
                contentType = contentType,
                span = span,
                contentPadding = contentPadding,
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = verticalSpacing,
                state = state,
                reverseLayout = reverseLayout,
                userScrollEnabled = userScrollEnabled,
                prefetchPolicy = prefetchPolicy,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = focusFollowKeyboard,
                modifier = modifier,
                itemContent = itemContent,
            )
        }
    }

    fun LazyVerticalGrid(
        spanCount: Int = 2,
        contentPadding: LazyContentPadding = LazyContentPadding.None,
        horizontalSpacing: Int = 0,
        verticalSpacing: Int = 0,
        state: LazyListState? = null,
        reverseLayout: Boolean = false,
        userScrollEnabled: Boolean = true,
        prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        focusFollowKeyboard: Boolean = false,
        modifier: Modifier = Modifier,
        content: LazyGridScope.() -> Unit,
    ) {
        with(delegate) {
            LazyVerticalGrid(
                spanCount = spanCount,
                contentPadding = contentPadding,
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = verticalSpacing,
                state = state,
                reverseLayout = reverseLayout,
                userScrollEnabled = userScrollEnabled,
                prefetchPolicy = prefetchPolicy,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = focusFollowKeyboard,
                modifier = modifier,
                content = content,
            )
        }
    }

    // ── 翻页 ──

    fun HorizontalPager(
        currentPage: Int,
        onPageChanged: (Int) -> Unit,
        pagerState: PagerState? = null,
        offscreenPageLimit: Int = 1,
        userScrollEnabled: Boolean = true,
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        key: Any? = null,
        modifier: Modifier = Modifier,
        pages: HorizontalPagerScope.() -> Unit,
    ) {
        with(delegate) {
            HorizontalPager(
                currentPage = currentPage,
                onPageChanged = onPageChanged,
                pagerState = pagerState,
                offscreenPageLimit = offscreenPageLimit,
                userScrollEnabled = userScrollEnabled,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                key = key,
                modifier = modifier,
                pages = pages,
            )
        }
    }

    fun VerticalPager(
        currentPage: Int,
        onPageChanged: (Int) -> Unit,
        pagerState: PagerState? = null,
        offscreenPageLimit: Int = 1,
        userScrollEnabled: Boolean = true,
        reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
        motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
        focusFollowKeyboard: Boolean = false,
        key: Any? = null,
        modifier: Modifier = Modifier,
        pages: HorizontalPagerScope.() -> Unit,
    ) {
        with(delegate) {
            VerticalPager(
                currentPage = currentPage,
                onPageChanged = onPageChanged,
                pagerState = pagerState,
                offscreenPageLimit = offscreenPageLimit,
                userScrollEnabled = userScrollEnabled,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = focusFollowKeyboard,
                key = key,
                modifier = modifier,
                pages = pages,
            )
        }
    }

    internal fun build(): List<VNode> = delegate.build()
}
