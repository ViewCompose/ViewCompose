# Paging 3 Integration Plan

## Status

Active. Planning baseline only; no production implementation or publication input has started.
This plan was split out on 2026-08-18 from the optional Paging 3 follow-up in the unified roadmap
and the separate-integration note in the Lazy Collections guide. Those documents now point here;
this file is the only active plan that owns Paging 3 scope and delivery status.

This plan is canonical English-only under the documentation-governance policy. Every durable API,
behavior, dependency, and compatibility contract must move into active architecture, guide,
reference, and owning-module documentation before this plan is archived.

Last verified: 2026-08-18.

Next action: complete Phase 0 by selecting the reviewed AndroidX Paging baseline, proving the
custom-presenter path against the current lazy-collection contract, and freezing the module,
package, placeholder, lifecycle, and load-state decisions before production source is added.

## Maven release changesets

- None.

## Objective

Provide an optional AndroidX Paging 3 integration for ViewCompose lazy collections without moving
AndroidX Paging types or loading policy into the framework core. The completed integration must:

1. consume `Flow<PagingData<T>>` through official AndroidX Paging presentation primitives;
2. expose one ViewCompose-observable item and load-state surface with explicit refresh, retry,
   placeholder, generation, and cancellation semantics;
3. preserve lazy-item identity, revision, saveable state, renderer ownership, and transactional
   rendering while loaded pages are inserted, removed, invalidated, or replaced;
4. support local `PagingSource` and `RemoteMediator` pipelines without owning the application's
   database, network, repository, cache, or `cachedIn` policy;
5. add no AndroidX Paging dependency or recurring work when the optional integration is absent; and
6. ship compiled samples, Demo coverage, documentation, dependency verification, and deterministic
   tests for the supported paging matrix.

## Planning origin and ownership transfer

This table records where Paging 3 was previously mentioned and how status ownership changed. It
prevents the old candidate wording from becoming a second source of truth.

| Previous active location | Previous responsibility | Status after this split |
| --- | --- | --- |
| [Unified roadmap](../roadmap.md), Collections next focus | Listed optional Paging 3 integration as an unactivated follow-up | Superseded for execution tracking. The roadmap keeps a summary and links to this active plan. |
| [Lazy Collections guide](../../guides/lazy-collections.md), deliberate non-goals | Kept Paging adapters and remote loading/retry outside the core collection contract | Architectural boundary retained. The guide now delegates the optional integration's API and delivery work to this plan. |
| `docs/archive/` | Historical evidence, if an older record mentions adjacent paging work | Unchanged. Archived documents never carry current status and must not be rewritten to simulate a transfer. |

No other active plan owns Paging 3. If another plan discovers a prerequisite in the shared lazy
contract, that plan may own the neutral prerequisite only; this plan continues to own the Paging
dependency, adapter, public integration API, samples, compatibility, and release evidence.

## Why the integration uses AndroidX Paging

Declarative rendering makes an already available immutable list straightforward to display and
replace. It does not by itself provide paging generations, concurrent load arbitration, append and
prepend state, invalidation, cancellation, retry, refresh, page dropping, jump support, placeholder
semantics, request deduplication, or database/network mediation.

ViewCompose must therefore integrate the official AndroidX Paging library instead of implementing
an equivalent paging engine from existing collection primitives. AndroidX Paging owns the data
pipeline and presentation events. ViewCompose owns conversion of the currently presented data into
stable lazy declarations, observation through ViewCompose state, and user-facing composition of
items and load-state UI.

The integration must prefer the public custom-UI presenter surface, currently
`PagingDataPresenter`, rather than embedding `PagingDataAdapter`, `AsyncPagingDataDiffer`, or
`paging-compose`. Phase 0 must verify the selected version and public contract before this choice
is frozen. A second RecyclerView adapter or diff owner is forbidden because Android Renderer
already owns the native collection, recycling, patching, and rollback boundary.

## Proposed module and dependency boundary

The names below are planning targets. Phase 0 must confirm them against the module catalog,
five-layer migration state, publication coordinates, and the selected AndroidX Paging version.

