---
draft: true
schema_version: 2
document_id: plan.viewmodel-androidx-optimal-architecture
doc_type: plan
owner:
  kind: module
  id: viewcompose-viewmodel-androidx
version_lane: version-agnostic
capability_ids:
  - viewmodel.owner-boundaries
  - viewmodel.saved-state
  - viewmodel.scoped-owners
  - viewmodel.store-resolution
artifact_ids:
  - viewcompose-android
  - viewcompose-host-android
  - viewcompose-navigation-android
  - viewcompose-viewmodel-androidx
sample_ids: []
status: active
scope: Hard-cut the AndroidX ViewModel integration toward one authoritative ownership model, close material Android Compose capability gaps, and replace shallow test evidence with lifecycle and restoration contracts.
non_goals:
  - Preserve source or binary compatibility with defective Alpha APIs or implementation details.
  - Add Kotlin Multiplatform, Hilt-specific, Navigation 2, or Navigation 3 compatibility layers.
  - Copy Compose symbol names when a ViewCompose-native ownership contract is clearer.
baseline: The 2026-08-28 audit found sound module boundaries and passing mainline tests, but no arbitrary-subtree ViewModel scope, a redundant composition cache, non-Compose blank-key semantics, a leaked SavedStateHandle holder, and material lifecycle and restoration test gaps.
ordered_work:
  - Freeze capability identities, Q levels, target invariants, API removals, and the cross-module architecture decision.
  - Upgrade the AndroidX baseline and hard-cut ViewModel lookup and creation semantics.
  - Add a general retained and reference-counted scoped-owner facility for arbitrary UI subtrees.
  - Move navigation store retention onto the general facility and add host-owned ViewTree discovery where appropriate.
  - Replace the standalone SavedStateHandle holder design with constructor and CreationExtras ownership, then close saveable-state interoperability deliberately.
  - Complete unit, lifecycle, restoration, navigation, host, API, documentation, and deletion-guard coverage.
  - Remove obsolete paths, update durable documentation, run full acceptance, and archive the plan before release.
completion:
  - Every accepted target invariant and Compose capability disposition has executable evidence and no unresolved partial state.
  - The ViewModelStore is the only ViewModel instance cache, and arbitrary subtree scopes survive recreation and clear exactly at permanent removal.
  - Deprecated holder, standalone-handle, duplicate navigation-store, blank-key-default, and compatibility paths are absent.
  - All affected capability, API, sample, module, architecture, migration, release-intent, and documentation gates pass.
last_verified: 2026-08-29
next_action: Implement Phase 5's coverage ledger, negative guards, and real-device process-restoration journey.
maven_release_changesets:
  - release/changes/20260829-lifecycle-toolchain-prerequisite.json
  - release/changes/20260829-navigation-shared-viewmodel-scopes.json
  - release/changes/20260829-viewmodel-saved-state-hard-cut.json
  - release/changes/20260829-viewmodel-scoped-owners.json
  - release/changes/20260829-viewmodel-store-resolution.json
---

# AndroidX ViewModel Optimal Architecture and Compose Capability Parity Plan

## Status

Active. The audit baseline, Phase 0 contract freeze, Phase 1 store-resolution hard cut, Phase 2
general retained scoped-owner provider, Phase 3 navigation/host convergence, and Phase 4
SavedStateHandle ownership hard cut are complete.

Last verified: 2026-08-29.

Next action: implement Phase 5's coverage ledger, negative guards, and real-device process-
restoration journey.

## Maven release changesets

- `release/changes/20260829-lifecycle-toolchain-prerequisite.json`
- `release/changes/20260829-navigation-shared-viewmodel-scopes.json`
- `release/changes/20260829-viewmodel-saved-state-hard-cut.json`
- `release/changes/20260829-viewmodel-scoped-owners.json`
- `release/changes/20260829-viewmodel-store-resolution.json`

## Release intent rationale

The initial plan-only change did not alter production source, publication inputs, or a compiled API
sample. The toolchain prerequisite is tracked by
`release/changes/20260829-lifecycle-toolchain-prerequisite.json`; it classifies the directly
affected host and Preview artifacts, including the isolated Preview worker's JDK 21 runtime break.
Later implementation pull requests must add their own immutable Changeset and must not amend this
record. The release planner, not this plan, derives reverse-dependency propagation.

