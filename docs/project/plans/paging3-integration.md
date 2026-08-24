# Paging 3 Integration Plan

## Status

Active. Phases 0 through 5 completed on 2026-08-25; Phase 6 is next. This
plan is the only active owner of Paging 3
delivery after the optional roadmap item and the Lazy Collections non-goal were redirected here on
2026-08-18. Historical archives retain only evidence.

Phase 0 pinned AndroidX Paging 3.5.1, compiled its public `PagingDataPresenter` contract with Kotlin
2.0.21, froze the API and lifecycle policies below, and rejected the current fully materialized
`List<LazyListItem>` path for placeholders and page drops. This plan is canonical English-only;
durable shipped contracts move to their owning active public documentation before archival.

Last verified: 2026-08-25.

Next action: begin Phase 6 samples, Demo, and documentation closeout. Phase 5 verified every frozen
lifecycle policy, application-owned `cachedIn` replay across navigation and recreation, structured
cancellation, and deterministic real-`RemoteMediator` source/mediator failures.

## Maven release changesets

- `release/changes/20260825-paging-presenter-characterization.json` — shared build wiring is
  explicitly release-neutral; no Maven artifact changes.
- `release/changes/20260825-paging-non-placeholder.json` — introduces the optional published
  non-placeholder frontend and explicitly classifies its shared registration inputs.
- `release/changes/20260825-paging-placeholder-compact-table.json` — hard-cuts the neutral lazy
  collection contract to an indexed table and records the placeholder/page-drop frontend plus
  renderer update support.
- `release/changes/20260825-paging-load-state-composition.json` — adds typed primary-content and
  per-load-type source/mediator projections without assigning framework-owned UI policy.
- `release/changes/20260825-paging-mediated-lifecycle.json` — fixes initial content projection to
  preserve source/mediator refresh failures and records lifecycle/mediator verification.

## Objective and ownership

Provide an optional AndroidX Paging frontend for ViewCompose lazy collections. AndroidX owns paging
generations, loading, invalidation, retry, refresh, page eviction, jumps, and source/mediator
coordination. ViewCompose owns observable presentation state, stable lazy-item identity, renderer
transactions, lifecycle-bound collection, samples, and documentation. The application continues to
own `Pager`, `PagingConfig`, `PagingSource`, `RemoteMediator`, storage, network, repository, query,
cache, and `cachedIn` policy.

Declarative rendering simplifies an already available immutable list; it does not replace the
concurrency and generation semantics above. The integration therefore uses the official custom-UI
presenter and never recreates a paging engine from collection primitives.

| Previous active location | Status after the split |
| --- | --- |
| [Unified roadmap](../roadmap.md), Collections next focus | Keeps a summary and delegates execution here. |
| [Lazy Collections guide](../../guides/lazy-collections.md), deliberate non-goals | Retains the core/integration boundary and delegates Paging delivery here. |
| `docs/archive/` | Remains historical evidence and never owns current status. |

## Frozen module and dependency boundary

| Concern | Frozen contract |
| --- | --- |
| Published artifact / package | `viewcompose-paging-androidx` / `com.viewcompose.paging` |
| ViewCompose dependencies | `viewcompose-ui-foundation` as API; `viewcompose-lifecycle-androidx` as implementation |
| AndroidX dependencies | `androidx.paging:paging-common:3.5.1` as API; `androidx.paging:paging-testing:3.5.1` in tests |
| Forbidden dependencies/owners | `paging-runtime`, `paging-compose`, `PagingDataAdapter`, `AsyncPagingDataDiffer`, Compose lazy containers, and any second RecyclerView adapter or diff owner |
| Integration-owned state | Active presenter generation, presented access, coherent items/load states, retry/refresh delegation, and collector disposal |
| First container | `LazyColumn`; row and grid require equivalent correctness, placeholder, memory, and device evidence in later work |

Paging types remain inside the optional artifact, its samples, and tests. They never enter
`viewcompose-runtime`, `viewcompose-ui-contract`, `viewcompose-ui-foundation`, Android Renderer, or
an SDK-neutral node. The optional artifact contributes no initializer, observer, recurring work, or
transitive dependency when absent. Only official public AndroidX APIs are allowed.

## Frozen public API

Names may change only by reopening Phase 0 with compiled call-site evidence before publication.

