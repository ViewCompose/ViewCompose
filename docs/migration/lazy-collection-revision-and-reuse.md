---
title: Migrate lazy collection revisions and reuse
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

```kotlin
stickyHeader(
    key = "messages-header",
    contentRevision = StaticContentRevision,
) {
    Text("Messages")
}
```

Pager pages now expose all caller-owned snapshot fields:

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

## Update native interop reuse

A lazy mounted tree containing `AndroidView` does not cross keys unless every interop node declares
`onReset`. Use reset only for replay-safe configuration cleanup. Keep one-shot publication in
`onCommit` and permanent resource cleanup in `onRelease`.

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

- Pager `offscreenPageLimit` now defaults to ViewPager2's native `-1` policy. Pass a value of at
  least `1` only when the application intentionally requires extra resident pages.
- `TabRow` content is eager keyed parent content. It no longer owns lazy child sessions. Stable tab
  keys retain remember/saveable identity across reorder, and selection changes invalidate only the
  old and new selected children.
- `CollectionReusePolicy.mountedTreeCacheSize` bounds reset physical trees per collection. A value
  of `0` disables mounted-tree caching without changing logical correctness.
- `LazyLayoutPrefetchPolicy.nestedInitialPrefetchItemCount` replaces
  `initialPrefetchItemCount`; the hint applies to nested collection prefetch. Unknown or expensive
  types are staged without synchronous native preparation.

## Update custom sessions and renderers

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

`LazyListItem.sessionUpdater` is now required. It must install the newest content closure or
equivalent immutable input into the existing session. A revision change never permits replacing a
same-key, same-type logical session as an implementation fallback.

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