The Phase 2 provider API, composition adapters, and compiled sample are classified by
`release/changes/20260829-viewmodel-scoped-owners.json` as one additive feature for
`viewcompose-viewmodel-androidx`.

Phase 3 is classified by
`release/changes/20260829-navigation-shared-viewmodel-scopes.json`: Navigation Android receives a
breaking owner-boundary and shared-store migration, while the Android aggregate receives
ViewTree-owner discovery hardening without changing its public signatures.

## Objective

Make `viewcompose-viewmodel-androidx` the single, minimal AndroidX ViewModel ownership integration
for ViewCompose. The finished design must be optimal for ViewCompose's native-View and delayed-
session runtime, while providing the material Android capabilities available from Compose and
Lifecycle 2.11:

1. owner-bound ViewModel lookup with AndroidX-compatible key, Factory, and `CreationExtras`
   semantics;
2. arbitrary UI-subtree ViewModel scopes that survive configuration recreation and clear only
   after permanent removal;
3. destination, graph, retained-stack, Pager, lazy-item, tab, overlay, and custom-container use of
   one store-retention mechanism rather than parallel owner implementations;
4. constructor-based `SavedStateHandle` creation and process restoration without a public holder
   implementation type;
5. host-level ViewTree owner discovery where the host owns an Android View, while low-level
   `renderInto` remains explicit and platform-boundary neutral; and
6. complete executable evidence for reuse, replacement, cleanup, restoration, delayed rendering,
   and failure behavior.

Capability parity means equivalent ownership and observable behavior, not a requirement to clone
every Compose signature. Kotlin Multiplatform and DI-framework-specific integration remain separate
product decisions.

## Baseline and audit interpretation

### Accepted strengths

- The integration is correctly separated from the runtime, renderer, host engine, and navigation
  modules. It does not own Android View rendering or create host lifecycle owners.
- Activity and Fragment hosts install the intended owners. Fragment content uses the View lifecycle
  for rendering while retaining Fragment-scoped ViewModel and saved-state ownership.
- Explicit and local owners, explicit/default Factory precedence, copied default
  `CreationExtras`, destination owners, graph owners, repeated-route isolation, retained stacks,
  and permanent-pop cleanup are implemented.
- Navigation tests prove parent Factory and `CreationExtras` inheritance, destination and graph
  restoration, and independent store lifetime across retained stacks.

### Defects and capability gaps

| Severity | Finding | Required disposition |
| --- | --- | --- |
| High | The repository executes against Lifecycle 2.8.7 and exposes no general provider for arbitrary UI-subtree ViewModel scopes. Navigation owns a specialized store-retention path instead. | Upgrade the executable baseline to Lifecycle 2.11, establish one general scoped-owner facility, and migrate navigation store retention to it. |
| Medium | `viewModel()` remembers the resolved instance in composition even though `ViewModelStore` is already the authoritative cache. A cleared store can therefore leave a stale remembered instance if the composition remains active. | Remove the instance-level composition cache; every lookup goes through `ViewModelProvider`. |
| Medium | A null or blank key selects the default identity, while AndroidX/Compose treats only null as the default. | Hard-cut blank-key behavior to AndroidX-compatible explicit-key semantics and add compatibility tests for null, empty, blank, and ordinary keys. |
| Medium | The standalone `savedStateHandle()` path leaks `SavedStateHandleHolderViewModel`, reserves a string key, and duplicates the preferred ViewModel constructor/factory ownership model. | Remove the helper and holder without an alias; add initializer-based ViewModel creation and use ViewCompose `rememberSaveable` for UI-only state. |
| Medium | Module-local tests do not prove explicit/default extras, owner replacement, store clearing, `onCleared`, process recreation, default arguments, or SavedStateHandle restoration. | Add contract tests before each corresponding implementation slice lands. |
| Low | Custom low-level hosts do not discover a ViewTree owner automatically. | Add discovery only at a host boundary that owns an Android View; keep `renderInto` explicit. |
| Low | Active migration pages previously contradicted navigation Factory/extras tests. | Keep current migration evidence synchronized in every implementation phase. |

The 2026-08-28 executable baseline passed 10 `viewcompose-viewmodel-androidx` unit tests and 148
`viewcompose-navigation-android` unit tests with zero failures. Conclusion: **mixed**. Existing
Activity, Fragment, and navigation main paths are credible, but the count does not close the
arbitrary-scope, store-clear, process-restoration, or holder-design findings because those cases are
absent or only indirectly exercised. The next evidence must target those missing contracts rather
than add more happy-path repetitions.