```kotlin
fun <T : Any> Flow<PagingData<T>>.collectAsViewComposePagingItems(
    lifecyclePolicy: PagingLifecyclePolicy = PagingLifecyclePolicy.Visible,
    context: CoroutineContext = EmptyCoroutineContext,
): ViewComposePagingItems<T>

enum class PagingLifecyclePolicy {
    Visible,
    Retained,
    Composition,
}

class ViewComposePagingItems<T : Any> internal constructor(...) {
    val itemCount: Int
    val loadedItemCount: Int
    val loadStates: CombinedLoadStates

    operator fun get(index: Int): T?
    fun peek(index: Int): T?
    fun retry()
    fun refresh()
}

sealed interface PagingContentState {
    data object InitialLoading : PagingContentState
    data class InitialError(val error: Throwable) : PagingContentState
    data object Empty : PagingContentState
    data object Content : PagingContentState
}

data class PagingLoadStateSnapshot(
    val loadType: LoadType,
    val combined: LoadState,
    val source: LoadState,
    val mediator: LoadState?,
)

val ViewComposePagingItems<*>.contentState: PagingContentState

fun CombinedLoadStates.forLoadType(loadType: LoadType): PagingLoadStateSnapshot

fun <T : Any> UiTreeBuilder.PagingLazyColumn(
    items: ViewComposePagingItems<T>,
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
)

fun <T : Any> UiTreeBuilder.PagingLazyColumn(
    items: ViewComposePagingItems<T>,
    key: (T) -> Any,
    placeholderContentRevision: Any,
    placeholderContent: UiTreeBuilder.(index: Int) -> Unit,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
    placeholderContentType: Any? = null,
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
)
```

The first overload is for placeholders disabled and fails before candidate publication if an
unloaded slot exists. The second requires explicit placeholder content and revision. Integration-
owned placeholder keys are positional and namespaced to the paging-items owner; loaded keys wrap
the application key in a separate domain. Placeholder/item transitions therefore cannot inherit
remember or saveable state, and there is no public `placeholderKey` escape hatch.

`get(index)` is the load-triggering presenter access used only by an active item Session; `peek`
is the non-triggering path for inspection, diagnostics, reconciliation, generic diff, and framework
prefetch. Loaded items require stable application keys and explicit revisions. `retry()` retries
failed loads in the current generation; `refresh()` requests the AndroidX-owned replacement.
`loadedItemCount` counts non-placeholder items without flattening source and mediator detail in
`CombinedLoadStates`.

The two Q3 projections remain pure rather than owning a paging layout. Any loaded item makes
`contentState` return `Content`. Without items, combined/source/mediator refresh errors win in that
order, otherwise any loading origin returns `InitialLoading`, and only all-complete refresh returns
`Empty`; this Phase 5 hard cut prevents a skipped mediator refresh from masking source failure.
`forLoadType` preserves combined, source, and nullable mediator states. Both are synchronous O(1)
reads with no dispatch, nodes, lifecycle, or retry policy.

### Lifecycle and presentation coherence

| Policy | Collection lifetime |
| --- | --- |
| `Visible` (default) | Requires the nearest `LocalLifecycleOwner`; collects at `STARTED` or above. Hidden retained destinations keep the last presentation without collecting. |
| `Retained` | Requires the nearest owner; collects at `CREATED` or above for explicitly retained presentation. |
| `Composition` | Ignores Android lifecycle and collects from successful commit until composition release for custom hosts, tests, and preview fixtures. |

Flow identity owns the remembered `ViewComposePagingItems`. Policy or context changes restart its
structured collector after commit without replacing accepted state. `context` may supply non-Job
elements; composition retains Job and cancellation ownership. Inactive policies retain the last
presentation; restart follows normal Flow semantics, including application-owned `cachedIn` replay.
Upstream Flow exceptions terminate the collector, while Paging load errors remain load-state data.

Initial state has zero items, source refresh `Loading`, prepend/append incomplete `NotLoading`, and
no mediator. One private immutable snapshot contains `ItemSnapshotList`, `CombinedLoadStates`, and a
monotonic revision. Publication occurs only after the presenter page store and combined load states
both advance, preventing mixed item/load-state revisions. Latest generation wins, and released or
superseded collectors cannot publish.

## Frozen Paging-neutral lazy prerequisite