| Concern | Planning target |
| --- | --- |
| Published artifact | `viewcompose-paging-androidx` |
| Package root | `com.viewcompose.paging` |
| ViewCompose dependency | The smallest public UI Foundation surface required to declare and observe lazy content |
| AndroidX dependency | `androidx.paging:paging-common` by default; add `paging-runtime` only if verified lifecycle/runtime behavior requires it |
| Explicitly excluded dependency | `androidx.paging:paging-compose`, RecyclerView `PagingDataAdapter`, and a second native collection owner |
| Application-owned inputs | `Pager`, `PagingConfig`, `PagingSource`, `RemoteMediator`, database, network client, repository, query stream, and `cachedIn` scope |
| Integration-owned state | Active presenter generation, presented item access, combined load states, retry/refresh delegation, and disposal of collection work |

Paging types may appear in this optional artifact, its samples, and its tests. They must not enter
`viewcompose-runtime`, `viewcompose-ui-contract`, `viewcompose-ui-foundation`, Android Renderer, or
an SDK-neutral collection node. Any shared collection prerequisite discovered during Phase 0 must
remain generically useful without AndroidX Paging on the classpath.

## Provisional public API shape

The following shape is a planning hypothesis, not an approved source contract. Phase 0 must assign
Q levels, enumerate all applicable contract fields, test Kotlin inference and lifecycle ownership,
and record the final signature before implementation.

```kotlin
fun <T : Any> Flow<PagingData<T>>.collectAsViewComposePagingItems(
    lifecyclePolicy: PagingLifecyclePolicy = PagingLifecyclePolicy.Visible,
): ViewComposePagingItems<T>

interface ViewComposePagingItems<T : Any> {
    val itemCount: Int
    val loadStates: CombinedLoadStates

    operator fun get(index: Int): T?
    fun peek(index: Int): T?
    fun retry()
    fun refresh()
}

fun <T : Any> UiTreeBuilder.PagingLazyColumn(
    items: ViewComposePagingItems<T>,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
    placeholderKey: ((index: Int) -> Any)? = null,
    placeholderContent: (UiTreeBuilder.(index: Int) -> Unit)? = null,
    state: LazyListState? = null,
    modifier: Modifier = Modifier,
    itemContent: UiTreeBuilder.(T) -> Unit,
)
```

The final API may use a scoped builder instead of `PagingLazyColumn`, but it must preserve these
properties:

1. indexed access used for visible/bind work reaches the presenter and therefore participates in
   AndroidX Paging prefetch-distance behavior;
2. non-triggering inspection is distinguishable from load-triggering access;
3. a loaded item requires an application-stable key and an explicit content revision;
4. a placeholder has deterministic positional identity that never masquerades as loaded-item
   identity;
5. `refresh()` creates the AndroidX-owned replacement generation and `retry()` retries failed loads
   in the current generation;
6. source and mediator load states remain distinguishable through `CombinedLoadStates`; and
7. cancelling composition collection stops presenter work but does not invent ownership over an
   upstream scope selected by the application through `cachedIn`.

If the current finite `LazyItemsSnapshot` path cannot represent placeholder slots or page drops
without rebuilding the complete table, Phase 0 may propose a generic read-only indexed lazy-data
contract in UI Foundation. That contract must be Paging-neutral, immutable per accepted revision,
transaction compatible, independently documented, and useful without this artifact. It cannot be
a disguised `PagingData` wrapper in a core package.

## Scope

### Presenter and observable state

1. Collect one `PagingData` generation at a time with structured cancellation and latest-generation
   semantics.
2. Apply accepted `PagingDataEvent` updates atomically to one observable presentation revision.
3. Publish item count, accessible presented items, and `CombinedLoadStates` from a consistent
   accepted presenter state; a render cannot combine items from one event with load states from an
   earlier event.
4. Delegate indexed reads, non-triggering peeks, retry, and refresh to official Paging behavior.
5. Dispose collectors and listeners exactly once when the owning composition scope is permanently
   released.

### Lazy-collection bridge

1. Render loaded data through existing logical item sessions and Android Renderer collection
   ownership.
2. Preserve state for a stable loaded-item key across insertions, page replacement, and moves, and
   release it when that logical item leaves the accepted dataset.
