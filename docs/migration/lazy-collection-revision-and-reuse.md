---
schema_version: 2
document_id: migration.lazy-collection-revision-reuse
doc_type: migration
owner:
  kind: capability
  id: lazy.collections
version_lane: released
capability_ids:
  - lazy.collections
  - host.android-view
  - renderer.reconciliation
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-host-android
  - viewcompose-renderer-android
sample_ids:
  - migration.lazy-typed-revision
  - migration.lazy-static-revision
  - migration.lazy-pager-revision
  - migration.lazy-named-item
  - migration.lazy-snapshot
  - migration.lazy-implicit-siblings
  - migration.lazy-explicit-root
  - migration.lazy-android-view-reuse
  - migration.lazy-item-table
source_state: The retired alpha contentToken, aggregate snapshotRevision, multi-root delayed holder, and coupled logical/physical reuse contracts.
target_state: Explicit semantic content revisions, immutable snapshot identity, single-root delayed content, resettable Android Views, and separated logical Session and physical presentation ownership.
---

# Migrate lazy collection revisions and reuse

## Scope

This guide covers the alpha hard cut from callback-sensitive `contentToken` behavior to explicit
logical item revisions and separated physical presentation reuse. It applies to `LazyColumn`,
`LazyRow`, `LazyVerticalGrid`, `HorizontalPager`, `VerticalPager`, `TabRow`, custom
`LazyListItemSession` implementations, and lazy items containing `AndroidView`.

## Replace content tokens with semantic revisions

Rename item and page `contentToken` arguments to `contentRevision`. The value is no longer a loose
hint: equal key, content revision, and framework environment revision skip the item render
completely. A changing ordinary Kotlin capture must therefore enter the revision.

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-typed-revision" sample_id="migration.lazy-typed-revision" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
LazyColumn(
    items = messages,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
) { message ->
    MessageRow(message)
}
```

Bulk item overloads may keep their `{ it }` default only for immutable value models whose equality
covers every ordinary non-State value read by item content. Mutable models need an explicit
immutable version or snapshot. Values backed by ViewCompose `State` remain observable and do not
need duplication in the revision when item content reads them inside its active Session.

Single `item`, `stickyHeader`, pager `Page`, and `Tab` declarations no longer default their content
revision from the key. Their `contentRevision` is required and non-null; `null` is not a static
sentinel. Use `StaticContentRevision` only when the declaration has no changing ordinary non-State
input:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-static-revision" sample_id="migration.lazy-static-revision" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
stickyHeader(
    key = "messages-header",
    contentRevision = StaticContentRevision,
) {
    Text("Messages")
}
```

Pager pages now expose all caller-owned snapshot fields:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-pager-revision" sample_id="migration.lazy-pager-revision" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
Page(
    key = account.id,
    contentRevision = account.version,
    contentType = "account-page",
) {
    AccountPage(account)
}
```

These single-entry declarations place `contentRevision` immediately after `key`, followed by
optional physical-reuse or layout arguments such as `contentType` and grid `span`. This ordering
keeps logical identity and semantic content revision together and leaves physical presentation
policy afterward. Bulk `items` overloads intentionally keep the nullable
`contentRevision: (T) -> Any? = { it }` selector: a nullable element or selector result can be a
real immutable model state, while a single declaration must express an intentional non-null
revision or `StaticContentRevision`.

This is a source-breaking alpha change. Recompilation alone is not a sufficient migration for
positional source calls. An old three-position call such as
`item(key, contentType, contentRevision)` or `Page(key, contentType, contentRevision)` can still
type-check after the signature change because both semantic values accept `Any`; it then treats the
old `contentType` as the revision and the old revision as the physical content type. Rewrite it as
`item(key, contentRevision, contentType)` or `Page(key, contentRevision, contentType)`. Prefer named
semantic arguments in maintained source:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-named-item" sample_id="migration.lazy-named-item" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
item(
    key = message.id,
    contentRevision = message.version,
    contentType = "message-row",
) {
    MessageRow(message)
}
```

Then recompile every consumer rather than mixing binaries built against the earlier single-entry
parameter order with the new artifact. On the JVM, adjacent `Any?`/`Any` parameters can erase to
the same `Object` descriptor, so an old call may not fail to link and can instead bind the former
`contentType` and `contentRevision` values to the opposite semantics. Named arguments protect the
reviewed source call, but do not make an already compiled old call safe.

Pager pages and tabs now require explicit, unique keys. Position is physical placement, not logical
identity; the framework no longer guesses that an unkeyed child at the same index owns the previous
child's remember, saveable state, or effects.

The framework automatically captures theme, Android resource, locale, direction, density, font
scale, and other active local values as `environmentRevision`; applications must not duplicate
those values in `contentRevision`.