The current full `List<LazyListItem>` NodeSpec allocates one declaration per presented position;
Android Renderer also builds a complete key table and list diff. Large placeholder counts and page
drops therefore require this Q3 compact snapshot and Q2 immutable updates in
`viewcompose-ui-contract`:

```kotlin
interface LazyItemTable : Iterable<LazyListItem> {
    val size: Int
    operator fun get(index: Int): LazyListItem
    fun toList(): List<LazyListItem>
    fun indexOfKey(key: Any): Int
    fun updatesFrom(previous: LazyItemTable): List<LazyItemTableUpdate>?
}

interface LazyItemTableStickyHeaders {
    val hasStickyHeaders: Boolean
    fun findStickyHeaderIndex(itemIndex: Int): Int
}

sealed interface LazyItemTableUpdate {
    data class InsertRange(val index: Int, val count: Int) : LazyItemTableUpdate
    data class RemoveRange(val index: Int, val count: Int) : LazyItemTableUpdate
    data class Move(val fromIndex: Int, val toIndex: Int) : LazyItemTableUpdate
    data class ChangeRange(val index: Int, val count: Int) : LazyItemTableUpdate
    data object ReloadAll : LazyItemTableUpdate
}
```

`LazyColumnNodeProps`, `LazyRowNodeProps`, and `LazyVerticalGridNodeProps` hard-cut `items` to this
contract in one breaking alpha change; existing finite lists receive a behavior-preserving wrapper.
A table is immutable per accepted revision. `get`/`indexOfKey` are synchronous, side-effect-free,
and non-triggering; invalid indices fail, missing keys return `-1`, and duplicate keys fail candidate
publication. `updatesFrom` returns an exact ordered transform for a recognized committed
predecessor, empty for semantic equality, or `null` for generic keyed fallback. Invalid operations,
wrong predecessors, or a mismatched result roll back without changing the installed adapter.
Iteration and `toList()` are full compatibility scans and not compact-source optimizations; the
finite wrapper returns its retained backing list and keeps structural equality. Optional sticky
metadata was added during implementation because the existing renderer otherwise had to scan every
virtual position merely to prove that no sticky header existed; non-implementers therefore promise
ordinary items only. This extension preserves the frozen Paging-neutral and sole-renderer boundary.

Paging stores loaded metadata plus placeholder counts, never one object per placeholder, and maps
accepted `PagingDataEvent` values to neutral range updates. Renderer remains the sole adapter,
stable-ID, holder, diff, and transaction owner. The prerequisite contains no Paging type, is useful
for other compact immutable indexed sources and custom renderers, and lands with Q3 samples,
manuals, compatibility notes, tests, and Changesets for UI Contract, UI Foundation, and Android
Renderer. A Paging-only adapter, parallel diff owner, or full placeholder table is forbidden.

## Delivery requirements

| Area | Required delivery |
| --- | --- |
| Presenter/state | Structured latest-generation collection; atomic presenter events; coherent item/load-state revisions; official access, peek, retry, and refresh behavior; exactly-once listener/collector disposal |
| Lazy bridge | Stable loaded-item state across inserts, moves, and replacement; isolated placeholder identity; prompt release after page drops; bounded compact metadata; separate AndroidX data access from non-triggering renderer prefetch |
| Load-state UI | Refresh, append, prepend, source, mediator, empty, loading, and error composition without framework-owned wording, visuals, analytics, auto-retry, offline, or destructive-refresh policy |
| Lifecycle/save | Frozen lifecycle policies; upstream `cachedIn` ownership; existing lazy scroll save/restore; no serialization of `PagingData`, pages, presenter, database, or network responses |
| Samples/Demo | Compiled Q3 sample; directly launchable in-process fake `PagingSource` Demo with stable automation roles and controlled initial/append/empty/error states; deterministic `RemoteMediator` example or fixture without production Room/network dependencies |
| Documentation/release | Module catalog/manual, setup, lifecycle, identity, placeholders, load states, cancellation, testing, migration, dependency notices, Chinese mirrors for active public pages, consumer proof, and immutable release Changesets |

An empty state requires completed refresh, no refresh error, and zero loaded items. Append failure
does not replace loaded content. Source and mediator failures remain inspectable even when a
convenience projection chooses the visible UI state.

## Non-goals

