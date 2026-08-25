# Paging 3 Integration Plan

## Status

Complete. Phases 0 through 7 completed on 2026-08-25. The optional official-presenter frontend,
compact placeholder table, lifecycle and mediator contracts, controlled Demo, large-generation
device proof, Release fixed-clock baseline, Maven consumers, and repository gates are closed. This
archived plan retains execution evidence only; current contracts live in the module manual, Lazy
Collections guide, performance tooling guide, and roadmap.

Phase 0 pinned AndroidX Paging 3.5.1, compiled its public `PagingDataPresenter` contract with Kotlin
2.0.21, froze the API and lifecycle policies below, and rejected the current fully materialized
`List<LazyListItem>` path for placeholders and page drops. This plan is canonical English-only;
durable shipped contracts move to their owning active public documentation before archival.

Last verified: 2026-08-25.

Next action: none. Row/grid integration, real database or network fixtures, and comparative
performance claims require a new attributed plan rather than reopening this archive.

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
- `release/changes/20260825-paging-performance-closeout.json` — records the repository-only Paging
  Release workload, result-tool gate, device evidence, and plan closeout as release-neutral.

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
| [Unified roadmap](../project/roadmap.md), Collections next focus | Keeps a summary and delegates execution here. |
| [Lazy Collections guide](../guides/lazy-collections.md), deliberate non-goals | Retains the core/integration boundary and delegates Paging delivery here. |
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

## Shipped contract references