## Replace aggregate tokens with explicit snapshot values

Typed `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, scoped `items`, and their `ScrollableScope`
wrappers do not accept a caller-owned aggregate snapshot revision. Remove `snapshotRevision` from
calls that used the interim API:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-typed-revision" sample_id="migration.lazy-typed-revision" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
LazyColumn(
    items = messages,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
) { message ->
    MessageRow(message)
}
```

Every declaration pass now evaluates list order and membership and invokes `key`, `contentType`,
`contentRevision`, and grid-span selectors. The framework does not trust list identity, list
equality, or an independently maintained version to bypass these checks. This avoids stale order,
membership, or selector output when a caller forgets to advance a parallel token, and scoped
declarations no longer need caller-defined token namespaces.

Selector evaluation does not discard keyed reuse. After the pass, equal key, content revision,
framework environment, content type, item kind, and span reuse the previously committed logical
item and Session binding; changed rows remain targeted. Observable State read by an item Session is
tracked independently. Because ViewCompose has no compiler transform that can identify arbitrary
Kotlin captures, every changing ordinary non-State value read by item content must still enter the
affected item's `contentRevision`. Callers compiled against the interim aggregate-parameter method
descriptors must recompile for this alpha hard cut.

For a homogeneous top-level or `ScrollableScope` container, an application that already owns an
immutable list submission may opt into the strongly typed whole-snapshot path:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-snapshot" sample_id="migration.lazy-snapshot" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
val lazyMessages = remember(messages) {
    messages.toLazyItemsSnapshot()
}

LazyColumn(
    items = lazyMessages,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
) { message ->
    MessageRow(message)
}
```

`toLazyItemsSnapshot()` shallow-copies ordered item references and creates a new opaque identity; it
does not accept or evaluate selectors. Each consuming container evaluates selectors on the first
declaration of that identity in a framework environment and retains its current and immediately
previous successfully committed snapshot/environment pair. An exact pair restores the ordered
logical-item list in constant time without selectors or a key scan. A new identity or environment
change is a miss and follows the ordinary keyed canonicalization path.
Only State read while item content executes in its active Session remains independently observed.
State or another changing input read by a selector requires a replacement snapshot because an exact
hit skips selectors. Selector or duplicate-key failure publishes no evaluated snapshot, so retrying
the same identity and environment reevaluates every selector.

Replace the `LazyItemsSnapshot` whenever order, membership, retained item data, selector captures,
or ordinary non-State item-content captures change. Those item-content captures must also enter the
affected `contentRevision`; the framework still has no compiler transform that can infer them.
Creating the snapshot on every composition remains correct but forfeits the identity fast path.
Scoped `LazyColumn { items(...) }` and `LazyVerticalGrid { items(...) }` deliberately have no
`LazyItemsSnapshot` overload and continue evaluating selectors on every declaration pass.

## Wrap delayed siblings in one explicit root

Each lazy `item`, `stickyHeader`, typed item-content invocation, and pager `Page` owns one native
holder and must now emit exactly one root node. The former multi-root behavior silently placed
siblings in the same neutral holder without defining vertical, horizontal, or overlay geometry.
The hard cut rejects both zero and multiple roots during composition preparation, before any native
candidate is committed. Use `Spacer` when an entry intentionally has no visible content.

Replace implicit siblings:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-implicit-siblings" sample_id="migration.lazy-implicit-siblings" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
item(key = "account", contentRevision = account.version) {
    Text(account.name)
    Text(account.status)
}
```

with an explicit layout owner:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-explicit-root" sample_id="migration.lazy-explicit-root" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
item(key = "account", contentRevision = account.version) {
    Column {
        Text(account.name)
        Text(account.status)
    }
}
```

The same rule applies to `HorizontalPagerScope.Page` and `VerticalPager` pages. `TabRow` remains
eager parent content and is not part of this delayed-holder restriction.

## Update native interop reuse

A lazy mounted tree containing `AndroidView` does not cross keys unless every interop node declares
`onReset`. Use reset only for replay-safe configuration cleanup. Keep one-shot publication in
`onCommit` and permanent resource cleanup in `onRelease`.

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-android-view-reuse" sample_id="migration.lazy-android-view-reuse" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> bindPlayer(view as PlayerView, item) },
    onReset = { view -> resetPlayer(view as PlayerView) },
    onRelease = { view -> (view as PlayerView).release() },
)
```

The old logical session, remember state, subscriptions, and effects end before reset. The renderer
may then reuse the physical tree for a different key with the same `contentType`. A bounded,
renderer-owned cache invokes final release on eviction; RecyclerView pools only empty holder
shells. Omit `onReset` when a View cannot safely support this lifecycle.

## Update container assumptions

