package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.padding
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
 * Emits a LazyColumn from list data.
 */
fun <T> UiTreeBuilder.LazyColumn(
    items: List<T>,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
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
            contentRevision = contentRevision,
            itemContent = itemContent,
        )
    }
}

/**
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
    val collector = LazyItemCollector(
        localSnapshot = LocalContext.snapshot(),
        saveableStateHolder = rememberSaveableStateHolder(),
    )
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
 * Emits a LazyRow from list data.
 */
fun <T> UiTreeBuilder.LazyRow(
    items: List<T>,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
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
            contentRevision = contentRevision,
            itemContent = itemContent,
        )
    }
}

/**
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
    val collector = LazyItemCollector(
        localSnapshot = LocalContext.snapshot(),
        saveableStateHolder = rememberSaveableStateHolder(),
    )
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
 * Emits a LazyVerticalGrid from list data.
 */
fun <T> UiTreeBuilder.LazyVerticalGrid(
    items: List<T>,
    spanCount: Int = 2,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
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
            contentRevision = contentRevision,
            span = span,
            itemContent = itemContent,
        )
    }
}

/**
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
    val collector = LazyItemCollector(
        localSnapshot = LocalContext.snapshot(),
        saveableStateHolder = rememberSaveableStateHolder(),
    )
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
 * Declares delayed pager pages with explicit logical identity and semantic revisions.
 *
 * Page keys are required and unique. A page owns remember, saveable state, observations, and
 * effects by key while its session is resident; a recycled native presentation never owns that
 * identity.
 *
 * @sample com.viewcompose.ui.foundation.samples.pagerAndTabIdentitySample
 */
@UiDslMarker
class HorizontalPagerScope internal constructor() {
    private val pages = mutableListOf<HorizontalPagerPage>()
    private val keys = linkedSetOf<Any>()

    /**
     * Adds one delayed page session.
     *
     * Equal key, [contentRevision], and framework environment revision skip page rendering.
     * [contentType] permits only physical-tree reuse after the old key session is disposed.
     *
     * @param key unique logical page identity across reorder and native recycling
     * @param contentType physical-tree compatibility class for reset and rebind
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param content page declaration evaluated when the page session renders
     * @throws IllegalArgumentException when [key] duplicates another page in this scope
     */
    fun Page(
        key: Any,
        contentType: Any? = null,
        contentRevision: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        require(keys.add(key)) {
            "Pager page keys must be unique. Duplicate key: $key"
        }
        pages += HorizontalPagerPage(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            content = content,
        )
    }

    internal fun build(): List<HorizontalPagerPage> = pages.toList()
}

/**
 * Emits a HorizontalPager and creates an independent lazy item session for each page.
 */