3. Keep placeholder identity positional and isolated from loaded-item remember/saveable identity.
4. Ensure page drops and large generations release item sessions, mounted trees, prepared-prefetch
   state, and renderer metadata outside the retained window.
5. Keep ViewCompose renderer prefetch and AndroidX data prefetch separate: indexed access may ask
   Paging for data, while renderer prefetch may only prepare framework presentation work that is
   already permitted by the existing lazy contract.
6. Define whether `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` share one bridge in the first
   release. The minimum accepted release is `LazyColumn`; additional containers require the same
   evidence rather than API aliases without coverage.

### Load-state UI and commands

The first release must support refresh, append, and prepend loading/error/end states without
building application visual policy into Paging. It may provide typed helpers or scoped declarations
for header/footer/empty/error content, but applications remain responsible for wording, visuals,
analytics, automatic retry, offline messaging, and destructive refresh confirmation.

An empty-state branch is valid only when refresh is complete, no refresh error is active, and the
accepted presentation contains no loaded item. Append failure must not replace already loaded
content. Source and mediator failures must remain inspectable even if a convenience projection
selects the user-visible state.

### Lifecycle, navigation, and saveable state

Collection belongs to the nearest composition scope and follows the selected documented lifecycle
policy. A hidden retained navigation destination must not continue presenter/UI collection merely
because its Activity is resumed, unless the caller explicitly selects a broader policy. Application
`cachedIn` ownership remains upstream and may deliberately outlive the UI collector.

The integration saves lazy scroll state through existing collection facilities. It does not
serialize `PagingData`, loaded pages, presenter internals, a database cache, or network responses.
After host recreation, AndroidX Paging recreates or resumes the application-owned pipeline and
stable keys restore meaningful presentation state as data becomes available.

### Samples and documentation

The completed work must add:

- a compiled Q3 sample for collection, rendering, retry, refresh, keys, revisions, and load-state UI;
- a deterministic Demo route backed by an in-process fake `PagingSource` with stable automation
  roles and controllable initial, append, empty, and error states;
- one `RemoteMediator` architecture example or tested fixture that does not make Room or a network
  client a production dependency unless separately approved;
- an owning-module manual covering dependency setup, lifecycle, keys, placeholders, load states,
  cancellation, testing, and migration from a manually accumulated immutable list; and
- active English public documentation with reviewed Simplified Chinese mirrors where required.

## Non-goals

This plan does not:

- reimplement `Pager`, `PagingSource`, `PagingData`, `RemoteMediator`, generation invalidation,
  caching, request scheduling, page eviction, retry, or refresh machinery;
- make Paging 3 mandatory for finite, in-memory, already-loaded, or modest lists;
- add AndroidX Paging types to an SDK-neutral ViewCompose layer;
- adopt `paging-compose`, host a Compose lazy list, or let a Paging RecyclerView adapter own the
  renderer's native list;
- select an application's page size, prefetch distance, initial load size, placeholder policy,
  jump threshold, network protocol, database schema, cache lifetime, query debounce, or error copy;
- persist loaded pages through ViewCompose saveable state;
- guarantee stable scroll position when an application supplies unstable or positional keys for
  loaded items; or
- publish a generic infinite-scroll component whose callback and boolean flags reproduce only a
  subset of Paging behavior.

## Current baseline

Verified from the worktree on 2026-08-18:

1. The repository has no AndroidX Paging dependency, integration module, source adapter, Demo
   scenario, module manual, or compiled Paging sample.
2. `LazyColumn` accepts a finite `List<T>`, a copied finite `LazyItemsSnapshot<T>`, or synchronous
   scoped item declarations. The current node receives one complete ordered item table.
3. Lazy items already have explicit logical keys, content types, content revisions, item sessions,
   saveable-state isolation, renderer reuse policy, and transactional commit behavior.
4. `LazyListState` publishes visible indices and total item count and owns scroll commands and
   save/restore of the first visible index and offset.
5. Renderer prefetch prepares ViewCompose sessions/native presentation; it is not a remote or
   database data-loading scheduler.
6. The unified roadmap previously listed Paging 3 as an optional Collections follow-up, and the
   Lazy Collections guide classified it as a separate integration concern. Both now delegate
   execution ownership to this plan.