### Phase 1 toolchain prerequisite acceptance

The comparison baseline was the JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.0.21, compile-SDK-36
lane. It could execute the prior repository but could not represent the intended API 37 and
Lifecycle 2.11 implementation lane. The accepted prerequisite uses JDK 21, Gradle 9.3.1, AGP
9.1.1, Kotlin 2.2.10, compile SDK 37 at the application and Preview boundaries, and Paparazzi
2.0.0-alpha05 while retaining Java 11 bytecode for published runtime libraries.

The final 2026-08-29 `qaQuick qaPreview` run passed all 2,270 actionable tasks: 181 executed and
2,089 were up to date. The focused result set passed 672/672 tests with zero failures or errors:
52 host, 536 renderer, 3 Material 3 overlay, 4 One UI overlay, 23 Preview Gradle plugin, 32 Preview,
12 Preview runner, and 10 Preview worker-host tests. The normalized gate completion and focused
test pass rates were both 100%. No equivalent API-37 run existed before the migration, so a
before/after duration or failure-rate delta would be misleading.

Conclusion: **improved** build and test compatibility. The migration exposed and closed four
previously hidden assumptions: Robolectric selecting an unsupported target SDK, Android 15's
enforced transparent navigation bar, AndroidX's deliberate empty accessibility delegate on compat
delegate removal, and versioned `android-37.0` platform paths. Limitations: Robolectric remains on
SDK 35 by default and therefore does not prove API 37 runtime behavior; exact legacy navigation-bar
color tests deliberately run on API 34 while API 35 tests prove the enforced edge-to-edge policy;
the isolated Preview worker now requires JDK 21; the AGP built-in-Kotlin migration remains deferred
behind the documented opt-out; and this acceptance makes no device, runtime-performance, or
ViewModel semantic claim. Those dimensions remain **inconclusive**. Next action: upgrade Lifecycle
to 2.11 and land the Phase 1 resolver and creation contracts on this verified lane.

### Phase 1 store-resolution acceptance

The comparison baseline was the seven-test `ViewModelCompositionTest` contract on Lifecycle 2.8.7.
It exercised owner, key, and Factory selection but did not directly prove explicit/default
`CreationExtras`, empty and whitespace key identity, owner replacement, store clearing, reified and
`KClass` initializer parity, initializer reuse, or initializer failure propagation. The accepted
Lifecycle 2.11 implementation routes every public overload through one resolver and leaves
`ViewModelStore` as the only ViewModel instance cache.

The 2026-08-29 focused run passed 21/21 `ViewModelCompositionTest` cases and 24/24 tests for the
owning module, with zero skips, failures, or errors. Resolver-contract coverage increased from 7 to
21 tests: 14 additional cases, a 3.0x total, or a normalized increase of 200%. The same revision's
`qaQuick qaPreview` acceptance passed all 2,270 actionable tasks: 337 executed and 1,933 were up to
date.

Conclusion: **improved**. Lookup now observes store clearing on the next composition, only null uses
the default identity, every non-null key remains explicit, explicit/default Factory and extras
precedence is executable, owner replacement is observable, and both initializer forms share the
same reuse and failure behavior. Limitations: this phase does not add arbitrary subtree owners,
navigation migration, process-restoration evidence, holder removal, device evidence, or runtime
performance measurements, so those dimensions remain **inconclusive**. Next action: implement the
Phase 2 retained scoped-owner provider and its reference, removal, rollback, isolation, recreation,
and delayed-session contracts.

### Phase 2 retained scoped-owner acceptance

The comparison baseline was the 24-test owning module after Phase 1, with no general child-scope
provider and navigation still carrying the only retained child-store implementation. The accepted
implementation delegates child-store allocation and reference counting to Lifecycle 2.11
`ViewModelStoreProvider`, adds one ViewCompose provider facade plus lease and composition adapters,
and stores commit/terminal metadata in AndroidX-owned stores rather than a parallel store map.

The final 2026-08-29 focused run passed 20/20 scoped-owner contracts and all 44/44 tests in
`viewcompose-viewmodel-androidx`, with zero skips, failures, or errors. The owning-module total grew
from 24 to 44 tests: 20 additional cases, a 1.833x total, or a normalized increase of 83.3%. The
contracts cover provider and child isolation, facade recreation, Factory/extras/default-argument
inheritance, multiple and idempotently closed leases, temporary absence, terminal clear, deferred
clear, no resurrection, parent-store cleanup, commit/abort, delayed local capture,
Pager/lazy/overlay reorder, and `INITIALIZED`/`DESTROYED` lifecycle boundaries. The same revision's
`qaQuick qaPreview` acceptance passed all 2,270 actionable tasks: 215 executed and 2,055 were up to
date.

