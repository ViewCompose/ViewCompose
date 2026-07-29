package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.node.collection.TabRowTab
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.state.PagerState
import com.viewcompose.ui.unit.UiDp

/**
 * 基于列表数据发射 LazyColumn。
 * Emits a LazyColumn from list data.
 */
fun <T> UiTreeBuilder.LazyColumn(
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
    LazyColumn(
        contentPadding = LazyContentPadding.all(contentPadding),
        spacing = spacing,
        state = state,
        reverseLayout = reverseLayout,
        userScrollEnabled = userScrollEnabled,
        prefetchPolicy = prefetchPolicy,
        reusePolicy = reusePolicy,
        motionPolicy = motionPolicy,
        focusFollowKeyboard = focusFollowKeyboard,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = key,
            contentType = contentType,
            itemContent = itemContent,
        )
    }
}

/**
 * 基于 LazyListScope DSL 发射 LazyColumn，并捕获当前 locals 供 item session 使用。
 * Emits a LazyColumn from LazyListScope DSL and captures current locals for item sessions.
 */
fun UiTreeBuilder.LazyColumn(
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
    val collector = LazyItemCollector(LocalContext.snapshot())
    LazyListScope(
        collector = collector,
        stickyHeadersAllowed = true,
    ).content()
    emit(
        type = NodeType.LazyColumn,
        spec = LazyColumnNodeProps(
            contentPadding = contentPadding,
            spacing = spacing,
            items = collector.build(),
            state = state,
            reverseLayout = reverseLayout,
            userScrollEnabled = userScrollEnabled,
            prefetchPolicy = prefetchPolicy,
            reusePolicy = reusePolicy,
            motionPolicy = motionPolicy,
            focusFollowKeyboard = focusFollowKeyboard,
        ),
        modifier = modifier,
    )
}

/**
 * 基于列表数据发射 LazyRow。
 * Emits a LazyRow from list data.
 */
fun <T> UiTreeBuilder.LazyRow(
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
    LazyRow(
        contentPadding = LazyContentPadding.all(contentPadding),
        spacing = spacing,
        state = state,
        reverseLayout = reverseLayout,
        userScrollEnabled = userScrollEnabled,
        prefetchPolicy = prefetchPolicy,
        reusePolicy = reusePolicy,
        motionPolicy = motionPolicy,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = key,
            contentType = contentType,
            itemContent = itemContent,
        )
    }
}

/**
 * 基于 LazyListScope DSL 发射 LazyRow；LazyRow 不支持 sticky header。
 * Emits a LazyRow from LazyListScope DSL; LazyRow does not support sticky headers.
 */
fun UiTreeBuilder.LazyRow(
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
    val collector = LazyItemCollector(LocalContext.snapshot())
    LazyListScope(
        collector = collector,
        stickyHeadersAllowed = false,
    ).content()
    emit(
        type = NodeType.LazyRow,
        spec = LazyRowNodeProps(
            contentPadding = contentPadding,
            spacing = spacing,
            items = collector.build(),
            state = state,
            reverseLayout = reverseLayout,
            userScrollEnabled = userScrollEnabled,
            prefetchPolicy = prefetchPolicy,
            reusePolicy = reusePolicy,
            motionPolicy = motionPolicy,
        ),
        modifier = modifier,
    )
}

/**
 * 基于列表数据发射 LazyVerticalGrid。
 * Emits a LazyVerticalGrid from list data.
 */
fun <T> UiTreeBuilder.LazyVerticalGrid(
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
    LazyVerticalGrid(
        spanCount = spanCount,
        contentPadding = LazyContentPadding.all(contentPadding),
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
    ) {
        items(
            items = items,
            key = key,
            contentType = contentType,
            span = span,
            itemContent = itemContent,
        )
    }
}

/**
 * 基于 LazyGridScope DSL 发射 LazyVerticalGrid。
 * Emits a LazyVerticalGrid from LazyGridScope DSL.
 */