- Pager `offscreenPageLimit` defaults to the renderer's RecyclerView caching policy at `-1`. Pass a
  value of at least `1` only when the application intentionally requires that many adjacent
  page-sized layout spaces on each side.
- Remove every `focusFollowKeyboard` argument. Focused editors in LazyColumn, LazyVerticalGrid, and
  ScrollableColumn now use Android's native rectangle-request chain automatically. A
  VerticalPager page that can be obscured by the IME must place its form inside a page-local
  ScrollableColumn, LazyColumn, or another real vertical scroll owner; the pager owns page
  selection only.
- `TabRow` content is eager keyed parent content. It no longer owns lazy child sessions. Stable tab
  keys retain remember/saveable identity across reorder, and selection changes invalidate only the
  old and new selected children.
- `CollectionReusePolicy.mountedTreeCacheSize` bounds reset physical trees per collection. A value
  of `0` disables mounted-tree caching without changing logical correctness.
- `LazyLayoutPrefetchPolicy.nestedInitialPrefetchItemCount` replaces
  `initialPrefetchItemCount`; the hint applies to nested collection prefetch. Unknown or expensive
  types are staged without synchronous native preparation.

## Update custom sessions and renderers

Direct NodeSpec producers must also migrate the alpha collection boundary from
`List<LazyListItem>` to `LazyItemTable`:

{/* compiled-region source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/collection/ViewComposeLazyCollectionMigrationSamples.kt" region="migration-lazy-item-table" sample_id="migration.lazy-item-table" build_target=":samples:compose-migration:compileDebugKotlin" */}
```kotlin
LazyColumnNodeProps(
    contentPadding = LazyContentPadding.None,
    spacing = UiDp.Zero,
    items = itemModels.asLazyItemTable(),
)
```

Foundation `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` DSL call sites do not change. The finite
adapter validates unique keys and preserves ordinary keyed diff behavior. A custom compact source
may implement `LazyItemTable` directly, but its snapshot must be immutable; `get` and `indexOfKey`
must be synchronous and side-effect-free; and every declared `LazyItemTableUpdate` must exactly
transform the recognized predecessor. Return `null` to request the finite generic diff or
`ReloadAll` for an explicit conservative replacement. Invalid operations reject the complete
candidate rather than partially updating RecyclerView.

Custom renderers must not enumerate a compact table to prebuild all keys or stable IDs. Resolve
positions through `indexOfKey`, allocate collision-safe physical IDs independently of application
hashes, and consume optional `LazyItemTableStickyHeaders` metadata. A table that omits that metadata
promises it has no sticky entries. Iterating a table is a finite compatibility scan and may be
prohibitively expensive for virtual positions.

Custom `LazyListItemSession` implementations must preserve the full lifecycle:

1. optional externally silent `prepare`;
2. one `activate` before committed presentation;
3. `render` only for a changed content or environment revision;
4. `disposeForReuse` ending every logical owner before returning a reset physical presentation;
5. idempotent final `dispose` and `ReusableItemPresentation.release`.

`activate` and `render` now return `true` only when the installed content committed. Return `false`
after a rollback so the renderer does not advance the item revision and may retry the same
submission. Once a native frame committed, later side-effect or diagnostics failures do not change
the return value.

`LazyListItem` now owns one `sessionStrategy` plus one opaque `sessionPayload`; the former
`sessionFactory` and `sessionUpdater` constructor fields are removed. The strategy receives the
current item synchronously in both `create` and `update`, reads its payload, and must not retain the
item snapshot. `create` installs the initial payload and `update` installs a changed payload into
the existing Session. A revision change never permits replacing a same-key, same-type logical
Session as an implementation fallback.

Typed and strong-snapshot declarations share one strategy across every item in that declaration,
so committed storage no longer contains one factory/updater wrapper and one item-capturing content
closure per row. Low-level static implementations whose callbacks do not need the payload may use
`lazyListItemSessionStrategy(create, update)`; payload-aware implementations directly implement
`LazyListItemSessionStrategy`.

An adoption that returns `false`, or throws before ownership transfers, releases the presentation
immediately. A failed first cross-owner rebind must not invoke the old logical owner's update
callback or restore its visible frame; the adopted tree is released.

## Verification

Run the repository unit and documentation gates, then exercise the Diagnostics route in a release
build. Switch among Theme, Renderer, and Gaps and immediately perform forceful long flings to the
bottom and back to the top. Verify equal revisions produce no item render, changed revisions update
only the target key, old effects dispose before native reset, and cache eviction releases once.

See [ADR-0012](../architecture/decisions/0012-lazy-collection-logical-and-physical-ownership.md) and
the [lazy collection guide](../guides/lazy-collections.md) for the current architecture.