Conclusion: **improved**. Arbitrary retained UI subtrees now have one stable-key, reference-
protected ownership primitive with explicit terminal-removal semantics, configuration retention,
transactional composition rollback, and no global fallback store. The final boundary review also
closed an early-lifecycle leak: normal removal at `INITIALIZED` now clears the provider, while only
`DESTROYED` preserves it for parent-store recreation. Limitations: navigation has not yet migrated
off `NavEntryOwnerStore`; process-death `SavedStateHandle` restoration, host ViewTree fallback,
device evidence, runtime performance measurements, and the holder/helper hard cut remain
**inconclusive**. Next action: migrate navigation destination and graph stores to the shared
provider, prove its full retained-stack matrix, and add host-owned ViewTree precedence tests.

## Design rules and hard-cut policy

This artifact is Alpha. Keeping a defective contract is more expensive than a coordinated break,
so the implementation uses the following rules:

1. Maintain exactly one authoritative lifetime cache: `ViewModelStore`. Composition may remember
   scope bindings and reference tokens, but never a resolved ViewModel instance.
2. A scope identity is stable data owned by the caller or container. Call position, list position,
   object identity, and incrementing counters are invalid persistent identities.
3. Parent Factory and starting `CreationExtras` are inherited once for child creation; the child
   replaces only its own store owner, saved-state owner, and scoped default arguments.
4. Temporary render absence, exit animation, retained navigation, and delayed child composition do
   not imply permanent removal. Store cleanup occurs only when all live scope references are
   released and the owner confirms terminal removal.
5. Configuration recreation transfers retained provider state through the established AndroidX
   owner boundary. Application singletons, static maps, and process-global registries are forbidden.
6. Saved UI state belongs to ViewCompose `rememberSaveable`; business state belongs to a ViewModel
   and its constructor-created `SavedStateHandle`. A second public handle-only ViewModel model is
   forbidden.
7. Host conveniences stay in host or aggregate modules. The integration module remains free of
   concrete Android View ownership.
8. Remove obsolete public declarations, implementation classes, duplicate stores, tests, samples,
   and prose in the same hard cut. Do not add deprecated aliases, adapters, feature flags, dual
   reads/writes, or fallback implementations.
9. Every public/protected API slice resolves capability impact, Q level, contract fields,
   canonical-English KDoc/Javadoc, compiled Q3 samples, module documentation, and its immutable
   Changeset before merge.

## Phase 0 contract freeze

Phase 0 accepts
[ADR-0023](../../architecture/decisions/0023-retained-viewmodel-scope-ownership.md) and freezes the
following decisions before production work:

- `viewmodel.scoped-owners` is the stable capability ID. Its capability record lands with the first
  compiled declaration because governance capability records describe the current public inventory
  and cannot be pre-created.
- `ViewModelScopeProvider` is the one module-owned provider facade over Lifecycle 2.11
  `ViewModelStoreProvider`. `ViewModelStoreOwnerLease` is its reference-owning core adapter;
  `rememberViewModelScopeProvider` and `rememberViewModelStoreOwner` are its composition adapters;
  existing `ProvideViewModelStoreOwner` remains the local-publication adapter.
- Provider and child identities are explicit, non-null, caller-owned stable values. There is no
  position-derived retained-provider overload and no specialized Pager, lazy, overlay, tab, or
  navigation store API.
- Closing a lease means temporary reference release. `clear(key)` or `clearAll()` means terminal
  removal. A removal request waits for active leases, rejects resurrection, and permits a fresh
  scope with the same key only after old references finish.
- Candidate commit/abort is transactional: an aborted new scope is cleared, an already committed
  scope is preserved, restored state is not consumed by a failed candidate, and no token leaks.
- The initializer additions are reified and `KClass` `CreationExtras.() -> VM` overloads. The
  removals are `savedStateHandle()` and `SavedStateHandleHolderViewModel`, with no aliases.
- The state-interoperability disposition is final: `rememberSaveable` owns UI-only state;
  constructor/initializer-created `SavedStateHandle` plus `getMutableStateFlow()` owns ViewModel
  business state. No snapshot-state adapter or second writable source is introduced.

