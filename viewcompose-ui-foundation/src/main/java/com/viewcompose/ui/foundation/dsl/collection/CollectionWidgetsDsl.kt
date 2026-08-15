package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.GridItemSpan
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
 * Emits a virtualized vertical grid from list data and stable item identities.
 *
 * [cells] may fix the column count or derive it from a minimum cell width. Physical column-count
 * changes do not replace logical item sessions. [span] uses renderer-neutral policies, so
 * [GridItemSpan.FullLine] remains correct when an adaptive grid resizes.
 *
 * @sample com.viewcompose.ui.foundation.samples.adaptiveGridSample
 * @param T list element type
 * @receiver active tree builder receiving the grid node
 * @param items ordered data snapshot for this render
 * @param cells fixed or adaptive horizontal cell policy
 * @param key unique stable logical identity for each item
 * @param contentType physical-presentation compatibility class for each item
 * @param contentRevision semantic version of every changing non-State value captured by item content
 * @param span cell-span policy for each item; `Fixed(1)` is normalized to [GridItemSpan.Single]
 * @param contentPadding equal logical padding on all content edges
 * @param horizontalSpacing non-negative gap between adjacent columns
 * @param verticalSpacing non-negative gap between adjacent rows
 * @param state optional caller-owned lazy-list position and command state
 * @param reverseLayout whether rows and scroll start from the opposite main-axis edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy item preparation and native cache hints
 * @param reusePolicy native presentation reuse hints
 * @param motionPolicy native item-mutation animation hints
 * @param focusFollowKeyboard whether keyboard focus may bring an item into view
 * @param modifier ordered configuration applied to the grid root
 * @param itemContent delayed item content evaluated in its keyed session
 * @throws IllegalArgumentException for duplicate keys or invalid spacing
 */