fun UiTreeBuilder.HorizontalPager(
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    pagerState: PagerState? = null,
    offscreenPageLimit: Int = -1,
    userScrollEnabled: Boolean = true,
    reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    pages: HorizontalPagerScope.() -> Unit,
) {
    val builtPages = HorizontalPagerScope().apply(pages).build()
    val localSnapshot = LocalContext.snapshot()
    val saveableStateHolder = rememberSaveableStateHolder()
    val saveableStateKeys = resolveDelayedChildSaveableStateKeys(
        builtPages.map(HorizontalPagerPage::key),
    )
    val resolvedPages = builtPages.mapIndexed { index, page ->
        val saveableStateKey = saveableStateKeys[index]
        LazyListItem(
            key = page.key,
            contentType = page.contentType,
            contentRevision = page.contentRevision,
            environmentRevision = localSnapshot,
            sessionFactory = LazyListItemSessionFactory { container ->
                WidgetLazyListItemSession(
                    container = container,
                    localSnapshot = localSnapshot,
                    saveableStateHolder = saveableStateHolder,
                    saveableStateKey = saveableStateKey,
                    content = page.content,
                )
            },
            sessionUpdater = { session ->
                (session as WidgetLazyListItemSession).updateContent(
                    localSnapshot = localSnapshot,
                    content = page.content,
                )
            },
        )
    }
    saveableStateHolder?.let { holder ->
        val committedKeys = saveableStateKeys.toSet()
        SideEffect {
            holder.retainKeys(committedKeys)
        }
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
 * Snapshot of one pager page declaration.
 */
internal data class HorizontalPagerPage(
    val key: Any,
    val contentType: Any?,
    val contentRevision: Any?,
    val content: UiTreeBuilder.() -> Unit,
)

// VerticalPager.

/**
 * Emits a VerticalPager and creates an independent lazy item session for each page.
 */
fun UiTreeBuilder.VerticalPager(
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    pagerState: PagerState? = null,
    offscreenPageLimit: Int = -1,
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
    val saveableStateHolder = rememberSaveableStateHolder()
    val saveableStateKeys = resolveDelayedChildSaveableStateKeys(
        builtPages.map(HorizontalPagerPage::key),
    )
    val resolvedPages = builtPages.mapIndexed { index, page ->
        val saveableStateKey = saveableStateKeys[index]
        LazyListItem(
            key = page.key,
            contentType = page.contentType,
            contentRevision = page.contentRevision,
            environmentRevision = localSnapshot,
            sessionFactory = LazyListItemSessionFactory { container ->
                WidgetLazyListItemSession(
                    container = container,
                    localSnapshot = localSnapshot,
                    saveableStateHolder = saveableStateHolder,
                    saveableStateKey = saveableStateKey,
                    content = page.content,
                )
            },
            sessionUpdater = { session ->
                (session as WidgetLazyListItemSession).updateContent(
                    localSnapshot = localSnapshot,
                    content = page.content,
                )
            },
        )
    }
    saveableStateHolder?.let { holder ->
        val committedKeys = saveableStateKeys.toSet()
        SideEffect {
            holder.retainKeys(committedKeys)
        }
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
 * Declares eager keyed tab children in the parent composition.
 *
 * Tabs do not create lazy item sessions or physical reuse owners. Their keys preserve ordinary
 * parent-tree remember/saveable identity across reorder, while selection invalidates only the old
 * and new selected children.
 *
 * @sample com.viewcompose.ui.foundation.samples.pagerAndTabIdentitySample
 */
@UiDslMarker
class TabRowScope internal constructor() {
    private val tabs = mutableListOf<TabRowTabEntry>()
    private val keys = linkedSetOf<Any>()

    /**
     * Adds one eager keyed tab child.
     *
     * @param key unique identity used by ordinary keyed child reconciliation
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param content declaration receiving whether this tab is currently selected
     * @throws IllegalArgumentException when [key] duplicates another tab in this scope
     */
    fun Tab(
        key: Any,
        contentRevision: Any? = key,
        content: UiTreeBuilder.(selected: Boolean) -> Unit,
    ) {
        require(keys.add(key)) {
            "TabRow keys must be unique. Duplicate key: $key"
        }
        tabs += TabRowTabEntry(
            key = key,
            contentRevision = contentRevision,
            content = content,
        )
    }

    internal fun build(): List<TabRowTabEntry> = tabs.toList()
}

/**
 * Emits a TabRow whose tabs are eager keyed children in the parent composition.
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
    val environmentRevision = LocalContext.snapshot()
    val currentOnTabSelected = ComposerContext.currentComposer()?.let {
        rememberUpdatedState(onTabSelected)
    }
    emit(
        type = NodeType.TabRow,
        key = key,
        spec = TabRowNodeProps(
            selectedIndex = selectedIndex,
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
    ) {
        builtTabs.forEachIndexed { index, entry ->
            val selected = index == selectedIndex
            val buildTab: () -> Unit = {
                RecomposeBoundary(
                    key = entry.key,
                    inputs = listOf(entry.contentRevision, environmentRevision, selected),
                ) {
                    Box(
                        key = entry.key,
                        rippleColor = rippleColor,
                        modifier = Modifier
                            .clickable {
                                (currentOnTabSelected?.value ?: onTabSelected)(index)
                            }
                            .padding(
                                horizontal = itemPaddingHorizontal,
                                vertical = itemPaddingVertical,
                            ),
                    ) {
                        entry.content(this, selected)
                    }
                }
            }
            if (ComposerContext.currentComposer() == null) {
                buildTab()
            } else {
                key(entry.key, block = buildTab)
            }
        }
    }
}

/**
 * Snapshot of one tab content declaration.
 */
internal data class TabRowTabEntry(
    val key: Any,
    val contentRevision: Any?,
    val content: UiTreeBuilder.(selected: Boolean) -> Unit,
)

/** Keeps required explicit identities host-saveable without hashing application keys. */
private fun resolveDelayedChildSaveableStateKeys(keys: List<Any>): List<Any> {
    return keys.map { key ->
        listOf(DELAYED_CHILD_KEY_MARKER, key)
    }
}

private const val DELAYED_CHILD_KEY_MARKER =
    "com.viewcompose.ui.foundation.dsl.collection.DelayedChildSaveableStateKey"