The integration does not reimplement AndroidX loading/caching/generation behavior, make Paging
mandatory for finite lists, persist pages through saveable state, select application paging/network/
database/error-copy policy, guarantee state for unstable or positional loaded keys, or publish a
callback-and-boolean infinite-scroll substitute. It does not introduce Paging into neutral modules,
host Compose collections, or give a Paging adapter ownership of the native list.

## Current baseline

The optional module publishes the official-presenter collector, three lifecycle policies, coherent
items/load states, commands, and explicit placeholder/no-placeholder `PagingLazyColumn` overloads.
Renderer prefetch remains presentation-only. The compact table stores loaded metadata, calculates
placeholders on demand, and maps page events to neutral updates. Phase 4/5 project primary content
and exact load origins, verify lifecycle replay/cancellation, and run real `RemoteMediator` control
flow over deterministic fake storage. No Paging adapter, Demo, real database/network result, or
broad Phase 7 performance claim exists yet.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0. Dependency and contract freeze | Complete | Paging 3.5.1 presenter path, artifact/package, lifecycle, overloads, LazyColumn-only scope, tests, and compact indexed-table prerequisite | Official API review and Kotlin 2.0.21 compile probes pass; full-table path is rejected with the Q3 replacement and breaking impact documented |
| 1. Presenter characterization harness | Complete | Non-published JVM module with deterministic generations, insert/drop, placeholder, hint, load-state, retry, refresh, invalidation, and cancellation fixtures | Six tests pass without Android Renderer, Android runtime, device, or network; explicit main-context injection and coherent publication ordering are frozen |
| 2. Non-placeholder LazyColumn slice | Complete | Optional module, observable items, stable-key bridge, latest generation, retry/refresh, and core Q3 sample | 12 deterministic tests, strict Q3 audit, local Maven consumers, complete API reconstruction, and the bilingual production site pass |
| 3. Placeholder and page-drop slice | Complete | Neutral indexed hard cut, positional placeholders, jump/drop handling | Implementation, deterministic contract tests, release intent, local publication, consumers, dropped-session/memory evidence, Pixel bounded-work verification, and post-ownership-hard-cut full gates pass |
| 4. Load-state composition | Complete | Pure primary-content and per-`LoadType` source/mediator projections plus empty/header/footer/error examples; no framework-owned layout | Twenty distinct tests pass in both variants; Q3 audit, bilingual docs, release intent, local publication, and isolated consumer pass |
| 5. Lifecycle and mediated data | Complete | Frozen policies, `cachedIn` guidance, recreation, deterministic mediator fixture, source-failure hard cut | Hidden/revealed navigation, cancellation, recreation, and source/mediator failure tests pass |
| 6. Samples, Demo, and documentation | Not started | Demo, Q3 samples, catalog/manual, setup/architecture/testing/migration docs, mirrors, notices | Localization, sample, automation-role, dependency, and consumer gates pass |
| 7. Performance, device, and release closeout | Not started | Same-build append/drop/large-generation/query/scroll evidence; final Changesets and Maven proof | No accepted correctness, leak, frame, or memory regression; evidence is interpreted before archival |

## Acceptance matrix

| Scenario | Required evidence |
| --- | --- |
| Initial refresh | Loading, data, empty, and initial error are distinct and deterministic |
| Append/prepend | Stable content remains mounted; directional state and retry are correct |
| Retry/refresh | Retry preserves the generation; refresh creates its AndroidX replacement |
| Query/invalidation | Latest generation replaces old state atomically; old hints/errors/states cannot publish |
| Placeholders/drop/jump | Identity never crosses loaded/unloaded domains; removed pages release Sessions and metadata; work remains bounded |
| Stable-key state | Insert, move, refresh, and mediator updates preserve state only for the same logical item |
| Source/mediator | Both origins remain inspectable through `CombinedLoadStates` |
| Lifecycle/recreation | Each policy, re-entry, and recreation avoid duplicate collection and honor upstream ownership |
| Failure/cancellation | Exceptions remain structured; cancellation is not a load failure; release leaks no collector |
| Performance/memory | Large generations, rapid appends, drops, query replacement, and flings meet recorded same-build budgets |

## Verification commands

