# Paging AndroidX Integration

`viewcompose-paging-androidx` connects AndroidX Paging generations to ViewCompose's existing
`LazyColumn` renderer. AndroidX remains the only paging engine; ViewCompose owns coherent observable
presentation, item Session identity, lifecycle collection, and native RecyclerView reconciliation.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-paging-androidx:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The collector, items owner, and container are guided Q3 APIs;
  `PagingLifecyclePolicy` is Q2 with closed Q1 entries.
- Platform: Android 7.0 (API 24) and newer.
- Dependency line: AndroidX Paging 3.5.1 and Kotlin coroutines 1.10.2.
- Optional: no aggregate artifact includes this module.
- `paging-common` is API-visible because `PagingData` and `CombinedLoadStates` appear in the public
  contract. `paging-runtime`, `paging-compose`, adapters, and a second diff owner are absent.

## Basic use

```kotlin
// `pages` is cached in an application-owned scope before it reaches the UI.
val pagingItems = repository.pages.collectAsViewComposePagingItems()

PagingLazyColumn(
    items = pagingItems,
    key = Contact::id,
    contentType = { "contact" },
    contentRevision = Contact::version,
) { contact ->
    ContactRow(contact)
}
```

When the `Pager` enables placeholders, select the explicit overload and version placeholder
appearance independently:

```kotlin
PagingLazyColumn(
    items = pagingItems,
    key = Contact::id,
    placeholderContentRevision = contactSkeletonVersion,
    placeholderContentType = "contact-placeholder",
    placeholderContent = { ContactPlaceholder() },
) { contact ->
    ContactRow(contact)
}
```

The placeholder-disabled overload rejects an unloaded slot. This makes accidental placeholder
enablement visible instead of silently rendering an empty item.

The application owns `Pager`, `PagingSource`, optional `RemoteMediator`, storage, network, query,
cache, and `cachedIn` scope. The integration calls the official `PagingDataPresenter`; it does not
reimplement loading, invalidation, generations, retry, refresh, or page events.

## Coherent state and commands

`ViewComposePagingItems` publishes item and `CombinedLoadStates` snapshots atomically. Page events
first update the presenter store, but become observable only after the matching final load state is
available. Reads during composition therefore never combine a new item list with the preceding
load-state revision.

`itemCount`, `loadedItemCount`, and `loadStates` are observable. Indexed `get` sends Paging's access
hint; `peek` is non-triggering and is the inspection path. `retry()` repeats failed loads in the
current generation, while `refresh()` requests the AndroidX-owned replacement. Indexed access and
commands run on the Android main thread. After the collecting call leaves composition, final
properties remain readable on a retained reference, but access and commands fail.

## Load-state composition

`contentState` is the primary-body projection, not a framework-owned layout. It returns
`InitialLoading`, `InitialError`, or `Empty` only while no item is loaded. Once any item is loaded,
`Content` wins during refresh, prepend, and append activity or failure, so directional UI does not
unmount the list:

```kotlin
when (val state = items.contentState) {
    PagingContentState.InitialLoading -> InitialLoading()
    is PagingContentState.InitialError -> InitialError(
        error = state.error,
        onRetry = items::retry,
    )
    PagingContentState.Empty -> EmptyResults()
    PagingContentState.Content -> key("contacts") {
        PagingLazyColumn(items = items, key = Contact::id) { contact -> ContactRow(contact) }
    }
}
```

For stable Header/Footer composition, select an operation once with
`loadStates.forLoadType(LoadType.PREPEND)` or `LoadType.APPEND`, then render its `combined` state
before or after the keyed list. The returned `PagingLoadStateSnapshot` also retains `source` and the
nullable `mediator` state from the same AndroidX snapshot. This permits separate source and mediator
diagnostics even when `combined` chooses one visible state. The helpers emit no nodes and select no
wording, analytics, auto-retry, offline, or destructive-refresh policy. Use `retry()` for failed
loads in the current generation and an explicit `refresh()` action for replacement.