fun UiTreeBuilder.LazyVerticalGrid(
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
    require(spanCount > 0) { "spanCount must be greater than zero." }
    val collector = LazyItemCollector(LocalContext.snapshot())
    LazyGridScope(collector).content()
    emit(
        type = NodeType.LazyVerticalGrid,
        spec = LazyVerticalGridNodeProps(
            spanCount = spanCount,
            contentPadding = contentPadding,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            items = collector.build(),
            state = state,
            reverseLayout = reverseLayout,
            userScrollEnabled = userScrollEnabled,
            prefetchPolicy = prefetchPolicy,
            reusePolicy = reusePolicy,
            motionPolicy = motionPolicy,
            focusFollowKeyboard = focusFollowKeyboard,
        ),
        modifier = modifier,
    )
}

// HorizontalPager.

/**
 * HorizontalPager 的页面收集 scope。
 * Page collection scope for HorizontalPager.
 */
@UiDslMarker
class HorizontalPagerScope internal constructor() {
    private val pages = mutableListOf<HorizontalPagerPage>()

    /**
     * 添加一个 pager 页面。
     * Adds one pager page.
     */
    fun Page(
        key: Any? = null,
        contentToken: Any? = null,
        content: UiTreeBuilder.() -> Unit,
    ) {
        pages += HorizontalPagerPage(
            key = key,
            contentToken = contentToken,
            content = content,
        )
    }

    internal fun build(): List<HorizontalPagerPage> = pages.toList()
}

/**
 * 发射 HorizontalPager，并为每个页面创建独立 lazy item session。
 * Emits a HorizontalPager and creates an independent lazy item session for each page.
 */
fun UiTreeBuilder.HorizontalPager(
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
    val builtPages = HorizontalPagerScope().apply(pages).build()
    val localSnapshot = LocalContext.snapshot()
    val resolvedPages = builtPages.map { page ->
        LazyListItem(
            key = page.key,
            contentToken = capturedLazyContentToken(
                contentToken = page.contentToken,
                localSnapshot = localSnapshot,
            ),
            sessionFactory = LazyListItemSessionFactory { container ->
                WidgetLazyListItemSession(
                    container = container,
                    localSnapshot = localSnapshot,
                    content = page.content,
                )
            },
            sessionUpdater = { session ->
                (session as? WidgetLazyListItemSession)?.updateContent(
                    localSnapshot = localSnapshot,
                    content = page.content,
                )
            },
        )
    }
    emit(
        type = NodeType.HorizontalPager,
        key = key,
        spec = HorizontalPagerNodeProps(
            pages = resolvedPages,
            currentPage = currentPage,
            onPageChanged = onPageChanged,
            offscreenPageLimit = offscreenPageLimit,
            pagerState = pagerState,
            userScrollEnabled = userScrollEnabled,
            reusePolicy = reusePolicy,
            motionPolicy = motionPolicy,
        ),
        modifier = modifier,
    )
}

/**
 * pager 页面声明快照。
 * Snapshot of one pager page declaration.
 */
internal data class HorizontalPagerPage(
    val key: Any?,
    val contentToken: Any?,
    val content: UiTreeBuilder.() -> Unit,
)

// VerticalPager.

/**
 * 发射 VerticalPager，并为每个页面创建独立 lazy item session。
 * Emits a VerticalPager and creates an independent lazy item session for each page.
 */
fun UiTreeBuilder.VerticalPager(
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
    val builtPages = HorizontalPagerScope().apply(pages).build()
    val localSnapshot = LocalContext.snapshot()
    val resolvedPages = builtPages.map { page ->
        LazyListItem(
            key = page.key,
            contentToken = capturedLazyContentToken(
                contentToken = page.contentToken,
                localSnapshot = localSnapshot,
            ),
            sessionFactory = LazyListItemSessionFactory { container ->
                WidgetLazyListItemSession(
                    container = container,
                    localSnapshot = localSnapshot,
                    content = page.content,
                )
            },
            sessionUpdater = { session ->
                (session as? WidgetLazyListItemSession)?.updateContent(
                    localSnapshot = localSnapshot,
                    content = page.content,
                )
            },
        )
    }
    emit(
        type = NodeType.VerticalPager,
        key = key,
        spec = VerticalPagerNodeProps(
            pages = resolvedPages,
            currentPage = currentPage,
            onPageChanged = onPageChanged,
            offscreenPageLimit = offscreenPageLimit,
            pagerState = pagerState,
            userScrollEnabled = userScrollEnabled,
            reusePolicy = reusePolicy,
            motionPolicy = motionPolicy,
            focusFollowKeyboard = focusFollowKeyboard,
        ),
        modifier = modifier,
    )
}