All changed or added public declarations are Q3. The applicable contract fields are `behavior`,
`inputs`, `outputs`, `state`, `lifecycle`, `concurrency`, `failure`, `android`, `performance`, and
`compatibility`; lease cleanup and initializer callbacks additionally document callback timing and
ordering. Phase implementation impact records must use those fields, canonical-English KDoc,
compiled samples, generated Reference, module owners, and breaking migration dispositions.

The frozen named test owners are `ViewModelCompositionTest` for lookup and creation,
`ViewModelScopeProviderTest` for provider/lease state, `ViewModelScopeCompositionTest` for commit,
abort, locals, and delayed sessions, `ViewModelSavedStateRestorationTest` for process-style state,
and the existing navigation and host integration suites for cross-module convergence. Test methods
must state the contract in their names; counts alone cannot close a row.

## Target architecture

### Store-only ViewModel resolution

`viewModel()` resolves the effective owner, Factory, extras, model class, and key for every build
and calls `ViewModelProvider`. Null selects AndroidX's class-derived default key; every non-null
string, including empty or whitespace-only strings, is an explicit key. Existing entries ignore a
new Factory/extras value according to AndroidX store semantics. Store clearing is immediately
observable on the next lookup.

The public creation surface includes reified and `KClass` forms plus a
`CreationExtras.() -> VM` initializer form. Overloads must delegate to one non-inline internal
resolver so precedence, diagnostics, key behavior, and tests cannot drift.

### General scoped-owner provider

Introduce one module-owned provider abstraction over Lifecycle 2.11 `ViewModelStoreProvider`. It
creates child stores by stable scope identity, returns child `ViewModelStoreOwner` bindings, retains
them across configuration recreation, and clears them after permanent removal. The binding owns a
reference token so retained destinations, delayed sessions, overlays, Pager pages, and lazy items
can remain alive without store resurrection or premature cleanup.

The final public API names are frozen only after the capability/Q3 review, but the contract must
support:

- hoisting one provider at a retained host boundary;
- providing one keyed child owner to a ViewCompose subtree;
- multiple simultaneous references to the same child scope;
- independent child scopes for equal local keys under different parent providers;
- deterministic terminal removal and provider-wide disposal;
- inherited parent Factory, `CreationExtras`, saved-state owner, and default arguments; and
- delayed-session local capture without a fallback to a newer unrelated host owner.

Navigation continues to own entry lifecycle, route arguments, saved-state registry, and transition
retention. It stops owning an independent ViewModelStore allocation policy: destination and graph
owners obtain their stores and reference tokens from the general provider. The specialized
`NavEntryOwnerStore` logic is deleted or reduced to navigation identity coordination after the
shared facility proves equivalent behavior.

### Host and ViewTree boundary

Standard Activity, Fragment, and aggregate-owned View roots may discover
`ViewTreeViewModelStoreOwner` when no explicit owner is supplied. Explicit owner provision always
wins. The low-level host engine and `renderInto` continue to install no implicit lifecycle,
ViewModel, saved-state, or disposal owner because callers of that API own those boundaries.

### Saved state and state adapters

Remove `savedStateHandle()` and `SavedStateHandleHolderViewModel`. A ViewModel that needs restored
business state obtains its handle through the owner's default saved-state Factory or through the
initializer overload and `CreationExtras.createSavedStateHandle()`.

Compose's `SavedStateHandle.saveable` symbol is not copied mechanically. Phase 0 must record one of
two capability-complete dispositions:

1. ViewCompose `rememberSaveable` owns UI state and `SavedStateHandle.getMutableStateFlow()` owns
   ViewModel business state, with compiled interop guidance proving observation and restoration; or
2. a ViewCompose-native SavedStateHandle-to-snapshot-state adapter is added only if it can guarantee
   single ownership, replay-safe updates, Saver validation, and process restoration without two
   writable sources of truth.

The first disposition is preferred unless an application-facing use case proves that a new adapter
adds behavior rather than API symmetry.

## Compose capability closure matrix

