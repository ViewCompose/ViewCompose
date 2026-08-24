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

The current alpha slice requires placeholders to be disabled. If an unloaded slot is present,
`PagingLazyColumn` rejects the candidate before publication. The slice builds one declaration and
key-table entry per loaded item, so composition and reconciliation are linear in loaded item count.
Placeholder, page-drop, and compact indexed-table support remain owned by the next active plan
phase; there is no full placeholder-object table or public placeholder key.

## Verification and current scope

Deterministic tests cover initial refresh, append/prepend access, retry, refresh, invalidation,
latest-query replacement, lifecycle stop/start retention, release, duplicate keys, and stable
key/index-safe access routing. The Q3 sample compiles from the module's public API. Physical-device
performance, placeholder/drop behavior, mediator-specific UI helpers, and the interactive Demo are
later plan phases and are not claimed by this slice.

## Related documentation

- [Lazy Collections guide](../../guides/lazy-collections.md)
- [Lifecycle AndroidX module](../viewcompose-lifecycle-androidx/README.md)
- [Paging integration plan](../../project/plans/paging3-integration.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-paging-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/).
