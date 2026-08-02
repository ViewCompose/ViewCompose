# ViewCompose Lazy Collections

## 1. Scope

Lazy collections keep Android `RecyclerView` and its layout managers as the scrolling, recycling,
focus, accessibility, nested-scroll, fling, and edge-effect engine. ViewCompose owns the
platform-independent item model, observable state, save/restore anchor, and renderer mapping.

The supported containers are `LazyColumn`, `LazyRow`, and `LazyVerticalGrid`. Pager state remains a
separate page-oriented model.

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
        initialPrefetchItemCount = 4,
        itemViewCacheSize = 4,
    ),
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
    ) { contact ->
        ContactRow(contact)
    }
}
```

The list scope supports `item`, `items`, and `stickyHeader`. The grid scope supports the same
structure plus per-item spans; a grid sticky header occupies the full line.

The convenience overload for homogeneous data also requires a stable key and delegates to the
structured model.

## 4. Renderer mapping

| Contract | Android mapping |
| --- | --- |
| stable key | collision-free adapter-local stable ID |
| content type | RecyclerView view type / recycled pool partition |
| item span | `GridLayoutManager.SpanSizeLookup` |
| sticky header | detached session-backed pinned holder + next-header push-off |
| pointer input on pinned header | transformed dispatch to the pinned holder |
| asymmetric content padding | relative RecyclerView padding |
| reverse layout | `LinearLayoutManager/GridLayoutManager.reverseLayout` |
| user scroll enabled | touch interception gate; programmatic scrolling remains available |
| initial prefetch count | layout manager initial prefetch |
| item cache size | RecyclerView item-view cache |
| layout state | scroll, layout, and adapter observers feeding `LazyListState` |

The pinned sticky copy is not registered as a second accessibility node. The ordinary list header
remains the semantic source, avoiding duplicate TalkBack announcements.

## 5. Invariants

1. Collection keys are non-null and unique within a container.
2. A key identifies the same logical item across reorders.
3. `contentType` groups only layout-compatible item structures.
4. Platform callbacks publish immutable snapshots; Android types never enter `ui-contract`.
5. Rebinding the same RecyclerView connector must not reset the scroll anchor.
6. Save/restore persists the first visible index and offset only.
7. Item sessions are disposed when holders, pinned headers, or containers are released.

## 6. Deliberate non-goals

Paging 3 adapters, remote loading/retry policy, custom fling physics, and compiler-driven sub-item
composition are separate integration concerns. Paging libraries can drive an immutable list and
use `isAtEnd`, `lastVisibleItemIndex`, and `layoutInfo.totalItemsCount` without coupling Android
paging types into the core contract.