| Capability | Baseline | Completion gate |
| --- | --- | --- |
| Activity/Fragment owner propagation | Supported | Retain existing tests and add explicit ViewTree/override precedence coverage. |
| Explicit/local owner lookup | Supported | Nested, exception, delayed-session, and owner-replacement tests pass. |
| Key, Factory, and `CreationExtras` semantics | Partial | AndroidX-compatible null/non-null keys, all precedence branches, model mismatch, and existing-entry behavior pass. |
| Initializer-based creation | Missing | Reified and `KClass` initializer samples compile and SavedStateHandle creation restores. |
| Arbitrary UI-subtree scopes | Missing | Recreation, reference retention, permanent removal, sibling isolation, and provider disposal pass. |
| Navigation destination/graph/multi-stack scopes | Supported by specialized code | Same behavior passes after navigation consumes the general provider and duplicate store policy is absent. |
| Host ViewTree fallback | Missing outside standard injection | Host-owned fallback and explicit-owner precedence pass; `renderInto` remains deliberately explicit. |
| SavedStateHandle process restoration | Partially evidenced | Default arguments, mutation, recreation, namespace isolation, and terminal cleanup pass in module and host/navigation integration tests. |
| Saveable-state interoperability | API shape differs | One accepted disposition above has compiled guidance and restoration tests; no dual writable state exists. |
| Kotlin Multiplatform and Hilt | Out of scope | Remain explicit non-goals rather than hidden parity claims. |

## Test and evidence plan

Tests are implementation prerequisites, not a final cleanup phase. Each production slice starts
with a failing contract or characterization test and lands with its complete matrix.

### Module unit contracts

- Reified and `KClass` lookup; null, empty, blank, and ordinary explicit keys; same/different keys;
  same/different owners; and model-class mismatch under an existing key.
- Explicit Factory, owner default Factory, fallback Factory, explicit extras, copied owner extras,
  initializer extras, Factory failure, initializer failure, and existing-entry Factory/extras
  replacement behavior.
- Owner-local default, nested provision, restoration after normal return and exception, explicit
  owner precedence, owner replacement, missing-owner diagnostics, and delayed child local capture.
- Recomposition reuse through the store, `ViewModelStore.clear()`, one-shot `onCleared`, lookup after
  clear, scope removal, and provider disposal. Tests must fail if a composition-level instance cache
  can return a cleared model.

### Scoped-owner lifecycle contracts

- Same provider/key reuse, sibling isolation, nested-provider isolation, and stable identity under
  reorder.
- Configuration recreation with the child store retained and parent Factory/extras preserved.
- Multiple live references, temporary zero-visibility retention, delayed/exit-session references,
  final-reference release, terminal removal, and no resurrection after clear.
- Lazy item, Pager/tab, overlay, repeated navigation route, graph, and multiple-stack pressure cases.
- Rollback and failed render must not leak a new scope, consume restored state, or clear the prior
  committed scope.

### Saved-state and recreation contracts

- `SavedStateHandle` construction from default and initializer paths, default arguments, mutation,
  save, process-style owner recreation, and exact restoration.
- Separate keys and child scopes remain isolated; removed scopes do not restore stale state when the
  same logical route is created as a new identity.
- Activity configuration change, Fragment View recreation versus Fragment destruction, navigation
  pop/retain/restore, and host teardown each assert the intended store and handle lifetime.
- ViewCompose `rememberSaveable` and the accepted SavedStateHandle business-state path restore once
  without duplicate providers or two writable sources of truth.

### Structural, API, and documentation guards

- Source and compiled-API checks prove that `savedStateHandle()`,
  `SavedStateHandleHolderViewModel`, the redundant instance cache, blank-key-default wording,
  duplicate navigation store allocation, and compatibility aliases are absent.
- Every new/changed public API has canonical-English KDoc, Q3 compiled samples, capability-impact
  records, module-manual ownership, migration comparison, and one immutable release Changeset.
- Active English and Simplified Chinese migration pages agree with implementation and tests; this
  temporary plan remains repository-only and English-only.

## Execution phases

| Phase | Deliverable | Completion gate | Status |
| --- | --- | --- | --- |
| 0 | capability/Q3 contract, hard-cut list, ADR, dependency and test matrix | decision and test names reviewed before production edits | Complete |
| 1 | Lifecycle 2.11 baseline and store-only ViewModel resolver | lookup, key, Factory, extras, clear, and initializer tests pass | Complete |
| 2 | general retained scoped-owner provider | recreation, reference, removal, isolation, rollback, and delayed-session tests pass | Complete |
| 3 | navigation and host integration hard cut | navigation consumes shared stores; ViewTree precedence and all host/navigation regressions pass | Complete |
| 4 | SavedStateHandle redesign and saveable interoperability disposition | holder/helper removed; constructor/restoration and chosen interop path pass | Complete |
| 5 | coverage closure and defect-pressure matrix | every matrix row maps to executable evidence and mutation/negative guards detect regressions | Not started |
| 6 | deletion, documentation, release evidence, and archive | no obsolete path remains; full gates pass; durable conclusions move to active docs | Not started |

