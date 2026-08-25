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
class ContactsViewModel : ViewModel() {
    val pages = Pager(config, pagingSourceFactory = repository::contacts)
        .flow
        .cachedIn(viewModelScope)
}

val pagingItems = viewModel.pages.collectAsViewComposePagingItems()

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

`contentState` is the primary-body projection, not a framework-owned layout. With no loaded item,
an error from combined, source, or mediator refresh selects `InitialError`; otherwise any refresh
loading selects `InitialLoading`, and only fully completed refresh states select `Empty`. This
prevents a source failure from appearing empty when AndroidX's combined refresh defers to an
installed mediator. Once any item is loaded, `Content` wins during refresh, prepend, and append
activity or failure, so directional UI does not unmount the list:

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

Lifecycle restart follows the upstream Flow contract. A raw `Pager.flow` does not support repeated
collection; apply AndroidX `cachedIn` once in an application-owned scope such as a ViewModel before
the Flow reaches the lifecycle-gated collector. Hiding a `Visible` destination cancels only its UI
collector; revealing or recreating it replays the cached generation without duplicating upstream
loading. Cancelling the application scope ends that cache. ViewCompose never saves `PagingData`,
pages, presenter state, database rows, or network responses. Upstream Flow exceptions follow the
render-session coroutine failure route; cancellation is not converted to a load failure.

With `RemoteMediator`, the application database or equivalent store remains the source of truth:
the mediator writes it and invalidates its `PagingSource`. ViewCompose observes AndroidX's real
combined/source/mediator states and owns neither storage nor network work. If a mediator skips its
initial refresh, combined refresh may defer to mediator `NotLoading` while source refresh fails;
`contentState` preserves that source failure, and `forLoadType(REFRESH)` exposes its exact origin.

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

## Demo and deterministic testing

Open `collection.paging` in the Demo catalog. Its real in-process `Pager + PagingSource` suspends
each load until **Resolve pending load** applies Data, Empty, or Error; the action then becomes
**Request next page** or **Retry failed load**, while **Reset generation** replaces the Flow. This
exposes initial, append, retry, empty, and error states without I/O or a published production fake.

Automation should select the scenario by `collection.paging` and use its stable `root`, `ready`,
`primary_action`, `secondary_action`, `reset`, `state`, `target`, and `secondary_target` roles.
The state target reports body, refresh/append, and loaded count. Compiled Q3 samples cover
placeholder-aware `pagingLazyColumnSample` and directional `pagingLoadStateCompositionSample`.
Test repositories and `PagingSource` below UI, use AndroidX `paging-testing` for snapshots, and
reserve device tests for rendering and interaction. Mediator fixtures may fake storage and remote
results, but must run real AndroidX source/mediator coordination rather than UI booleans.

The benchmark host also exposes `performance.paging@1`. Its immediate local source presents one
million positions with pages of 32, a maximum loaded window of 96, placeholders, jumps, and
query-separated stable keys. **Next page**, **Replace query**, and **Reset** provide stable
automation targets for append/drop, generation replacement, and recovery; this route is a Release
integration workload, not a production fake source or an engine comparison.

## Migration

From Compose Paging, keep `Pager`, sources/mediator, repository, and the ViewModel-owned `cachedIn`
Flow. Replace `collectAsLazyPagingItems()` with `collectAsViewComposePagingItems()` and the Compose
item-count loop with `PagingLazyColumn`; provide a stable key and complete `contentRevision`. Map
the primary body through `contentState`, directional origin through `forLoadType(...)`, and keep
AndroidX `retry()`/`refresh()` semantics.

Keep finite lists on `LazyColumn` unless they need Paging generations, invalidation, eviction,
jumps, retry, or mediator coordination. Adoption moves callback/end-reached loading into
`PagingSource`, never a second `isLoading`/`isAtEnd` engine. Placeholders require the explicit
overload; positional loaded keys and materialized placeholder lists remain unsupported.

## Dependency and license notice

The published module exposes `androidx.paging:paging-common:3.5.1`; tests also use
`androidx.paging:paging-testing:3.5.1`. It does not require `paging-runtime` or `paging-compose`.
AndroidX Paging is distributed under Apache License 2.0 and is recorded in
`THIRD_PARTY_NOTICES.md`.

## Verification and current scope

Deterministic tests cover all three lifecycle policies, hidden/revealed navigation, `cachedIn`
replay across composition recreation, exact cancellation, real `Pager + RemoteMediator` refresh and
append failures, distinct source failure, presenter generations, placeholders, page drops, and
detached-cache disposal. Q3 samples compile from public APIs. The Demo adds a controlled real
`PagingSource` path and stable automation roles for initial, append, empty, error, retry, and reset.

On 2026-08-25, Pixel 4 XL Android 13 acceptance added 46,977 KiB PSS for 1,000,000 positions, jumped
to the end in 549 ms, retained 81 items under `maxSize = 96`, and released 58 dropped Sessions. A
separate bounded traversal ended at 96 loaded items and released 189 initial Sessions. The
controlled-Demo path covered initial loading, 10 rows, append error, retry to 20, reset, empty, and
initial error. The Release performance route also passed query replacement at target 32 and reset,
with a readable manual state of `q=1`, `loaded=64`, and `max=96`.

The first fixed-clock Release baseline used a rooted Xiaomi MI 6 / Android 9 and five iterations per
method. Append/drop recorded `4.281/29.189/33.973/43.592 ms` P50/P90/P95/P99, median peak heap
`117,797 KiB`, and run-P50 CV `0.077`; query replacement recorded
`4.215/13.810/40.809/48.345 ms`, `128,433 KiB`, and `0.021`; scroll recorded
`2.581/3.699/4.066/6.511 ms`, `119,087 KiB`, and `0.006`. All rows pass the `0.15` stability ceiling.
Conclusion: correctness, bounded-memory, device, Demo, and first absolute Release-baseline
confidence are **improved**; normalized performance direction remains **inconclusive** because no
compatible prior exists.

The evidence covers two devices but only one fixed-clock OEM/API, immediate local pages, and peak
process heap. Android 9 emitted no RSS; the values are not deltas or post-GC retained memory. Real
database/network/disk I/O, prepend UI, calibrated energy, startup, total duration, and a directional
performance comparison remain unproven. Future claims require a matching workload and controlled
longitudinal baseline; the exact protocol and results live in the performance guide.

## Related documentation

- [Lazy Collections guide](../../guides/lazy-collections.md)
- [Lifecycle AndroidX module](../viewcompose-lifecycle-androidx/README.md)
- [Archived Paging integration plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/paging3-integration.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-paging-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/).