Phases 0–6 shipped the collector, items, container, load-state, lifecycle, placeholder, and compact
lazy-table contracts. The [Paging module manual](../modules/viewcompose-paging-androidx/README.md)
now owns their usage, lifecycle, identity, placeholder, load-state, testing, migration, and
dependency guidance; the
[`viewcompose-paging-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/)
owns the exact current signatures. The [Lazy Collections guide](../guides/lazy-collections.md)
owns the Paging-neutral `LazyItemTable` and renderer transaction contract.

AndroidX remains the sole paging engine. Android Renderer remains the sole adapter, stable-ID,
holder, diff, transaction, and item-Session owner. Phase 7 measured these shipped contracts and
closed their release evidence without reopening them. Any future contract change requires a new
attributed plan, applicable Q documentation, compiled samples, and release Changesets.

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
flow over deterministic fake storage. Phase 6 adds a directly launchable controlled `PagingSource`
Demo that exposes initial loading, data, append, empty, error, retry, and generation reset states
without network or database timing. Phase 7 adds the Release-only `performance.paging@1` route,
five-iteration append/drop, query-replacement, and scroll workloads, compact and million-position
Pixel proofs, and a fixed-clock Xiaomi absolute baseline. It intentionally makes no comparative
performance claim because no compatible prior or alternate-engine baseline exists.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0. Dependency and contract freeze | Complete | Paging 3.5.1 presenter path, artifact/package, lifecycle, overloads, LazyColumn-only scope, tests, and compact indexed-table prerequisite | Official API review and Kotlin 2.0.21 compile probes pass; full-table path is rejected with the Q3 replacement and breaking impact documented |
| 1. Presenter characterization harness | Complete | Non-published JVM module with deterministic generations, insert/drop, placeholder, hint, load-state, retry, refresh, invalidation, and cancellation fixtures | Six tests pass without Android Renderer, Android runtime, device, or network; explicit main-context injection and coherent publication ordering are frozen |
| 2. Non-placeholder LazyColumn slice | Complete | Optional module, observable items, stable-key bridge, latest generation, retry/refresh, and core Q3 sample | 12 deterministic tests, strict Q3 audit, local Maven consumers, complete API reconstruction, and the bilingual production site pass |
| 3. Placeholder and page-drop slice | Complete | Neutral indexed hard cut, positional placeholders, jump/drop handling | Implementation, deterministic contract tests, release intent, local publication, consumers, dropped-session/memory evidence, Pixel bounded-work verification, and post-ownership-hard-cut full gates pass |
| 4. Load-state composition | Complete | Pure primary-content and per-`LoadType` source/mediator projections plus empty/header/footer/error examples; no framework-owned layout | Twenty distinct tests pass in both variants; Q3 audit, bilingual docs, release intent, local publication, and isolated consumer pass |
| 5. Lifecycle and mediated data | Complete | Frozen policies, `cachedIn` guidance, recreation, deterministic mediator fixture, source-failure hard cut | Hidden/revealed navigation, cancellation, recreation, and source/mediator failure tests pass |
| 6. Samples, Demo, and documentation | Complete | Controlled `PagingSource` Demo, Q3 samples, catalog/manual, setup/architecture/testing/migration docs, mirrors, and notices | Local compile/unit/localization/automation gates and the full controlled-state Pixel device path pass; dependency and sample contracts remain covered by the Phase 2/5 published-module gates |
| 7. Performance, device, and release closeout | Complete | Same-build append/drop/large-generation/query/scroll evidence; final Changesets and Maven proof | Release fixed-clock evidence, Pixel functional/manual evidence, local Maven/consumer proof, documentation/site gates, and complete repository/device gates pass with limitations interpreted before archival |

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
| 2026-08-18 to 2026-08-25 | Phases 0–5 consolidated baseline | Official presenter characterization, the published frontend, compact placeholders/page drops, load-state projections, every lifecycle policy, `cachedIn` recreation, structured cancellation, and real `Pager + RemoteMediator` fake-storage coordination passed their API, unit, device, Maven, consumer, and documentation gates. The Pixel Phase 3 probe added 48,124 KiB PSS for 1,000,000 positions, jumped to 999,999 in 555 ms, retained 81 loaded items, and released dropped Sessions. Phase 5 ended at 49,161,510 non-API documentation bytes. Conclusion: **improved** correctness, ownership, lifecycle, and compact-memory confidence with **mixed** documentation growth. Limitations remained one local-data device/API and no real database/network, Demo, frame, or release-performance result. Git history preserves the superseded per-phase execution ledger. |
| 2026-08-25 | Phase 6 controlled Demo and documentation acceptance | The controlled-source unit suite, registry tests, app/debug/android-test compilation, localization, and automation-selector gates passed. One Pixel 4 XL Android 13/API 33 instrumentation test passed in a 13 s Gradle run and deterministically traversed initial loading, ten loaded items, append loading/error, same-generation retry to twenty items, generation reset, empty, and initial error. Manual inspection of the same Pixel confirmed that initial, content, and retained-content append-error states are fully visible, readable, and scrollable. Bounding the Demo viewport and disabling renderer cache prefetch prevented an off-screen Session from requesting append before the explicit action, while the real Paging configuration retained its valid `prefetchDistance = 1`. Documentation/API/dependency/release-intent gates passed for 118 English pages and 115 current mirrors; `qaQuick + qaPreview` passed 2,338 tasks in 1 min 21 s, including local publication, consumer/sample compilation, unit tests, Paparazzi, and Release APK verification. Conclusion: **improved** sample, automation, documentation, and manual-verification confidence. Limitations: one device/API, in-process fake source, no real database/network, prepend UI, frame, memory, or release-performance result. Next: Phase 7. |
| 2026-08-25 | Phase 7 performance, device, and release closeout | A rooted Xiaomi MI 6 ran the R8 Release APK for five fixed-clock iterations per method. Append/drop recorded frame P50/P90/P95/P99 `4.281/29.189/33.973/43.592 ms`, median peak heap `117,797 KiB`, and run-P50 CV `0.077`; query replacement recorded `4.215/13.810/40.809/48.345 ms`, `128,433 KiB`, and CV `0.021`; scroll recorded `2.581/3.699/4.066/6.511 ms`, `119,087 KiB`, and CV `0.006`. RSS was unavailable. Pixel 4 XL/API 33 functional proof ended the compact path at 96 loaded and 189 released Sessions; the million-position path added 46,977 KiB PSS, jumped to the end in 549 ms, and ended with 81 loaded and 58 released Sessions. Manual inspection accepted query, append/drop, jump, reset, and bounded loaded-window feedback. Local Maven publication, metadata, six isolated consumers, complete API reconstruction, and `qaRelease` passed 1,486 tasks in 13 min 59 s. The bilingual site generated 440 pages within all output budgets; `qaQuick + qaPreview` passed 2,339 tasks in 31 s. With Pixel temporarily locked to portrait and its original auto-rotation setting restored afterward, `qaFull` passed 152 App tests (150 pass, two documented prerequisite skips), one Counter test, and two Tutorials tests in 15 min 9 s. Conclusion: **improved** release, functional, bounded-memory, and automation confidence; normalized performance direction is **inconclusive** because this is the first absolute baseline. Limitations: synthetic in-process data, one API level per device role, no real database/network I/O, prepend workload, alternate engine, comparable prior baseline, or benchmark RSS. Future expansion requires a new plan. |

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
9. 2026-08-25 — Keep the Demo on a real `Pager + PagingSource`, but suspend each load behind an
   explicit result control and bound its viewport so people and automation can inspect transient
   states without network/database timing or accidental off-screen append access.
10. 2026-08-25 — Make `performance.paging@1` a ViewCompose-only Release workload, accept its first
    fixed-clock run as an absolute baseline rather than a comparative claim, and require any row,
    grid, real-I/O, or alternate-engine expansion to receive a new plan and workload revision.