### Phase 0: freeze contracts before implementation

1. Resolve a stable capability ID for arbitrary scoped ViewModel owners; update the existing owner,
   store-resolution, and saved-state capability dispositions.
2. Assign Q3 and enumerate every applicable lifecycle, saved-state, threading, error, ordering,
   performance, and compatibility field required by the source-documentation standard.
3. Add an ADR for the general provider, navigation reuse, reference-token lifetime, configuration
   retention, host boundary, and rejected alternatives.
4. Freeze the exact public removals and additions. Alpha status authorizes the break, but release
   classification and migration guidance remain mandatory.
5. Name the complete failing test matrix and decide the SavedStateHandle interoperability
   disposition before introducing an adapter.

### Phase 1: hard-cut lookup and creation

1. Upgrade the executable Lifecycle baseline to 2.11 and update dependency compatibility evidence.
2. Replace overload-specific logic with one internal resolver and remove ViewModel-instance
   `remember` caching.
3. Treat only null as the default key; preserve every non-null explicit key byte-for-byte.
4. Add reified and `KClass` initializer overloads backed by `CreationExtras`.
5. Land all lookup, Factory, extras, clear, exception, owner-replacement, KDoc, Q3 sample, module
   manual, capability-impact, and Changeset evidence together.

### Phase 2: general scoped owners

1. Implement the provider and child-owner binding over Lifecycle 2.11 primitives.
2. Integrate stable identity, configuration retention, reference tokens, terminal removal, delayed
   local capture, rollback, and provider disposal.
3. Add lazy, Pager/tab, overlay, reorder, nested-provider, and sibling-isolation fixtures.
4. Reject invalid lifecycle transitions and post-disposal use with direct diagnostics; do not
   recover through global fallback stores.

### Phase 3: navigation and host convergence

1. Move destination and graph ViewModelStore allocation/retention to the shared provider while
   preserving navigation-owned lifecycle, saved state, route arguments, and transition policy.
2. Prove repeated routes, retained stacks, graph lifetime, parent Factory/extras, process restore,
   pop cleanup, exit animation, and rollback before deleting duplicate navigation store logic.
3. Add host-owned ViewTree discovery and explicit-owner precedence at the narrowest aggregate or
   host adapter that owns the Android View.
4. Preserve and test `renderInto` as an explicit no-owner low-level API.

Phase 3 completed locally on 2026-08-29. Navigation entry and graph owners now lease keyed stores
from the general `ViewModelScopeProvider`; a saved host-scope identity retains those stores across
configuration recreation, while pop, graph removal, normal host removal, and parent-store teardown
provide terminal cleanup. `NavHost` requires `LocalViewModelStoreOwner` and no longer has an
ownerless fallback. Activity aggregate roots discover their ViewTree owner, explicit Fragment
ownership wins over the shorter View-lifecycle owner, nested explicit providers retain precedence,
and `renderInto` remains unchanged.

The clean focused run passed 151/151 Navigation Android tests and 21/21 Android aggregate-host
cases with zero skips, failures, or errors. The navigation baseline was 148 tests; three added
contracts cover missing-owner failure, configuration-retained ViewModel identity, and version-4
state migration. Aggregate source coverage increased from 10 to 11 test methods and adds Activity
ViewTree/nested-override evidence while strengthening the existing Fragment recreation test.
Conclusion: **improved** for instance retention, terminal cleanup, and owner selection. This is
JVM/Robolectric evidence rather than device process-kill, memory, leak, or frame-time evidence, so
those dimensions remain **inconclusive**. Phase 4 followed this acceptance.

### Phase 4: SavedStateHandle acceptance

The comparison baseline was the 44-test owning module after Phase 2, with the standalone helper,
public holder ViewModel, and reserved key still forming a second ownership model. Production and
sample callers now put restored business values in their actual ViewModel through the owner's
default Factory or a `CreationExtras.createSavedStateHandle()` initializer. UI-only values remain
in `rememberSaveable`; no snapshot adapter, compatibility holder, alias, or dual writer was added.

