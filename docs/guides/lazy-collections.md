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
        contentRevision = StaticContentRevision,
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
Single `item`, `stickyHeader`, pager `Page`, and `Tab` declarations therefore require an explicit,
non-null revision. Their signature order is `key`, `contentRevision`, then optional physical-reuse
or layout policy such as `contentType` and grid `span`. `null` is not a static shortcut;
`StaticContentRevision` is the named promise for truly static ordinary inputs. The bulk
`contentRevision: (T) -> Any? = { it }` selector remains nullable and is safe by default only for
immutable value models whose equality covers every ordinary non-State value read by item content.

Every ordinary homogeneous or scoped `List` declaration evaluates its ordered elements and invokes its
`key`, `contentType`, `contentRevision`, and grid-span selectors on every parent composition pass.
ViewCompose deliberately does not treat list reference identity, list equality, or a caller-owned
aggregate token as proof that the complete declaration is unchanged: Kotlin `List` values may have
mutable aliases, and the framework cannot infer ordinary lambda captures without a compiler
transform.

This selector pass does not imply that every item renders again. After evaluation, an item with the
same key, content revision, captured framework environment, content type, kind, and span reuses its
committed logical item and Session binding. A changed list may therefore preserve all unaffected
item Sessions, while a changed `contentRevision` targets only the affected item. Theme, resources,
locale, layout direction, density, font scale, and other framework locals automatically enter the
environment revision. Observable State read inside an item Session remains independently tracked.
An ordinary non-State value read by item content cannot be inferred automatically and must still
participate in that item's `contentRevision`.

Bulk typed and strong-snapshot declarations use one declaration-shared item-session strategy. Each
committed item stores its source model as an opaque payload rather than allocating its own factory,
updater, and model-capturing content closure. RecyclerView Holder binding passes the selected item
directly to that strategy, so the reduced retained set does not move callback construction onto the
scrolling path. This storage optimization does not change key, revision, State, or effect ownership.

### Explicit whole-snapshot fast path

When an application already owns an immutable list snapshot and needs to avoid the selector and key
scan on a stable parent recomposition, it may create a `LazyItemsSnapshot` at that state boundary:

```kotlin
val contactsSnapshot = remember(contacts) {
    contacts.toLazyItemsSnapshot()
}

LazyColumn(
    items = contactsSnapshot,
    key = { contact -> contact.id },
    contentType = { "contact-row" },
    contentRevision = { contact -> contact.version },
) { contact ->
    ContactRow(contact)
}
```

`toLazyItemsSnapshot()` shallow-copies item references in iteration order and gives the result an
opaque framework identity; it does not evaluate selectors or deep-copy item models. Each consuming
container evaluates the selectors when it first sees that identity in the current framework
environment. It retains the current and immediately previous successfully committed
snapshot/environment pair. An exact hit returns the cached ordered logical-item list in constant
time without invoking selectors or hashing item keys, so alternating between two committed
snapshots also stays on the fast path. A changed environment is deliberately a miss and reevaluates
selectors so theme, resources, locale, direction, density, and other locals remain correct.
Only State read while item content executes in its active Session remains independently observed.
State or another changing input read by a selector is frozen into the evaluated snapshot and
therefore requires a replacement snapshot. If a selector throws or keys are duplicated, the failed
declaration publishes no evaluated snapshot; a retry reevaluates every selector.

The application must replace the `LazyItemsSnapshot` whenever order, membership, retained item
data, selector captures, or ordinary non-State values captured by item content change. Item-content
captures must also enter the affected item's `contentRevision`; without a compiler transform the
framework cannot infer them. Creating a new snapshot on every composition is correct but forfeits
the exact-identity fast path. A new identity with equal content still performs the selector pass,
then may canonicalize unchanged keyed items. This overload is available only on homogeneous
top-level and `ScrollableScope` `LazyColumn`, `LazyRow`, and `LazyVerticalGrid`; scoped
`LazyColumn { items(...) }` and `LazyVerticalGrid { items(...) }` deliberately keep the safe
per-pass contract and have no snapshot overload.

