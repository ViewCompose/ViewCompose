# ViewCompose Lazy Collections

## 1. Scope

Lazy collections keep Android `RecyclerView` and its layout managers as the scrolling, recycling,
focus, accessibility, nested-scroll, fling, and edge-effect engine. ViewCompose owns the
platform-independent item model, observable state, save/restore anchor, and renderer mapping.

The supported containers are `LazyColumn`, `LazyRow`, and `LazyVerticalGrid`. Pager state remains a
separate page-oriented model, while eager `ScrollableColumn`/`ScrollableRow` use `ScrollState`.

## 2. Observable state

`LazyListState` is backed by snapshot state. Reading its properties during composition
automatically registers recomposition dependencies.

```kotlin
val state = rememberLazyListState()

Text(
    "visible=${state.layoutInfo.visibleItemsInfo.map { it.index }} " +
        "scrolling=${state.isScrollInProgress}",
)

Button(
    text = "Go to 20",
    onClick = { state.animateScrollToItem(20) },
)
```

The state exposes:

- first visible item index, key, and pixel scroll offset
- last visible item index
- visible item keys, content types, offsets, sizes, and grid spans
- viewport bounds, content padding, spacing, orientation, and reverse-layout state
- total item count
- scroll-in-progress and last scroll direction
- forward/backward scroll capability and start/end boundary state
- immediate scroll, animated scroll, and stop-scroll commands

Only the durable index and pixel offset are saved across host recreation. Visible geometry, active
scroll state, and direction flags belong to the current layout session.

## 3. Structured item DSL

Stable keys are required for all collection item helpers. Duplicate keys fail during tree
construction instead of silently disabling keyed diff.

```kotlin
LazyColumn(
    state = state,
    contentPadding = LazyContentPadding.symmetric(
        horizontal = 16.dp,
        vertical = 8.dp,
    ),
    prefetchPolicy = LazyLayoutPrefetchPolicy(
        nestedInitialPrefetchItemCount = 4,
        itemViewCacheSize = 4,
    ),
    reusePolicy = CollectionReusePolicy(mountedTreeCacheSize = 2),
) {
    stickyHeader(
        key = "contacts-header",
        contentType = "header",
    ) {
        Text("Contacts")
    }

    items(
        items = contacts,
        key = { contact -> contact.id },
        contentType = { "contact-row" },
        contentRevision = { contact -> contact.version },
    ) { contact ->
        ContactRow(contact)
    }
}
```

The list scope supports `item`, `items`, and `stickyHeader`. The grid scope supports the same
structure plus per-item spans; a grid sticky header occupies the full line. `contentRevision` is a
correctness contract, not only a performance hint. A changing value captured by item content must
be observed State or appear in this revision; equal key and revisions skip item rendering entirely.

The homogeneous-data overload can additionally skip construction of the complete logical item
snapshot. Pass an immutable aggregate `snapshotRevision` whose equality is constant-time:

```kotlin
LazyColumn(
    items = contactsSnapshot.items,
    key = { contact -> contact.id },
    contentType = { "contact-row" },
    contentRevision = { contact -> contact.version },
    snapshotRevision = contactsSnapshot.revision,
) { contact ->
    ContactRow(contact)
}
```

An equal non-null `snapshotRevision` plus an equal framework environment revision reuses the exact
committed `List<LazyListItem>`. The top-level homogeneous overload therefore does not invoke item
selectors, allocate item bindings, or traverse the item map on a hit. ViewCompose retains two
committed snapshots so an update/reset pair can reuse both versions. A failed composition never
publishes its candidate.

The token is an authoritative correctness contract. It must change when order, membership, key,
content type, item content revision, grid span, or an ordinary non-State item-content capture
changes. Changing the token reevaluates selectors, but an equal per-item `contentRevision` still
preserves that item's existing content closure. A non-State value read by item content must
therefore participate in both the aggregate token and every affected item's `contentRevision`.
Theme, resources, locale, layout direction, density, font scale, and other captured framework
locals automatically enter the environment revision. `null`, the default, disables complete-
snapshot reuse and preserves full selector evaluation. Observable State inside an item session
remains independently invalidated. Supplying a `List` as the token is valid only if its equality and
immutability are acceptable; a scalar data generation is the predictable fast path.

Scoped `items` accepts the same parameter. It can reuse the typed segment, but a scope containing
headers or multiple declarations must still merge segments and validate cross-segment keys; use the
homogeneous overload when whole-container O(1) snapshot reuse is the goal. Namespace revisions for
multiple typed declarations so equal values always describe an identical declaration. Reusing one
non-null value in the same scope is rejected before the candidate can commit. Typed collection
wrappers inside `ScrollableScope` expose and forward the same contract.

Grid columns use a sealed policy rather than an Android span count:

```kotlin
LazyVerticalGrid(cells = GridCells.Adaptive(minSize = 120.dp)) {
    item(key = "heading", span = GridItemSpan.FullLine) {
        Text("Gallery")
    }
    items(items = cards, key = { card -> card.id }) { card ->
        CardRow(card)
    }
}
```