// TabRow.

/**
 * TabRow 的 tab 收集 scope。
 * Tab collection scope for TabRow.
 */
@UiDslMarker
class TabRowScope internal constructor() {
    private val tabs = mutableListOf<TabRowTabEntry>()

    /**
     * 添加一个 tab 内容声明。
     * Adds one tab content declaration.
     */
    fun Tab(
        key: Any? = null,
        content: UiTreeBuilder.(selected: Boolean) -> Unit,
    ) {
        tabs += TabRowTabEntry(
            key = key ?: tabs.size,
            content = content,
        )
    }

    internal fun build(): List<TabRowTabEntry> = tabs.toList()
}

/**
 * 发射 TabRow，并为每个 tab 创建可按 selected 状态更新的子 session。
 * Emits a TabRow and creates child sessions that update with selected state.
 */
fun UiTreeBuilder.TabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    pagerState: PagerState? = null,
    indicatorColor: Int = TabRowDefaults.indicatorColor(),
    indicatorHeight: UiDp = TabRowDefaults.indicatorHeight(),
    indicatorCornerRadius: UiDp = TabRowDefaults.indicatorCornerRadius(),
    indicatorPosition: TabIndicatorPosition = TabIndicatorPosition.Bottom,
    indicatorWidthMode: TabIndicatorWidthMode = TabIndicatorWidthMode.MatchItem,
    indicatorFixedWidth: UiDp = UiDp.Zero,
    containerColor: Int = TabRowDefaults.containerColor(),
    scrollable: Boolean = false,
    equalWidth: Boolean = true,
    rippleColor: Int = TabRowDefaults.rippleColor(),
    itemSpacing: UiDp = UiDp.Zero,
    itemPaddingHorizontal: UiDp = TabRowDefaults.itemPaddingHorizontal(),
    itemPaddingVertical: UiDp = TabRowDefaults.itemPaddingVertical(),
    minItemWidth: UiDp = TabRowDefaults.minItemWidth(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    tabs: TabRowScope.() -> Unit,
) {
    val builtTabs = TabRowScope().apply(tabs).build()
    val localSnapshot = LocalContext.snapshot()
    val resolvedTabs = builtTabs.mapIndexed { index, entry ->
        val selected = index == selectedIndex
        TabRowTab(
            item = LazyListItem(
                key = entry.key,
                contentToken = capturedLazyContentToken(
                    contentToken = Pair(entry.key, selected),
                    localSnapshot = localSnapshot,
                ),
                sessionFactory = LazyListItemSessionFactory { container ->
                    WidgetLazyListItemSession(
                        container = container,
                        localSnapshot = localSnapshot,
                        content = { entry.content(this, selected) },
                    )
                },
                sessionUpdater = { session ->
                    (session as? WidgetLazyListItemSession)?.updateContent(
                        localSnapshot = localSnapshot,
                        content = { entry.content(this, selected) },
                    )
                },
            ),
        )
    }
    emit(
        type = NodeType.TabRow,
        key = key,
        spec = TabRowNodeProps(
            tabs = resolvedTabs,
            selectedIndex = selectedIndex,
            onTabSelected = onTabSelected,
            pagerState = pagerState,
            indicatorColor = indicatorColor,
            indicatorHeight = indicatorHeight,
            indicatorCornerRadius = indicatorCornerRadius,
            indicatorPosition = indicatorPosition,
            indicatorWidthMode = indicatorWidthMode,
            indicatorFixedWidth = indicatorFixedWidth,
            containerColor = containerColor,
            scrollable = scrollable,
            equalWidth = equalWidth,
            rippleColor = rippleColor,
            itemSpacing = itemSpacing,
            itemPaddingHorizontal = itemPaddingHorizontal,
            itemPaddingVertical = itemPaddingVertical,
            minItemWidth = minItemWidth,
        ),
        modifier = modifier,
    )
}

/**
 * tab 内容声明快照。
 * Snapshot of one tab content declaration.
 */
internal data class TabRowTabEntry(
    val key: Any,
    val content: UiTreeBuilder.(selected: Boolean) -> Unit,
)