Grid columns use a sealed policy rather than an Android span count:

```kotlin
LazyVerticalGrid(cells = GridCells.Adaptive(minSize = 120.dp)) {
    item(
        key = "heading",
        contentRevision = StaticContentRevision,
        span = GridItemSpan.FullLine,
    ) {
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

The Android adapter accepts Q3 `LazyItemTable`: finite Foundation declarations provide an indexed
wrapper, while compact integrations can calculate positions on demand and publish neutral range
updates. Stable IDs are collision-safe and allocated lazily for queried keys rather than by
enumerating the entire table. The table owns key-to-position lookup; optional sticky-header
metadata avoids a full scan when present. Invalid declared operations or duplicate keys reject the
candidate atomically. RecyclerView view types remain stable for the mounted container lifetime, including
when one type temporarily disappears. A container accepts at most 1,024 distinct
`kind`/`contentType` compatibility classes; exceeding this limit fails immediately instead of
retaining an unbounded type history. Model values and revisions do not belong in `contentType`.

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
3. `contentType` groups only layout-compatible item structures and must come from a finite
   taxonomy; one mounted Android container supports at most 1,024 distinct `kind`/`contentType`
   combinations.
4. A single item, sticky header, page, or tab provides a non-null `contentRevision` immediately
   after `key`; optional `contentType` and layout policy follow it. `null` is not a static sentinel,
   and `StaticContentRevision` is valid only with no changing ordinary non-State input. A bulk
   nullable `{ it }` default requires an immutable value model whose equality covers every such
   input.
5. Every ordinary typed `List` declaration reevaluates order, membership, and item selectors on each parent
   composition pass; equal key, content revision, environment, content type, kind, and span may
   reuse the committed logical item afterward.
6. An exact `LazyItemsSnapshot` identity/environment pair may bypass selectors and the key scan;
   any structural, retained-data, selector-capture, or ordinary content-capture change requires a
   replacement snapshot, and an environment change always reevaluates selectors.
7. Platform callbacks publish immutable snapshots; Android types never enter `ui-contract`.
8. Rebinding the same RecyclerView connector must not reset the scroll anchor.
9. Save/restore persists the first visible index and offset only.
10. Item sessions are disposed when holders, pinned headers, or containers are released.
11. Collection, modifier, and inset padding contributions have one renderer-owned native value and
   must survive both targeted patches and full environment rebinds.
12. Item saveable state is scoped by the container and stable logical key; duplicate providers are
   rejected only within one logical item scope.
13. Prefetch preparation is externally silent and never marks a child submission committed;
    activation and later active renders preserve the normal transactional effect order.
14. Logical key state never enters RecyclerView pools or mounted-tree caches; reset physical trees
    carry no remember, saveable, subscription, or effect identity.
15. Adaptive column changes update physical layout only; they do not rebuild keyed logical item
    sessions or become an application-owned content revision.

## 6. Deliberate non-goals

Paging 3 loading, invalidation, and retry remain outside the core collection contract. The optional
[`viewcompose-paging-androidx`](../modules/viewcompose-paging-androidx/README.md) artifact now adapts
the official presenter into `PagingLazyColumn` without moving Paging types or loading policy into
UI Foundation. Its explicit placeholder overload and page-drop handling use the neutral compact
item-table contract; the placeholder-disabled overload rejects unloaded slots. Paging remains the
loading, generation, retry, and refresh owner, while Android Renderer remains the only adapter,
stable-ID, diff, holder, and Session owner. The
[archived Paging 3 integration plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/paging3-integration.md)
completed its controlled Demo, million-position device path, stable first Release baseline, Maven
proof, and release closeout. Row/grid support, real I/O, or a directional longitudinal performance
claim now requires a newly attributed plan. Finite-list applications may
continue to use `isAtEnd`, `lastVisibleItemIndex`, and `layoutInfo.totalItemsCount` without any Paging
dependency. Custom fling physics and compiler-driven sub-item composition remain separate
integration concerns.