`GridCells.Fixed` keeps an exact positive count. `GridCells.Adaptive` derives at least one column
from available inner width, minimum cell size, spacing, and density. `GridItemSpan.Fixed` is capped
to the current column count, `FullLine` tracks that count after resize, and `Fixed(1)` is
canonicalized to `Single`. A physical column-count change does not change key, revision, session,
remembered state, or effects.

## 4. Renderer mapping

| Contract | Android mapping |
| --- | --- |
| stable key | collision-free adapter-local stable ID |
| content type | native compatibility partition for empty holders and reset mounted trees |
| content revision | caller-owned semantic version used for targeted item invalidation |
| item span | `GridLayoutManager.SpanSizeLookup` |
| fixed/adaptive cells | current `GridLayoutManager.spanCount`, updated without adapter replacement |
| sticky header | detached session-backed pinned holder + next-header push-off |
| pointer input on pinned header | transformed dispatch to the pinned holder |
| asymmetric content padding | relative RecyclerView padding |
| reverse layout | `LinearLayoutManager/GridLayoutManager.reverseLayout` |
| user scroll enabled | touch interception gate; programmatic scrolling remains available |
| nested initial prefetch count | layout-manager hint used when the list is nested |
| item cache size | RecyclerView item-view cache |
| mounted-tree cache size | bounded framework-owned reset-tree cache with deterministic release |
| layout state | scroll, layout, and adapter observers feeding `LazyListState` |

A detached holder that has never been presented can use RecyclerView prefetch to compose and build
its Android View tree only after the renderer has observed that content type within its synchronous
cost budget. Unknown or expensive types are staged without native preparation. A prepared tree is
a candidate, not a committed child frame. Remember
activation, `SideEffect`, `DisposableEffect`, `LaunchedEffect`, native `AndroidView.onCommit`,
overlays, and committed diagnostics wait for first attachment. If observed state changes before
attachment, the stale candidate is abandoned and activation renders current state. A session that
already activated remains active through ordinary RecyclerView cache detach. Recycling terminates
its logical key session. A compatible, reset physical tree may then enter the bounded renderer
cache; RecyclerView's pool receives only the empty holder shell.

An `AndroidView` opts into cross-key mounted-tree reuse only by declaring `onReset`. The old logical
session and its effects are disposed before reset. Cache eviction or final container disposal calls
`onRelease` exactly once. A tree containing an interop View without `onReset` is released instead of
reused.

The pinned sticky copy is not registered as a second accessibility node. The ordinary list header
remains the semantic source, avoiding duplicate TalkBack announcements.

Each logical item key also owns a child saveable-state registry. Item-local automatic and explicit
`rememberSaveable` keys can therefore repeat in sibling rows. Detaching or recycling a holder
retains the logical item's saved map, and reattaching or reordering restores it by item key. A
detached pinned-header copy is a non-owning presentation replica: it may start from the owner's
current snapshot but cannot overwrite the header's persisted state.

`contentPadding` is logical and resolves start/end from the collection's captured layout
direction. It is added to physical `Modifier.padding` and the selected system-bar or IME inset
edges. The renderer retains this composite padding across native
View reuse and full environment rebinds, so a locale, direction, font-scale, density, or resource
revision change cannot temporarily expose content under a system bar or erase the list gutter.

## 5. Invariants

1. Collection keys are non-null and unique within a container.
2. A key identifies the same logical item across reorders.
3. `contentType` groups only layout-compatible item structures.
4. `contentRevision` includes every changing ordinary capture that is not observed State.
5. A non-null `snapshotRevision` versions the complete typed declaration and changes with order,
   membership, selector results, or ordinary non-State captures. Item-content captures also enter
   the affected `contentRevision`; `null` promises no aggregate skip. Scoped declarations use
   distinct namespaced non-null values.
6. Platform callbacks publish immutable snapshots; Android types never enter `ui-contract`.
7. Rebinding the same RecyclerView connector must not reset the scroll anchor.
8. Save/restore persists the first visible index and offset only.
9. Item sessions are disposed when holders, pinned headers, or containers are released.
10. Collection, modifier, and inset padding contributions have one renderer-owned native value and
   must survive both targeted patches and full environment rebinds.
11. Item saveable state is scoped by the container and stable logical key; duplicate providers are
   rejected only within one logical item scope.
12. Prefetch preparation is externally silent and never marks a child submission committed;
    activation and later active renders preserve the normal transactional effect order.
13. Logical key state never enters RecyclerView pools or mounted-tree caches; reset physical trees
    carry no remember, saveable, subscription, or effect identity.
14. Adaptive column changes update physical layout only; they do not rebuild keyed logical item
    sessions or become an application-owned content revision.

## 6. Deliberate non-goals

Paging 3 adapters, remote loading/retry policy, custom fling physics, and compiler-driven sub-item
composition are separate integration concerns. Paging libraries can drive an immutable list and
use `isAtEnd`, `lastVisibleItemIndex`, and `layoutInfo.totalItemsCount` without coupling Android
paging types into the core contract.