fun <T> UiTreeBuilder.LazyVerticalGrid(
    items: List<T>,
    cells: GridCells = GridCells.Fixed(2),
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
    span: (T) -> GridItemSpan = { GridItemSpan.Single },
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
        cells = cells,
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
 * Emits a virtualized vertical grid from explicitly keyed [LazyGridScope] entries.
 *
 * Adaptive columns are recomputed from available inner width without rebuilding keyed logical
 * sessions. Sticky headers and [GridItemSpan.FullLine] resolve against the current physical column
 * count.
 *
 * @sample com.viewcompose.ui.foundation.samples.adaptiveGridSample
 * @receiver active tree builder receiving the grid node
 * @param cells fixed or adaptive horizontal cell policy
 * @param contentPadding logical per-edge padding inside the scrollable content
 * @param horizontalSpacing non-negative gap between adjacent columns
 * @param verticalSpacing non-negative gap between adjacent rows
 * @param state optional caller-owned lazy-list position and command state
 * @param reverseLayout whether rows and scroll start from the opposite main-axis edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy item preparation and native cache hints
 * @param reusePolicy native presentation reuse hints
 * @param motionPolicy native item-mutation animation hints
 * @param focusFollowKeyboard whether keyboard focus may bring an item into view
 * @param modifier ordered configuration applied to the grid root
 * @param content keyed grid-item declarations captured for delayed sessions
 * @throws IllegalArgumentException for duplicate keys or invalid spacing
 */
fun UiTreeBuilder.LazyVerticalGrid(
    cells: GridCells = GridCells.Fixed(2),
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
    val collector = LazyItemCollector(
        localSnapshot = LocalContext.snapshot(),
        saveableStateHolder = rememberSaveableStateHolder(),
    )
    LazyGridScope(collector).content()
    emit(
        type = NodeType.LazyVerticalGrid,
        spec = LazyVerticalGridNodeProps(
            cells = cells,
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
 * Emits a horizontal pager with one keyed lazy session per page.
 *
 * [currentPage] is caller-owned. [onPageChanged] runs synchronously only after user or programmatic
 * motion settles on a different page; initial binding and controlled rebinding do not invoke it.
 * [pagerState] observes current, settled, target, offset, page-count, and motion snapshots and can
 * issue immediate or animated page commands while attached.
 *
 * @sample com.viewcompose.ui.foundation.samples.pagerAndTabIdentitySample
 * @receiver active tree builder receiving the pager node
 * @param currentPage controlled page selected for this render
 * @param onPageChanged callback receiving a newly settled page index
 * @param pagerState optional caller-owned observation and command state
 * @param offscreenPageLimit native adjacent-page residency limit, or `-1` for the platform default
 * @param userScrollEnabled whether direct user paging is accepted
 * @param reusePolicy native page-presentation reuse hints
 * @param motionPolicy native page-mutation animation hints
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration applied to the pager root
 * @param pages explicitly keyed delayed page declarations
 * @throws IllegalArgumentException for duplicate page keys or an invalid offscreen limit
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
 * Emits a vertical pager with one keyed lazy session per page.
 *
 * Settled callback and [pagerState] semantics match [HorizontalPager]. Keyboard focus-follow may
 * request the focused descendant page into view without transferring page-session identity.
 *
 * @sample com.viewcompose.ui.foundation.samples.pagerAndTabIdentitySample
 * @receiver active tree builder receiving the pager node
 * @param currentPage controlled page selected for this render
 * @param onPageChanged callback receiving a newly settled page index
 * @param pagerState optional caller-owned observation and command state
 * @param offscreenPageLimit native adjacent-page residency limit, or `-1` for the platform default
 * @param userScrollEnabled whether direct user paging is accepted
 * @param reusePolicy native page-presentation reuse hints
 * @param motionPolicy native page-mutation animation hints
 * @param focusFollowKeyboard whether keyboard focus may bring a descendant page into view
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration applied to the pager root
 * @param pages explicitly keyed delayed page declarations
 * @throws IllegalArgumentException for duplicate page keys or an invalid offscreen limit
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
 *
 * Selection changes invalidate only the previously selected and newly selected keyed children.
 * The row never creates lazy item sessions; appearance resolves once from instance, scoped, and
 * semantic defaults in that order.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder that receives the emitted eager TabRow
 * @param selectedIndex currently selected tab index
 * @param onTabSelected callback receiving a requested index synchronously on the renderer thread
 * @param pagerState optional pager state used to synchronize selection and indicator progress
 * @param overrides sparse instance appearance applied after scoped [ProvideTabRowOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended to the emitted TabRow node
 * @param tabs eager keyed tab declarations evaluated in the parent composition
 */
fun UiTreeBuilder.TabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    pagerState: PagerState? = null,
    overrides: TabRowOverrides = TabRowOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
    tabs: TabRowScope.() -> Unit,
) {
    val appearance = TabRowDefaults.resolve(overrides)
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
            indicatorColor = appearance.indicatorColor,
            indicatorHeight = appearance.indicatorHeight,
            indicatorCornerRadius = appearance.indicatorCornerRadius,
            indicatorPosition = appearance.indicatorPosition,
            indicatorWidthMode = appearance.indicatorWidthMode,
            indicatorFixedWidth = appearance.indicatorFixedWidth,
            containerColor = appearance.containerColor,
            scrollable = appearance.scrollable,
            equalWidth = appearance.equalWidth,
            rippleColor = appearance.rippleColor,
            itemSpacing = appearance.itemSpacing,
            itemPaddingHorizontal = appearance.itemPaddingHorizontal,
            itemPaddingVertical = appearance.itemPaddingVertical,
            minItemWidth = appearance.minimumItemWidth,
        ),
        modifier = Modifier
            .semantics {
                collectionInfo = SemanticsCollectionInfo(
                    rowCount = 1,
                    columnCount = builtTabs.size,
                    selectionMode = SemanticsCollectionSelectionMode.Single,
                )
            }
            .then(modifier),
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
                        rippleColor = appearance.rippleColor,
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {
                                role = SemanticsRole.Tab
                                this.selected = selected
                                enabled = true
                                collectionItemInfo = SemanticsCollectionItemInfo(
                                    rowIndex = 0,
                                    columnIndex = index,
                                )
                            }
                            .clickable {
                                (currentOnTabSelected?.value ?: onTabSelected)(index)
                            }
                            .padding(
                                horizontal = appearance.itemPaddingHorizontal,
                                vertical = appearance.itemPaddingVertical,
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