```bash
./gradlew :viewcompose-paging-androidx:test
./gradlew auditViewComposeApiDocs -PviewComposeDocsModules=viewcompose-paging-androidx
./gradlew :integration-tests:paging-presenter:test
./gradlew verifyModulePackageRoots verifyAndroidModuleNamespaces verifyModuleDependencyBoundaries
./gradlew verifyDocumentationStructure
./gradlew qaQuick
./gradlew qaPreview
./gradlew qaFull
```

Missing device or credentials must be reported as prerequisites, not passes. Accepted performance
evidence records comparison context, absolute and normalized results, conclusion, limitations, and
next action in owning active documentation.

## API and documentation impact

The collector, items owner, both containers, and `LazyItemTable` are Q3. Lifecycle and immutable
range-update values are Q2; closed entries may be Q1 where their owner makes meaning unambiguous.

| API family | Required contract fields | Inapplicable fields |
| --- | --- | --- |
| Collector/items owner | Ordering; inputs; observable output/identity; ownership/retention; commit/start/stop/disposal; main-thread presenter, structured cancellation, latest generation, no-Job context; command timing; upstream/load failure; lifecycle-owner requirement; allocation cost; Paging compatibility | Application callback, resource, theme, measurement, coordinate, accessibility |
| `get`/`peek`/commands | Triggering distinction; bounds/null placeholder; generation; main-thread boundary; inactive behavior; failure/cancellation; delegation cost | Callback, child content, View ownership, saved payload |
| Both containers | Loaded keys/revisions/types; placeholder enablement/identity; standard list policies; content slots; observation; Session lifecycle; hint order; validation/rollback; layout/scroll/semantics/environment/Modifier; compact cost; first-container compatibility | Application I/O, repository/cache, permission/credential, native View ownership |
| Lifecycle policy | Threshold/default, owner, inactive retention, restart, disposal, migration | Output, callback, allocation; only missing/unsupported owner use can fail |
| Load-state projections | Coherent snapshot input; populated-content precedence; empty/error rules; combined/source/mediator preservation; structural value identity; observation and post-release reads; existing presentation-read threading plus synchronous O(1) work and one directional snapshot allocation; no UI, command, lifecycle, retry, or wording ownership | Android host, resource, theme, measurement, coordinates, accessibility, persistence |
| Indexed table/updates | Immutability, bounds/key lookup, predecessor/operation validation, renderer ownership, concurrency, rollback, storage/fallback complexity, custom-renderer and binary compatibility | Android type/lifecycle/Flow, callback, theme/resource/measurement, application persistence |

Every Q3 family lands with canonical-English KDoc and a compiled owning-module sample. The neutral
hard cut updates UI Contract, UI Foundation, and Android Renderer manuals and compatibility notes;
the Paging work updates its manual, Lazy Collections guide, roadmap, Demo docs, dependency metadata,
and required Chinese mirrors. The first publication-relevant change adds immutable
`release/changes/<unique>.json` entries for every affected artifact and replaces `- None.` above
with exact filenames.

## Completion criteria

Completion requires all frozen boundaries and acceptance rows to pass; official AndroidX behavior
to remain the only paging engine; lifecycle, mediator, cancellation, identity, leak, performance,
and memory evidence to be interpreted in active docs; samples, Demo, manuals, mirrors, dependency
metadata, Changesets, and Maven consumer proof to be complete; and roadmap/guide text to describe
shipped behavior. This file then moves to `docs/archive/` with no active source still marking the
work pending.

## Evidence ledger

