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
import com.viewcompose.ui.unit.UiDp

/**
 * Builds nested scrollable content without exposing the outer [UiTreeBuilder].
 *
 * Every function records its nodes in a private builder. This preserves DSL ownership when a
 * scroll container nests lazy collections, pagers, or another scroll container.
 */
@UiDslMarker
class ScrollableScope internal constructor() {
    private val delegate = UiTreeBuilder()

    /**
     * Adds a keyed lazy column from [items].
     *
     * [key] must remain stable for the lifetime of an item so mounted views and item state can be
     * reused across moves. Duplicate keys are invalid.
     *
     * @param contentType groups structurally compatible items for view reuse
     * @param state optional externally owned scroll state
     * @param focusFollowKeyboard whether keyboard focus should request the focused item into view
     */
    fun <T> LazyColumn(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentPadding: UiDp = UiDp.Zero,
        spacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a lazy column whose items are declared through [LazyListScope].
     *
     * @param state optional externally owned scroll state
     * @param focusFollowKeyboard whether keyboard focus should request the focused item into view
     * @param content declares keyed lazy items and item groups
     */
    fun LazyColumn(
        contentPadding: LazyContentPadding = LazyContentPadding.None,
        spacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a vertically scrolling column whose full child tree remains mounted.
     *
     * Prefer [LazyColumn] for large or unbounded collections.
     */
    fun ScrollableColumn(
        key: Any? = null,
        spacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a keyed lazy row from [items].
     *
     * [key] must remain stable for the lifetime of an item so mounted views and item state can be
     * reused across moves. Duplicate keys are invalid.
     *
     * @param contentType groups structurally compatible items for view reuse
     * @param state optional externally owned scroll state
     */
    fun <T> LazyRow(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentPadding: UiDp = UiDp.Zero,
        spacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a lazy row whose items are declared through [LazyListScope].
     *
     * @param state optional externally owned scroll state
     * @param content declares keyed lazy items and item groups
     */
    fun LazyRow(
        contentPadding: LazyContentPadding = LazyContentPadding.None,
        spacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a horizontally scrolling row whose full child tree remains mounted.
     *
     * Prefer [LazyRow] for large or unbounded collections.
     */
    fun ScrollableRow(
        key: Any? = null,
        spacing: UiDp = UiDp.Zero,
        arrangement: MainAxisArrangement = MainAxisArrangement.Start,
        verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
        modifier: Modifier = Modifier,
        content: RowScope.() -> Unit,
    ) {
        with(delegate) { ScrollableRow(key, spacing, arrangement, verticalAlignment, modifier, content) }
    }

    /**
     * Adds a keyed lazy vertical grid from [items].
     *
     * [key] must remain stable and [span] must return a value in `1..spanCount`.
     *
     * @param contentType groups structurally compatible items for view reuse
     * @param state optional externally owned scroll state
     * @param focusFollowKeyboard whether keyboard focus should request the focused item into view
     */
    fun <T> LazyVerticalGrid(
        items: List<T>,
        spanCount: Int = 2,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        span: (T) -> Int = { 1 },
        contentPadding: UiDp = UiDp.Zero,
        horizontalSpacing: UiDp = UiDp.Zero,
        verticalSpacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a lazy vertical grid whose items are declared through [LazyGridScope].
     *
     * @param spanCount number of columns available to item spans
     * @param state optional externally owned scroll state
     * @param content declares keyed grid items and their spans
     */
    fun LazyVerticalGrid(
        spanCount: Int = 2,
        contentPadding: LazyContentPadding = LazyContentPadding.None,
        horizontalSpacing: UiDp = UiDp.Zero,
        verticalSpacing: UiDp = UiDp.Zero,
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

    /**
     * Adds a horizontally scrolling pager.
     *
     * [onPageChanged] is invoked after the pager settles on a different page. [currentPage] remains
     * the caller-owned source of truth and should be updated in response.
     *
     * @param pagerState optional externally owned pager state
     * @param offscreenPageLimit number of adjacent pages retained on each side
     */
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

    /**
     * Adds a vertically scrolling pager.
     *
     * [onPageChanged] is invoked after the pager settles on a different page. [currentPage] remains
     * the caller-owned source of truth and should be updated in response.
     *
     * @param pagerState optional externally owned pager state
     * @param offscreenPageLimit number of adjacent pages retained on each side
     */
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

    /**
     * Exports child nodes collected by this scope.
     */
    internal fun build(): List<VNode> = delegate.build()
}