## Locked architectural rules

1. AndroidX Paging owns data loading and paging correctness; ViewCompose owns declarative
   presentation and lazy-item identity.
2. The integration is optional and independently removable. Its absence contributes no dependency,
   code path, startup initializer, observer, or recurring work.
3. Only official public AndroidX Paging APIs may be used. No reflection or dependency on internal
   presenter state is allowed.
4. Android Renderer retains the only RecyclerView adapter, diff, bind, recycling, and transaction
   owner.
5. Each accepted presenter event becomes one coherent ViewCompose observation revision.
6. Loaded application items use application-stable keys. Placeholder identity is positional,
   transient, and isolated from loaded-item remember/saveable state.
7. `get(index)`-style access that drives Paging prefetch is allowed only from documented active
   presentation work; diagnostics and diff inspection use a non-triggering path.
8. `refresh`, `retry`, source/mediator load states, cancellation, and end-of-pagination semantics
   retain AndroidX Paging meaning instead of being redefined by ViewCompose.
9. Any neutral lazy-contract extension must be independently useful, Paging-free, transaction-safe,
   and documented in its owning public module.
10. No public API ships without Q-level classification, applicable contract fields,
    canonical-English KDoc, compiled Q3 samples, owning-module documentation, reviewed Chinese
    mirrors, binary/API validation, and an immutable release Changeset.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0. Dependency and contract freeze | Not started | Pin a reviewed AndroidX Paging version; characterize `PagingDataPresenter`; freeze artifact/package names, lifecycle policy, placeholder support, container scope, public API, testing dependencies, and release baseline | Written spike proves official APIs can feed the current lazy contract or documents the smallest Paging-neutral prerequisite with its Q level and impact |
| 1. Presenter characterization harness | Not started | Deterministic presenter fixtures for generations, insert/drop events, placeholders, access hints, load states, retry, refresh, invalidation, and cancellation | Event ordering and state-coherence assertions pass without Android Renderer or network dependencies |
| 2. Non-placeholder LazyColumn slice | Not started | Optional module, observable paging-items state, stable-key bridge, latest-generation collection, retry/refresh, and core Q3 sample | Initial load, append/prepend, error/retry, invalidation, query replacement, navigation disposal, and key-state tests pass |
| 3. Placeholder and page-drop slice | Not started | Positional placeholder contract, unloaded-slot rendering, jump/page-drop handling, and any approved generic indexed lazy prerequisite | Placeholder-to-item transitions cannot steal state; dropped pages release sessions and memory; bounded-work evidence passes |
| 4. Load-state composition | Not started | Refresh/append/prepend and source/mediator state helpers with empty, header, footer, and error composition examples | State matrix preserves loaded content, distinguishes retry from refresh, and exposes mediator detail without visual policy |
| 5. Lifecycle and mediated data | Not started | Visible/retained lifecycle behavior, upstream `cachedIn` ownership guidance, recreation coverage, and deterministic `RemoteMediator` fixture | Hidden/revealed navigation, cancellation, process recreation, source plus mediator failures, and database/network boundary tests pass |
| 6. Samples, Demo, and documentation | Not started | Directly launchable fake-source Demo, compiled Q3 samples, module catalog/manual, setup/architecture/testing/migration docs, Chinese mirrors, and dependency notices | Documentation/localization, sample compilation, automation-role, dependency verification, and consumer-build gates pass |
| 7. Performance, device, and release closeout | Not started | Same-build timing/allocation/memory evidence for append, drop, large generations, rapid query replacement, and scroll; final Changesets and Maven consumer verification | No accepted correctness, leak, frame-time, or memory regression; evidence is interpreted in active docs before archival |

## Acceptance matrix