| Date | Evidence | Result and next action |
| --- | --- | --- |
| 2026-08-18 | Worktree and active-document review | No Paging implementation existed; roadmap/guide execution ownership moved here. |
| 2026-08-25 | Phase 0–1 official API, Kotlin probes, CodeGraph review, and presenter harness | Paging 3.5.1 compiled with Kotlin 2.0.21 (12 s plus 1 s inference probe); 6/6 deterministic tests passed in 2 s. They freeze coherent event/load-state ordering, explicit non-Android main context, hint behavior, same-generation retry, replacement refresh/invalidation, cancellation, and both drop directions. Conclusion: **improved** contract confidence without a runtime-performance claim. Limitations: no Android lifecycle, renderer, mediator, device, network, or published frontend. |
| 2026-08-25 | Phase 2 frontend and publication closeout | 12/12 tests passed in 4 s; Q3 audit passed in 18 s; selected Maven publication and consumers passed 1,022 tasks in 4 min 19 s; 100-version plus 6-current API reconstruction passed in 7 min 23 s. The 440-page bilingual site measured 48,942,128 non-API bytes, +190,471 (+0.391%) from Phase 0, and moved the ceiling to 46.7 MiB. Conclusion: **improved** correctness/release confidence with **mixed** site size. Placeholders, mediator, Demo, device, frame, and memory remained later work. |
| 2026-08-25 | Pixel 4 XL Android 13/API 33 Phase 3 acceptance | Two debug tests passed in 5.51 s. The 1,000,000-position case added 48,124 KiB PSS, jumped to 999,999 in 555 ms, retained 81 items under `maxSize = 96`, and released initial Sessions; bounded scrolling ended at 96 loaded items. The probe exposed detached holders retaining dropped keys, now synchronously disposed with double-release proof. Conclusion: **improved** compact-memory, jump/drop, and lifecycle confidence. Limitations: one local-data device/API/geometry; no frame, mediator, network, load-state UI, or Demo evidence. |
| 2026-08-25 | Phase 3 full closeout | Four modules passed 1,015/1,015 tests (81 + 391 + 527 + 16); strict API/documentation checks passed for 118 English pages and 115 current mirrors in 15 s; `qaQuick` passed 2,324 tasks in 2 min 9 s. Conclusion: **improved** correctness and release confidence; later phases retain mediator, Demo, and broader performance limits. |
| 2026-08-25 | Phase 4 load-state projections and closeout | Twenty distinct tests passed in both variants (40 executions) in 6 s, covering initial/body states, content retention during directional loading/error, every `LoadType` origin, absent mediator, and retry/refresh distinction. Q3 audit passed in 12 s; documentation passed in 2 s; one feature release intent and the published consumer passed; `qaQuick` passed 2,324 tasks in 18 s. Conclusion: **improved** composition/API confidence without a second layout or state owner. Limitations: synthetic mediator states; no real mediator, database/network, recreation, Demo, device, frame, or memory path. Next: Phase 5. |
| 2026-08-25 | Phase 5 lifecycle and mediated-data closeout | Twenty-seven distinct module tests passed in Debug and Release (54 executions) in 6 s. Seven new tests cover every lifecycle policy, hide/reveal, `cachedIn` replay across composition recreation without duplicate upstream collection, in-flight mediator cancellation, real `Pager + RemoteMediator` refresh/append errors, and distinct source failure. The fixture exposed combined `NotLoading` masking source refresh failure; `contentState` now hard-cuts to origin-aware error/loading precedence. API/docs/release and dependency/isolation gates passed; `qaQuick` passed 2,324 tasks in 19 s. The 440-page site passed at 49,161,510 non-API bytes under the unchanged 46.9 MiB ceiling. Conclusion: **improved** lifecycle, cancellation, and mediated-state correctness with **mixed** small documentation growth. Limitations: in-memory store and fake remote result; no real database/network, Demo, device, frame, or memory result. Next: Phase 6. |

## Decision history

1. 2026-08-18 — Use AndroidX Paging as the optional engine and the public custom-UI presenter;
   preserve Android Renderer as sole adapter/diff owner.
2. 2026-08-18 — Keep Paging outside neutral modules and separate loaded/placeholder identity.
3. 2026-08-25 — Pin 3.5.1 with `paging-common`/`paging-testing`; exclude runtime and Compose.
4. 2026-08-25 — Default to visible (`STARTED`), with explicit retained (`CREATED`) and
   composition-only policies.
5. 2026-08-25 — First release is `LazyColumn` with distinct placeholder/no-placeholder overloads
   and private positional placeholder keys.
6. 2026-08-25 — Hard-cut full NodeSpec lists to compact `LazyItemTable` plus range updates; never
   add a Paging-only adapter, full placeholder table, or second diff owner.
7. 2026-08-25 — Ship the non-placeholder frontend first. Fold presenter index into the bridge's
   private content revision so moved stable keys refresh access routing without losing their
   Session/saveable state; require application-owned `cachedIn` for restartable `Pager.flow` use.
8. 2026-08-25 — Preserve any refresh-origin failure before selecting initial empty content; keep
   exact origin available through `forLoadType` and retain application ownership of cache/storage.