## Lifecycle and upstream ownership

The default `Visible` policy requires the nearest `LocalLifecycleOwner` and collects at `STARTED`.
`Retained` collects at `CREATED`; `Composition` ignores Android lifecycle for custom hosts and test
fixtures. Inactive lifecycle policies retain the last coherent presentation. Flow identity creates
a new items owner, while a policy or non-`Job` context change serially restarts collection on the
same owner. Leaving composition cancels collection and releases presenter listeners.

Lifecycle restart follows the upstream Flow contract. A raw `Pager.flow` does not support a second
active collection; use AndroidX `cachedIn` in an application-owned scope before the Flow reaches the
default lifecycle-gated collector. `PagingData`, pages, presenter state, database rows, and network
responses are never saved by ViewCompose. Upstream Flow exceptions follow the render-session
coroutine failure route; Paging load failures stay in `CombinedLoadStates`.

## Identity, placeholders, and cost

Loaded keys must be stable and unique. `contentRevision` must cover every changing ordinary value
captured by item content; observable State and framework environment retain their existing Session
semantics. The bridge also folds the current presenter index into its private revision, so moving an
unchanged key refreshes access routing while preserving the same key-owned Session and saveable
state. Paging access hints are sent only when an item Session activates, not while composition scans
the presented list. RecyclerView, the Android Renderer, and existing lazy-list policies remain the
sole scrolling, stable-ID, reuse, diff, and transaction owners.

The explicit placeholder overload builds a compact indexed table whose metadata is proportional to
loaded items, not `itemCount`. Unloaded items are calculated from placeholder counts on positional
lookup; there is no full placeholder-object table or public placeholder key. Placeholder identity
is private, positional, and namespaced by items owner plus Paging generation. Loaded identity uses
a separate private namespace around the application key, so the two domains cannot collide.
`placeholderContentRevision` invalidates placeholder appearance without replacing its positional
identity, while a loaded replacement at that position terminates the placeholder Session.

Standard AndroidX refresh, prepend, append, and page-drop events become neutral bounded range
updates. If a renderer skips an intermediate table revision, the bridge requests `ReloadAll`
rather than replaying an unsafe event. Dropped loaded keys disappear from the table immediately so
the renderer disposes key-owned Sessions, effects, and saveable state for attached and detached
cached holders in the same committed submission. Non-triggering table
inspection sends no access hint; the hint is issued from the committed child Session's
`SideEffect`, after content is actually activated. A theme/Local, placeholder revision/type, or
loaded selector result change that is not represented by a Paging page event also requests a
conservative reload, ensuring the newer declaration is installed without enumerating placeholders.

## Verification and current scope

Deterministic tests cover presenter generations and commands, lifecycle/release, keyed routing,
placeholder replacement and invalidation, page drops, skipped revisions, primary-content branches,
per-`LoadType` source/mediator selection, and detached-cache disposal without double release.
Renderer coverage also updates 1,000,000 positions without full enumeration; Q3 samples compile
from public APIs.

On 2026-08-25, two Pixel 4 XL Android 13/API 33 debug tests passed in 5.51 s. The
1,000,000-position case added 48,124 KiB PSS, jumped to the last position in 555 ms, retained 81
loaded items under `maxSize = 96`, and released initial Sessions; bounded scrolling ended at 96
loaded items. Conclusion: **improved** memory, jump/drop, and ownership confidence. This local,
single-device/geometry evidence is not a frame, network, real `RemoteMediator`, physical load-state
UI, or Demo claim; later phases own those paths.

## Related documentation

- [Lazy Collections guide](../../guides/lazy-collections.md)
- [Lifecycle AndroidX module](../viewcompose-lifecycle-androidx/README.md)
- [Paging integration plan](../../project/plans/paging3-integration.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-paging-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/).
