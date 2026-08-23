package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionStrategy
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.node.LazyListItemKind
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
 * Virtualizes vertical list data with explicit logical identity and revision contracts.
 *
 * A stable [key] owns item state and effects. Equal key, [contentRevision], captured environment,
 * [contentType], kind, and span reuse the canonical item and skip its rendering. [contentType] also
 * permits native presentation reuse across different keys without sharing their logical sessions.
 * Collection selectors run on every parent declaration pass. Mutable values read by item content
 * must be observable State or enter the affected item's content revision. The default
 * [contentRevision] is valid only for immutable value models whose equality covers every ordinary
 * non-State value read by item content.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyCollectionRevisionSample
 * @sample com.viewcompose.ui.foundation.samples.delayedContentSingleRootSample
 * @param T item model type
 * @receiver active tree builder receiving the lazy list
 * @param items ordered data snapshot represented by the list
 * @param key stable unique logical identity for each item
 * @param contentType reusable presentation category, or `null` for shared untyped reuse
 * @param contentRevision semantic selector; immutable value models default to themselves
 * @param contentPadding uniform viewport content padding in dp
 * @param spacing fixed gap in dp between adjacent items
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether logical item order starts at the trailing edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the lazy-list host
 * @param itemContent content factory invoked only while an item session is active; each invocation
 * must emit exactly one root node
 * @throws IllegalArgumentException when [key] selects duplicate values
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
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
    modifier: Modifier = Modifier,
    itemContent: UiTreeBuilder.(T) -> Unit,
) {
    val itemSnapshot = rememberLazyItemCollector(NodeType.LazyColumn).apply {
        addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = { GridItemSpan.Single },
            itemContent = itemContent,
        )
    }.build()
    emit(
        type = NodeType.LazyColumn,
        spec = LazyColumnNodeProps(
            contentPadding = LazyContentPadding.all(contentPadding),
            spacing = spacing,
            items = itemSnapshot,
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
 * Virtualizes a copied item snapshot with a bounded whole-table reuse path.
 *
 * The first declaration of each [items] identity under a framework environment evaluates [key],
 * [contentType], and [contentRevision] once per item and freezes those results in this declaration's
 * cache. Reusing either of its two most recently committed snapshot/environment pairs returns the
 * complete ordered logical-item list in constant time without invoking selectors or scanning keys.
 * A cache miss evaluates selectors in item order and retains ordinary keyed item reuse.
 *
 * Create a replacement [LazyItemsSnapshot] whenever its order, membership, item data, selector
 * captures, or ordinary non-State values captured by [itemContent] change. Such content values must
 * also participate in [contentRevision]. Only observable State read by [itemContent] remains
 * independently invalidating. State or any other changing value read by a selector requires a
 * replacement snapshot; a framework environment change deliberately reevaluates selectors.
 * Selector failures propagate unchanged and do not publish an evaluated snapshot, so retrying the
 * same identity and environment reevaluates every selector.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyItemsSnapshotSample
 * @param T item model type
 * @receiver active tree builder receiving the lazy list
 * @param items shallow-copied ordered submission with framework-owned identity
 * @param key stable unique logical identity selector for each item
 * @param contentType reusable presentation category, or `null` for shared untyped reuse
 * @param contentRevision semantic selector; immutable value models default to themselves
 * @param contentPadding uniform viewport content padding in dp
 * @param spacing fixed gap in dp between adjacent items
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether logical item order starts at the trailing edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the lazy-list host
 * @param itemContent content factory invoked only while an item session is active; each invocation
 * must emit exactly one root node
 * @throws IllegalArgumentException when [key] selects duplicate values on a cache miss
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
 * @throws Throwable when a selector fails on a cache miss
 */
fun <T> UiTreeBuilder.LazyColumn(
    items: LazyItemsSnapshot<T>,
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
    val itemSnapshot = rememberLazyItemCollector(NodeType.LazyColumn).apply {
        addSnapshotItems(
            snapshot = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = { GridItemSpan.Single },
            itemContent = itemContent,
        )
    }.build()
    emit(
        type = NodeType.LazyColumn,
        spec = LazyColumnNodeProps(
            contentPadding = LazyContentPadding.all(contentPadding),
            spacing = spacing,
            items = itemSnapshot,
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
 * Virtualizes scoped vertical items while preserving captured locals per active item session.
 *
 * Item declarations must provide stable keys and accurate revisions through [LazyListScope]. Sticky
 * headers are supported. Sessions outside the active and reuse windows may be disposed. Focused
 * editors use the platform child-rectangle chain and remain visible without a container opt-in;
 * this programmatic movement remains active when [userScrollEnabled] is `false`.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyCollectionRevisionSample
 * @sample com.viewcompose.ui.foundation.samples.focusVisibilityOwnershipSample
 * @receiver active tree builder receiving the lazy list
 * @param contentPadding independent start, top, end, and bottom viewport padding
 * @param spacing fixed gap in dp between adjacent items
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether logical item order starts at the trailing edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the lazy-list host
 * @param content scoped item declarations evaluated synchronously for the list snapshot
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
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val collector = rememberLazyItemCollector(NodeType.LazyColumn)
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
        ),
        modifier = modifier,
    )
}

/**
 * Virtualizes horizontal list data with explicit logical identity and revision contracts.
 *
 * A stable [key] owns item state and effects. Equal key, [contentRevision], captured environment,
 * [contentType], kind, and span reuse the canonical item and skip its rendering. [contentType] also
 * permits native presentation reuse across different keys without sharing their logical sessions.
 * Collection selectors run on every parent declaration pass. Mutable values read by item content
 * must be observable State or enter the affected item's content revision. The default
 * [contentRevision] is valid only for immutable value models whose equality covers every ordinary
 * non-State value read by item content.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyListDslSample
 * @param T item model type
 * @receiver active tree builder receiving the lazy list
 * @param items ordered data snapshot represented by the list
 * @param key stable unique logical identity for each item
 * @param contentType reusable presentation category, or `null` for shared untyped reuse
 * @param contentRevision semantic selector; immutable value models default to themselves
 * @param contentPadding uniform viewport content padding in dp
 * @param spacing fixed gap in dp between adjacent items
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether logical item order starts at the trailing edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the lazy-list host
 * @param itemContent content factory invoked only while an item session is active; each invocation
 * must emit exactly one root node
 * @throws IllegalArgumentException when [key] selects duplicate values
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
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
    val itemSnapshot = rememberLazyItemCollector(NodeType.LazyRow).apply {
        addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = { GridItemSpan.Single },
            itemContent = itemContent,
        )
    }.build()
    emit(
        type = NodeType.LazyRow,
        spec = LazyRowNodeProps(
            contentPadding = LazyContentPadding.all(contentPadding),
            spacing = spacing,
            items = itemSnapshot,
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
 * Virtualizes a copied horizontal item snapshot with bounded whole-table reuse.
 *
 * The first declaration of an [items] identity in a framework environment evaluates [key],
 * [contentType], and [contentRevision] once per item. Either of the two most recently committed
 * snapshot/environment pairs can then return its complete ordered logical-item list in constant
 * time without selectors or a key scan. A miss evaluates selectors in order and retains keyed
 * canonical-item and native-presentation reuse.
 *
 * Create a replacement [LazyItemsSnapshot] whenever its order, membership, item data, selector
 * captures, or ordinary non-State values captured by [itemContent] change. Such content values must
 * also participate in [contentRevision]. Only observable State read by [itemContent] remains
 * independently invalidating. State or any other changing value read by a selector requires a
 * replacement snapshot; a framework environment change deliberately reevaluates selectors.
 * Selector failures propagate unchanged and do not publish an evaluated snapshot, so retrying the
 * same identity and environment reevaluates every selector.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyItemsSnapshotSample
 * @param T item model type
 * @receiver active tree builder receiving the lazy row
 * @param items shallow-copied ordered submission with framework-owned identity
 * @param key stable unique logical identity selector for each item
 * @param contentType reusable presentation category, or `null` for shared untyped reuse
 * @param contentRevision semantic selector; immutable value models default to themselves
 * @param contentPadding uniform viewport content padding in dp
 * @param spacing fixed gap in dp between adjacent items
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether logical item order starts at the trailing edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the lazy-list host
 * @param itemContent content factory invoked only while an item session is active; each invocation
 * must emit exactly one root node
 * @throws IllegalArgumentException when [key] selects duplicate values on a cache miss
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
 * @throws Throwable when a selector fails on a cache miss
 */
fun <T> UiTreeBuilder.LazyRow(
    items: LazyItemsSnapshot<T>,
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
    val itemSnapshot = rememberLazyItemCollector(NodeType.LazyRow).apply {
        addSnapshotItems(
            snapshot = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = { GridItemSpan.Single },
            itemContent = itemContent,
        )
    }.build()
    emit(
        type = NodeType.LazyRow,
        spec = LazyRowNodeProps(
            contentPadding = LazyContentPadding.all(contentPadding),
            spacing = spacing,
            items = itemSnapshot,
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
 * Virtualizes scoped horizontal items while preserving captured locals per active item session.
 *
 * Item declarations must provide stable keys and accurate revisions through [LazyListScope]. Sticky
 * headers are rejected because a horizontal list has no sticky-header contract.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyListDslSample
 * @receiver active tree builder receiving the lazy list
 * @param contentPadding independent start, top, end, and bottom viewport padding
 * @param spacing fixed gap in dp between adjacent items
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether logical item order starts at the trailing edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the lazy-list host
 * @param content scoped item declarations evaluated synchronously for the list snapshot
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
    val collector = rememberLazyItemCollector(NodeType.LazyRow)
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
 * [GridItemSpan.FullLine] remains correct when an adaptive grid resizes. Collection selectors run
 * on every parent declaration pass; equal key, content revision, environment, content type, kind,
 * and span reuse the canonical logical item and Session binding.
 * The default [contentRevision] is valid only for immutable value models whose equality covers
 * every ordinary non-State value read by [itemContent].
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyCollectionRevisionSample
 * @param T list element type
 * @receiver active tree builder receiving the grid node
 * @param items ordered data snapshot for this render
 * @param cells fixed or adaptive horizontal cell policy
 * @param key unique stable logical identity for each item
 * @param contentType physical-presentation compatibility class for each item
 * @param contentRevision semantic selector; immutable value models default to themselves
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
 * @param modifier ordered configuration applied to the grid root
 * @param itemContent delayed item content evaluated in its keyed session; each invocation must emit
 * exactly one root node
 * @throws IllegalArgumentException for duplicate keys or invalid spacing
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
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
    modifier: Modifier = Modifier,
    itemContent: UiTreeBuilder.(T) -> Unit,
) {
    val itemSnapshot = rememberLazyItemCollector(NodeType.LazyVerticalGrid).apply {
        addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = span,
            itemContent = itemContent,
        )
    }.build()
    emit(
        type = NodeType.LazyVerticalGrid,
        spec = LazyVerticalGridNodeProps(
            cells = cells,
            contentPadding = LazyContentPadding.all(contentPadding),
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            items = itemSnapshot,
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
 * Virtualizes a copied grid snapshot with bounded whole-table reuse.
 *
 * On the first declaration of an [items] identity in a framework environment, the container
 * evaluates [key], [contentType], [contentRevision], and [span] once per item. Either of the two
 * most recently committed snapshot/environment pairs can then restore its complete ordered logical
 * item list in constant time without selectors or a key scan. A miss evaluates selectors in order,
 * canonicalizes `Fixed(1)` spans, rejects duplicate keys, and retains keyed session reuse.
 *
 * Create a replacement [LazyItemsSnapshot] whenever order, membership, item data, selector captures,
 * or ordinary non-State values captured by [itemContent] change. Such content values must also
 * participate in [contentRevision]. Only observable State read by [itemContent] remains
 * independently invalidating. State or any other changing value read by a selector requires a
 * replacement snapshot; environment changes deliberately reevaluate every selector. Selector
 * failures propagate unchanged and do not publish an evaluated snapshot, so retrying the same
 * identity and environment reevaluates every selector.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyItemsSnapshotSample
 * @param T item model type
 * @receiver active tree builder receiving the grid node
 * @param items shallow-copied ordered submission with framework-owned identity
 * @param cells fixed or adaptive horizontal cell policy
 * @param key stable unique logical identity selector for each item
 * @param contentType reusable physical-presentation category for each item
 * @param contentRevision semantic selector; immutable value models default to themselves
 * @param span renderer-neutral cell-span selector
 * @param contentPadding uniform viewport content padding in dp
 * @param horizontalSpacing non-negative gap between adjacent columns
 * @param verticalSpacing non-negative gap between adjacent rows
 * @param state optional caller-owned scroll position and command state
 * @param reverseLayout whether rows and scroll start from the opposite main-axis edge
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param prefetchPolicy ahead-of-viewport session preparation policy
 * @param reusePolicy mounted-tree capacity and reuse policy
 * @param motionPolicy item placement and change animation policy
 * @param modifier ordered configuration applied to the grid root
 * @param itemContent content factory invoked only while an item session is active; each invocation
 * must emit exactly one root node
 * @throws IllegalArgumentException for invalid spacing, or duplicate keys on a cache miss
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
 * @throws Throwable when a selector fails on a cache miss
 */
fun <T> UiTreeBuilder.LazyVerticalGrid(
    items: LazyItemsSnapshot<T>,
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
    modifier: Modifier = Modifier,
    itemContent: UiTreeBuilder.(T) -> Unit,
) {
    val itemSnapshot = rememberLazyItemCollector(NodeType.LazyVerticalGrid).apply {
        addSnapshotItems(
            snapshot = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = span,
            itemContent = itemContent,
        )
    }.build()
    emit(
        type = NodeType.LazyVerticalGrid,
        spec = LazyVerticalGridNodeProps(
            cells = cells,
            contentPadding = LazyContentPadding.all(contentPadding),
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            items = itemSnapshot,
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
 * Emits a virtualized vertical grid from explicitly keyed [LazyGridScope] entries.
 *
 * Adaptive columns are recomputed from available inner width without rebuilding keyed logical
 * sessions. Sticky headers and [GridItemSpan.FullLine] resolve against the current physical column
 * count. Focused editors use the platform child-rectangle chain and remain visible without a
 * container opt-in; this programmatic movement remains active when [userScrollEnabled] is `false`.
 *
 * @sample com.viewcompose.ui.foundation.samples.adaptiveGridSample
 * @sample com.viewcompose.ui.foundation.samples.focusVisibilityOwnershipSample
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
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    val collector = rememberLazyItemCollector(NodeType.LazyVerticalGrid)
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
        ),
        modifier = modifier,
    )
}

private fun rememberLazyItemCollector(hostType: NodeType): LazyItemCollector {
    val saveableStateHolder = rememberSaveableStateHolder()
    val reuseCache = if (ComposerContext.currentComposer() == null) {
        LazyItemCanonicalReuseCache()
    } else {
        remember(saveableStateHolder, hostType) {
            LazyItemCanonicalReuseCache()
        }
    }
    return LazyItemCollector(
        localSnapshot = LocalContext.snapshot(),
        saveableStateHolder = saveableStateHolder,
        reuseCache = reuseCache,
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
 * @sample com.viewcompose.ui.foundation.samples.delayedContentSingleRootSample
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
     * Pass [StaticContentRevision] only when [content] has no changing ordinary non-State inputs;
     * otherwise the explicit revision must change with every such input that affects the page.
     *
     * @param key unique logical page identity across reorder and native recycling
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param contentType physical-tree compatibility class for reset and rebind
     * @param content page declaration evaluated when the page session renders; it must emit exactly
     * one root node, wrapping siblings in an explicit layout container
     * @throws IllegalArgumentException when [key] duplicates another page in this scope
     * @throws IllegalStateException when [content] emits zero or multiple root nodes
     */
    fun Page(
        key: Any,
        contentRevision: Any,
        contentType: Any? = null,
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
 * @param offscreenPageLimit adjacent-page residency limit, or `-1` for RecyclerView's default
 * @param userScrollEnabled whether direct pointer and accessibility paging is accepted
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
    val sessionStrategy = PagerLazyItemSessionStrategy(localSnapshot, saveableStateHolder)
    val resolvedPages = builtPages.mapIndexed { index, page ->
        val saveableStateKey = saveableStateKeys[index]
        LazyListItem(
            key = page.key,
            contentType = page.contentType,
            contentRevision = page.contentRevision,
            environmentRevision = localSnapshot,
            sessionStrategy = sessionStrategy,
            sessionPayload = PagerLazyItemPayload(saveableStateKey, page.content),
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
    val contentRevision: Any,
    val content: UiTreeBuilder.() -> Unit,
)

private data class PagerLazyItemPayload(
    val saveableStateKey: Any,
    val content: UiTreeBuilder.() -> Unit,
)

private class PagerLazyItemSessionStrategy(
    private val localSnapshot: LocalSnapshot,
    private val saveableStateHolder: SaveableStateHolder?,
) : LazyListItemSessionStrategy {
    override fun create(
        container: RenderContainerHandle,
        item: LazyListItem,
    ): LazyListItemSession {
        val payload = item.sessionPayload as PagerLazyItemPayload
        return WidgetLazyListItemSession(
            container = container,
            localSnapshot = localSnapshot,
            saveableStateHolder = saveableStateHolder,
            saveableStateKey = payload.saveableStateKey,
            content = PagerWidgetLazyItemContent,
            contentPayload = payload,
            role = RenderSessionRole.PagerPage,
        )
    }

    override fun update(
        session: LazyListItemSession,
        item: LazyListItem,
    ) {
        (session as WidgetLazyListItemSession).updateContent(
            localSnapshot = localSnapshot,
            content = PagerWidgetLazyItemContent,
            contentPayload = item.sessionPayload,
        )
    }
}

private data object PagerWidgetLazyItemContent : WidgetLazyItemContent {
    override fun render(
        builder: UiTreeBuilder,
        payload: Any?,
    ) {
        (payload as PagerLazyItemPayload).content.invoke(builder)
    }
}

// VerticalPager.

/**
 * Emits a vertical pager with one keyed lazy session per page.
 *
 * Settled callback and [pagerState] semantics match [HorizontalPager]. The pager owns discrete page
 * selection only; a page whose focused content can be occluded must declare its own vertical scroll
 * owner.
 *
 * @sample com.viewcompose.ui.foundation.samples.pagerAndTabIdentitySample
 * @sample com.viewcompose.ui.foundation.samples.focusVisibilityOwnershipSample
 * @receiver active tree builder receiving the pager node
 * @param currentPage controlled page selected for this render
 * @param onPageChanged callback receiving a newly settled page index
 * @param pagerState optional caller-owned observation and command state
 * @param offscreenPageLimit adjacent-page residency limit, or `-1` for RecyclerView's default
 * @param userScrollEnabled whether direct pointer and accessibility paging is accepted
 * @param reusePolicy native page-presentation reuse hints
 * @param motionPolicy native page-mutation animation hints
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
    val sessionStrategy = PagerLazyItemSessionStrategy(localSnapshot, saveableStateHolder)
    val resolvedPages = builtPages.mapIndexed { index, page ->
        val saveableStateKey = saveableStateKeys[index]
        LazyListItem(
            key = page.key,
            contentType = page.contentType,
            contentRevision = page.contentRevision,
            environmentRevision = localSnapshot,
            sessionStrategy = sessionStrategy,
            sessionPayload = PagerLazyItemPayload(saveableStateKey, page.content),
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
     * Pass [StaticContentRevision] only when [content] has no changing ordinary non-State inputs;
     * otherwise the explicit revision must change with every such input that affects the tab.
     *
     * @param key unique identity used by ordinary keyed child reconciliation
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param content declaration receiving whether this tab is currently selected
     * @throws IllegalArgumentException when [key] duplicates another tab in this scope
     */
    fun Tab(
        key: Any,
        contentRevision: Any,
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
                        modifier = Modifier
                            .interactionIndication(
                                UiInteractionIndication.StateLayer(
                                    if (selected) {
                                        appearance.selectedStateLayerColors
                                    } else {
                                        appearance.unselectedStateLayerColors
                                    },
                                ),
                            )
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
    val contentRevision: Any,
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
