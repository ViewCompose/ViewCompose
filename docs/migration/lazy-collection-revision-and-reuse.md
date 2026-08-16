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

Immutable data classes may keep the default item value as their revision. Mutable models need an
explicit immutable version or snapshot. Values backed by ViewCompose `State` remain observable and
do not need duplication in the revision.

Pager pages now expose all caller-owned snapshot fields:

```kotlin
Page(
    key = account.id,
    contentType = "account-page",
    contentRevision = account.version,
) {
    AccountPage(account)
}
```

Pager pages and tabs now require explicit, unique keys. Position is physical placement, not logical
identity; the framework no longer guesses that an unkeyed child at the same index owns the previous
child's remember, saveable state, or effects.

The framework automatically captures theme, Android resource, locale, direction, density, font
scale, and other active local values as `environmentRevision`; applications must not duplicate
those values in `contentRevision`.

## Opt into complete typed-snapshot reuse

Typed `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, and scoped `items` now accept
`snapshotRevision`. The default `null` preserves the previous correctness behavior: every
declaration pass invokes all item selectors. Use a non-null immutable aggregate revision only when
the application can version the complete typed declaration:

```kotlin
LazyColumn(
    items = messagesSnapshot.items,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
    snapshotRevision = messagesSnapshot.revision,
) { message ->
    MessageRow(message)
}
```

Equal aggregate and framework environment revisions authorize reuse of the exact committed logical
item list. The token must change with item order, membership, key, content type, item revision, grid
span, or any ordinary non-State content capture. Changing only the aggregate token reevaluates item
selectors but does not replace an item whose `contentRevision` remains equal, so an ordinary value
read by item content must also enter the affected item revision. Observable State remains
independently tracked. Prefer a constant-time comparable scalar revision rather than a collection
token. The cache keeps two committed snapshots, never publishes a failed composition, and retains
key/session/saveable ownership by the existing item key contract.

The top-level homogeneous overload is the whole-container fast path: a hit does not traverse item
selectors or the item map. Scoped declarations can reuse one typed segment, but headers and
multiple segments still require merge and cross-segment duplicate-key validation. Namespace the
revision when a scope contains multiple typed declarations; duplicate non-null values in one scope
fail before the candidate commits. Typed `ScrollableScope` wrappers expose and forward the same
parameter. Existing callers should use named arguments around the new optional parameter; this
alpha hard cut does not preserve binary linkage to the old method descriptors.

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