The clean focused run passed both new `SavedStateViewModelIntegrationTest` contracts and all 45/45
owning-module tests with zero skips, failures, or errors. The module total grew by one because the
two new contracts for default-argument constructor injection and process-style initializer/flow
restoration replace one deleted helper-only guard. The same run passed 151/151 Navigation Android
tests and 12/12 Preview runner tests, and compiled the migrated Demo. Source inspection finds no
holder implementation, helper declaration, or reserved key outside the intentional migration,
plan, and immutable governance records.

The repository-wide acceptance command, `./gradlew qaQuick qaPreview
-PviewComposeReleaseBaseRevision=8c79f2b4`, then completed successfully with 2270 actionable tasks:
237 executed and 2033 up-to-date. This is positive integration-gate evidence for the hard cut; it
does not replace the clean focused absolute results above or establish a performance change because
most aggregate tasks reused verified outputs.

Conclusion: **improved**. Restored business state now has one ViewModel-owned writable path and one
AndroidX restoration path, while UI-only state remains independently owned by `rememberSaveable`.
The evidence is JVM/Robolectric plus compilation and does not prove a real device process kill,
memory retention, or runtime performance, so those dimensions remain **inconclusive**. Next action:
Phase 5 maps every remaining contract to evidence, adds negative/deletion guards, and runs the
connected-device process-restoration journey.

### Phase 4: SavedStateHandle redesign

1. Move every sample and production caller to ViewModel constructor/default Factory or initializer
   ownership.
2. Add default-argument and process-style restoration tests before removing the standalone helper
   and holder class.
3. Implement the accepted saveable-state interoperability disposition and prove it has one writable
   owner and one restoration path.
4. Delete the reserved holder key, public holder, helper API, tests, samples, and documentation in
   one change; add no alias or deprecated bridge.

### Phase 5: coverage closure

1. Build a requirement-to-test ledger covering every row in this plan and every applicable Q3
   contract field.
2. Add negative and mutation-style checks for premature clear, missed clear, stale instance return,
   wrong owner fallback, lost extras, duplicated state provider, restoration replay, and removed API
   resurrection.
3. Run the focused module, host, aggregate, and navigation suites from a clean test output and
   interpret results in the owning active documentation.
4. Do not close a row with test count alone; record comparison context, absolute result, conclusion,
   limitations, and next action.

### Phase 6: hard-cut cleanup and acceptance

1. Search structurally for removed symbols, old keys, compatibility aliases, duplicate provider
   stores, stale samples, and contradictory prose; delete every dead path.
2. Update the ADR, lifecycle/saved-state architecture, module manuals, migration matrices, roadmap,
   KDoc, compiled samples, and capability records to the final behavior in both required locales.
3. Run release-intent planning, targeted suites, documentation verification, and complete
   `qaQuick`; run device or process-recreation evidence where JVM simulation cannot prove the
   platform contract.
4. Move all durable conclusions and accepted evidence into active owners, mark the plan complete,
   move it to `docs/archive/`, update both indexes, and only then allow the related Maven Central
   upload.

## Verification commands

Minimum local acceptance commands are:

```text
./gradlew :viewcompose-viewmodel-androidx:testDebugUnitTest
./gradlew :viewcompose-host-android:testDebugUnitTest
./gradlew :viewcompose-android:testDebugUnitTest
./gradlew :viewcompose-navigation-android:testDebugUnitTest
./gradlew verifyDocumentationStructure verifyViewComposeReleaseIntent
./gradlew qaQuick
```

Run focused suites after every phase and `qaQuick` only after the affected slice is stable. Any
required device or process-recreation target must be named in Phase 0 and recorded with its exact
API level, host, result, conclusion, limitation, and next action.

## Completion and archival criteria

The plan is complete only when:

1. all six phases are complete and no matrix row remains partial without an explicit, accepted
   out-of-scope rationale;
2. arbitrary child scopes, lookup, navigation, host fallback, SavedStateHandle, and process
   recreation meet their executable completion gates;
3. the ViewModelStore is the only instance cache and the general provider is the only child-store
   retention policy;
4. every rejected API and compatibility path is absent from source, compiled API, samples, tests,
   and active documentation;
5. public API, capability/Q3, KDoc, sample, module, migration, Changeset, release-intent,
   documentation, and `qaQuick` gates pass; and
6. accepted evidence and final trade-offs live in durable active documents before this plan moves
   to the archive.