| Scenario | Required evidence |
| --- | --- |
| Initial refresh | Loading, first data, empty, and initial error states are distinct and deterministic |
| Append and prepend | Existing content remains mounted where keys are stable; direction-specific state and retry are correct |
| Retry and refresh | Retry preserves the generation and targets failed loads; refresh creates the AndroidX-owned replacement generation |
| Query replacement | Latest generation wins; old collectors, access hints, errors, and load states cannot publish afterward |
| Invalidation | New generation replaces old data atomically without mixing item and load-state revisions |
| Placeholders | Unloaded slots render deterministically; loaded items receive their own stable identity and never inherit placeholder state |
| Page drops and jumps | Removed pages release sessions and renderer metadata; supported jumps retain bounded work and correct indices |
| Stable-key state | Insert, move, refresh, and mediator updates preserve remember/saveable state only for the same logical item |
| Source and mediator | `CombinedLoadStates` retains both origins and convenience UI does not erase diagnostic detail |
| Navigation and lifecycle | Hidden retained destinations follow the selected policy; re-entry and recreation resume without duplicate collection |
| Failure and cancellation | Exceptions remain structured, cancellation is not reported as load failure, and permanent release leaks no collector |
| Performance and memory | Large generations, rapid appends, page drops, and flings remain within recorded same-build budgets |

## Verification commands

Exact new-module task names are frozen in Phase 0. The completed plan must include at least:

```bash
./gradlew :viewcompose-paging-androidx:test
./gradlew :viewcompose-paging-androidx:apiCheck
./gradlew verifyModuleArchitecture
./gradlew verifyDocumentationStructure
./gradlew qaQuick
./gradlew qaPreview
./gradlew qaFull
```

Device-only or credential-dependent cases must be isolated and reported as explicit prerequisites;
they cannot turn a missing environment into a false pass. Accepted performance or benchmark output
must be interpreted in the owning active documentation with comparison context, absolute results,
normalized change, conclusion, limitations, and next action.

## API and documentation impact

The provisional collection function, paging-items owner, and Paging-aware container/scope are Q3
because they establish lifecycle, observation, identity, cancellation, dependency, and failure
contracts. Immutable public policy or state values are at least Q2. Before source is added, Phase 0
must inventory every applicable contract field from the API documentation standard and record any
inapplicable field with a reason.

Production work must update the module catalog, owning module manual, API reference, Lazy
Collections guide, unified roadmap, compiled sample catalog, Demo documentation, dependency
verification metadata, consumer rules if required, and Simplified Chinese mirrors of active public
pages. The first publication-relevant source or publication-input change must add immutable
`release/changes/<unique>.json` entries for every directly affected artifact; this section must then
replace `- None.` with the exact filenames.

## Completion criteria

This plan is complete only when:

1. the optional artifact and final public API satisfy every locked boundary and Q-level contract;
2. official AndroidX Paging behavior, not a ViewCompose paging clone, owns the data pipeline;
3. the acceptance matrix passes for the supported no-placeholder and placeholder configurations;
4. lifecycle, navigation, recreation, source/mediator, cancellation, stable-key, leak, performance,
   and memory evidence has durable interpretation in active documentation;
5. compiled samples, Demo routes, public English pages, reviewed Chinese mirrors, module manual,
   dependency metadata, release Changesets, and Maven consumer verification are complete;
6. the unified roadmap and Lazy Collections guide describe the shipped behavior rather than an
   active future plan; and
7. this document moves to `docs/archive/` with final evidence and no active document continues to
   present the work as pending.

## Evidence ledger

| Date | Evidence | Result | Interpretation / next action |
| --- | --- | --- | --- |
| 2026-08-18 | Worktree and active-document review | Planning baseline established | No Paging dependency or implementation exists. The roadmap candidate and Lazy Collections boundary were transferred to this plan; Phase 0 must verify the official presenter and freeze the public contract. |

## Decision history

1. 2026-08-18 — Use AndroidX Paging as the paging engine; declarative collection primitives are
   presentation infrastructure, not a substitute for paging generations and load coordination.
2. 2026-08-18 — Keep Paging optional and outside all SDK-neutral core contracts.
3. 2026-08-18 — Prefer the official custom-UI presenter path and forbid a second RecyclerView
   adapter/diff owner.
4. 2026-08-18 — Treat loaded-item keys and placeholder identity as different state domains.
5. 2026-08-18 — Transfer execution ownership from the roadmap candidate and Lazy Collections
   non-goal note into this dedicated active plan while leaving historical archives unchanged.
