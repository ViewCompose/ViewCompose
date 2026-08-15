# Runtime Data Propagation and Android View Patch Optimization Plan

## Status

Completed on 2026-08-15 after an implementation, necessity, and benchmark-gate re-audit. Phases 0
through 3 are implemented: nullable Local lookup is correct, framework-owned logical tuples publish
atomically, one global Snapshot Apply invalidates each affected Observation at most once,
modifier-only Android View updates avoid semantic Node rebinding and unchanged LayoutParams work,
and immutable LocalSnapshot identity now changes only at provider boundaries. Unrelated navigation
and design-system matrices remain device-blocked but do not exercise these changes. The plan no
longer treats a broad diagnostics system, a shared frame scheduler, or general Compose parity as
prerequisites.

The old statement that all related work was unstarted is obsolete: later work independently added `snapshotFlow`,
configuration-aware Android resources, resource and environment revisions, transactional effects,
deferred child-session activation, and the logical-session/physical-tree collection reuse model.
Those capabilities are audited as current foundations below, not repeated as unfinished phases.

Last verified: 2026-08-15.

Archived after the final repository gates passed. Durable contracts live in current architecture,
module, migration, and performance documentation; this file remains historical evidence only.

## Maven release changesets

- `release/changes/20260814-composition-runtime-correctness.json`

## Objective

Correct runtime data propagation where the current behavior can publish an invalid logical state,
then remove two demonstrated classes of redundant Android View work without weakening immutable
render inputs, delayed-session ownership, renderer transactions, or native View lifecycle.

This plan is complete without a process-wide scheduler, persistent Local collection, new public
diagnostics surface, or Compose-equivalent implementation. An optimization remains only when its
targeted operation count falls and the replacement Demo baseline shows no policy regression.

## Re-audit conclusion

The original plan mixed four different kinds of work:

1. foundations or product capabilities that are already implemented;
2. two current correctness defects that should not wait for performance measurement;
3. small or medium optimizations with a concrete redundant-work path but no valid current Demo
   baseline; and
4. speculative infrastructure whose complexity is not justified by present evidence.

Only categories 2 and 3 remain active. Category 1 is recorded as evidence, and category 4 is
rejected or moved behind a new-plan trigger so it cannot silently become a release blocker.

## Audit method and current evidence

The re-audit inspected current source, tests, active architecture and module documentation, and the
changes landed since the plan's 2026-08-05 baseline. The important current facts are:

1. `SnapshotRuntime` already supplies MVCC reads, mutable-snapshot buffering, conflict handling,
   atomic global apply, history pruning, and the public `Snapshot.withMutableSnapshot` boundary.
2. `snapshotFlow` is implemented, documented, sampled, and tested. Snapshot collection types remain
   a separate product decision.
3. A successful global apply still accumulates `Observation` instances in a list. One observation
   that read several changed states is therefore invoked once per changed state, as its current
   public KDoc explicitly states.
4. `Transition.syncFromCore()` still writes seven observable mirrors separately. `Animatable` and
   `AnchoredDraggableState` also publish related target/running/value or
   semantic/target/offset/dragging fields in separate automatic snapshots. A Kotlin `synchronized`
   block serializes writers but does not make those snapshot publications atomic to readers.
5. `LocalContext.current()` uses a cast plus Elvis fallback. A present nullable value of `null` is
   therefore indistinguishable from an absent binding and incorrectly evaluates the default.
6. `LocalContext` stores a Map in its `ThreadLocal`, and each `snapshot()` creates a new immutable
   wrapper. `UiTreeBuilder` captures that wrapper while emitting nodes, so wrapper allocation still
   scales with emission rather than only provider boundaries.
7. `NodeBindingDiffer` still returns `Rebind` when only a Modifier changes. Preflight then resolves
   the chain, rebuilds `LayoutParams`, and runs a complete NodeSpec bind even though
   `ViewModifierApplier` can already compare and patch modifier families.
8. Targeted spec patches, renderer prepare/apply/commit/rollback, AndroidView reset/release, and
   aggregate rebind/patch/skip diagnostics already exist. The original broad Phase 0 is not needed
   to prove the two remaining redundant-work paths.
9. Configuration-aware resource access, `LocalResourceRevision`, framework-owned environment
   revisions, and retained child-session refresh are implemented. Same key plus equal content and
   environment revisions can skip lazy or pager item rendering.
10. `TabRow` is now an eager keyed child set, while Lazy containers and Pager retain independent
    logical Sessions and a separate bounded physical mounted-tree reuse layer. The old plan's
    assumptions about tab Sessions and undifferentiated delayed containers are obsolete.
11. Android render Sessions still own separate frame dispatchers and Choreographer callbacks.
    Per-session invalidations already coalesce, and no current trace or benchmark shows that callback
    registration, rather than the Sessions' actual composition and patch work, is material.

Authoritative current documents:

- [State snapshot architecture](../../architecture/state-snapshots.md)
- [Architecture overview](../../architecture/overview.md)
- [Delayed-session container checklist](../../architecture/session-containers.md)
- [Architecture decision index](../../architecture/decisions/README.md), including ADR-0007 for the
  host-owned Android resource environment and ADR-0012 for lazy collection logical and physical
  ownership
- [Performance specification](../../tooling/performance.md)
- [State and recomposition migration boundary](../../migration/compose-state-recomposition-and-restoration.md)
- [Layout, Modifier, and environment migration boundary](../../migration/compose-layout-modifier-and-environment.md)

## Scope

Retained work may affect:

- `viewcompose-runtime`: one-apply observation delivery and focused snapshot tests;
- `viewcompose-ui-foundation`: nullable Local lookup and LocalSnapshot representation;
- `viewcompose-animation` and `viewcompose-gesture`: atomic publication of fields that form one
  public logical state;
- `viewcompose-renderer-android`: a benchmark-gated modifier-only binding plan and its rollback,
  LayoutParams, modifier-family, and AndroidView coverage;
- the replacement Demo scenarios and `viewcompose-benchmark`: workload identity, operation-count
  evidence, and same-device acceptance; and
- affected public KDoc, compiled samples, module manuals, architecture, performance, migration, and
  localized documentation required by an implemented slice.

The plan does not own collection logical/physical reuse, the developer preview locator, general
diagnostics UI, theme-system policy, or new resource APIs.

## Audited decisions

| Area | Current evidence | Necessity and priority | Decision |
| --- | --- | --- | --- |
| Snapshot MVCC and explicit transactions | MVCC, nested mutable snapshots, conflict handling, and `Snapshot.withMutableSnapshot` exist and are tested. | Implemented foundation. | Retain; do not build another transaction layer. |
| `snapshotFlow` | Cold per-collector observation, invalidation conflation, documentation, samples, and tests exist. | Implemented independently. | Remove the stale rejection; snapshot collections remain out of scope. |
| Resource and environment propagation | Android resource APIs, host revision publication, VNode environment equality, and delayed-session refresh exist. | Implemented independently. | Treat as current input to renderer and item revision decisions. Do not reopen resource architecture here. |
| Collection Session and native-tree reuse | Lazy/Pager separate logical identity from bounded physical reuse; TabRow is eager keyed content. | Implemented independently. | Preserve the current architecture; no Session merging or tab scheduler work. |
| Renderer transactions and diagnostics | Targeted spec patches, rollback, reset/release, aggregate stats, patch records, and frame failure data exist. | Implemented foundation. | Reuse focused counters and tests; do not build the original general correlation subsystem. |
| Nullable Local lookup | Presence-aware lookup, nesting, batch providers, public snapshot restoration, exceptional restore, and a delayed item Session are tested. | P0 correctness was required because the generic API returned the wrong value. | Implemented: an explicit `null` now overrides the default. |
| Atomic related-state publication | Transition, Animatable, MutableTransitionState, anchored drag, and TextFieldState tuple tests now read only complete committed states. | P0 correctness: readers previously could observe a committed mixed tuple; repeated automatic applies were secondary cost. | Implemented with the existing Snapshot transaction boundary only around proven invariants. |
| One-apply observation delivery | Focused tests cover multi-state apply, separate applies, callback thread, conflict/no-op apply, disposal race, and `snapshotFlow`. | P1 contract simplification and deterministic hot-path reduction. | Implemented as a hard cut to at-most-once per successful global apply, with public KDoc, compiled sample, module, architecture, and migration updates. |
| Modifier-only Android View binding | Modifier-only changes currently force a full NodeSpec bind and unconditional LayoutParams rebuild. | P1 concrete redundancy, but end-to-end value and rollback shape still require the replacement benchmark. | Retain as a benchmark-gated experiment; keep only the smallest plan that reuses existing modifier-family comparison. |
| LocalSnapshot wrapper identity | Every capture creates a wrapper although the installed map is immutable for that scope. | P1 low-risk allocation experiment after a valid workload baseline. | Retain the `ThreadLocal<LocalSnapshot>` identity-reuse experiment; require deterministic allocation reduction and no regression. |
| Shared frame scheduler | Sessions register separate callbacks, but callbacks only dispatch independent render work and no evidence identifies registration as material. | Not currently necessary; high lifecycle, reentrancy, failure-isolation, and process-global retention cost. | Remove from this plan. A future trace showing material callback cost requires a separate plan and ADR-level ownership review. |
| Broad runtime-to-View trace correlation | Existing composition, renderer, tree, failure, and frame diagnostics already locate the retained gaps. | Not necessary for the current corrections; always-on correlation would add hot-path work. | Reject as a prerequisite. Add only bounded test/benchmark counters local to an experiment. |
| Productized optimization diagnostics | The Demo already exposes actionable renderer outcomes; the Demo itself is being rebuilt around stable scenarios. | Separate tooling concern with no current product requirement. | Remove the old Phase 5. Keep only metrics that remain useful after a decision. |
| Persistent Local map | The expected active Local count is small and wrapper reuse has not yet been measured. | Speculative dependency and lookup complexity. | Reject from this plan; require evidence from at least two revisioned workloads before a new proposal. |
| Observation dependency-set reuse | It reaches prepared composition commit/abort and subscription lifetime. | Risk is disproportionate without a measured allocation share. | Reject from this plan; a future trigger requires its own transactional design. |
| Derived-state equal-result suppression | Correct suppression changes when arbitrary user calculations run. | No proven workload or safe scheduling contract. | Do not implement here. |
| Tracked UiLocals | Automatic tracking would add hidden subscriptions and broad invalidation without compiler scopes. | Not needed for current environment/service lookup. | Keep explicit snapshot propagation; use State or Flow for changing business data. |
| Field-specific environment patching | Locale, density, font scale, direction, theme, and resources can affect broad native state. | Rare path and high semantic risk. | Keep conservative full rebind on environment inequality. |
| VNode, NodeSpec, LocalSnapshot, State, or View pools | Mutable pooling conflicts with immutable inputs, rollback, and final release. | Risk exceeds unproven allocation value. | Reject. Keep only the separately owned bounded mounted-tree cache for declared resettable content. |
| Nullable mutation-policy merge protocol | Current `null`-means-conflict cannot express a successful merge to `null`. | Real API-design issue, but unrelated to propagation or View patch hot paths. | Leave to a separately scheduled runtime API design. |

## Locked principles

### 1. Correctness work does not wait for performance infrastructure

A nullable Local returning its default and a framework state exposing a mixed committed tuple are
contract defects. Their focused reproductions and fixes may proceed before the Demo rearchitecture.

### 2. One logical publication uses one existing Snapshot transaction

Only fields whose public meaning forms one invariant are grouped. Do not wrap every adjacent write,
invent a parallel transaction type, or use frame debouncing to conceal intermediate commits.

### 3. Operation-count evidence precedes timing claims

The retained optimizations must first prove their exact effect: a modifier-only update performs no
full NodeSpec bind or unnecessary LayoutParams replacement, and LocalSnapshot wrappers scale with
provider boundaries instead of emitted nodes. Timing alone cannot substitute for those assertions.

### 4. Performance comparison requires stable workload identity

Do not compare the current text-coupled, changing Demo against a future fixture and call the result
an optimization. Performance slices start only after a direct scenario ID and explicit
`workloadRevision` identify the same fixture, setup, action, and result in both builds.

### 5. Native View ownership and renderer rollback remain authoritative

No patch may bypass preflight, mutate a View outside the renderer transaction, retain a View past
release, replay an AndroidView without its reset contract, or make failure rollback incomplete.

### 6. Simpler current infrastructure wins without a material trigger

Per-session scheduling, copy-on-provider Maps, fresh composition observations, lazy derived state,
and conservative environment rebind remain the default while no stable representative workload
shows that their cost is material.

## Phase 0: Focused reproductions and contract freeze — completed

Phase 0 is a correctness-test gate, not a diagnostics phase. Add the smallest executable evidence
for:

1. a nullable `UiLocal<String?>` whose non-null default is overridden with `null`, including nested
   providers, batch providers, capture/restore, exceptional restore, and one delayed child Session;
2. Transition mirror publication, Animatable begin/end/cancel publication, and anchored-drag
   snap/settle/anchor-reconciliation publication, proving that readers cannot observe a committed
   mixed tuple;
3. one `RuntimeObservation` reading two states changed by one explicit mutable snapshot, two
   independent successful applies, conflict/no-op apply, callback thread, disposal race, and
   `snapshotFlow` invalidation behavior; and
4. an inventory of framework-owned adjacent writes limited to animation, gesture, text,
   navigation, and session state, classifying each group as one invariant or independent events.

Contract freeze:

- an absent Local evaluates its default; a present binding returns its value even when that value
  is `null`;
- framework-owned fields documented as one logical state are visible from one successful Snapshot
  commit or not at all;
- if observation coalescing is retained, one observation is invalidated at most once per successful
  global apply while separate applies remain separate opportunities; and
- `synchronized` protects writer arbitration but never substitutes for Snapshot read consistency.

Phase 0 completed with focused executable tests, Q3 classifications for `RuntimeObservation`,
`Transition`, `Animatable`, `MutableTransitionState`, `AnchoredDraggableState`, and
`TextFieldState`, a Q2 classification for `UiLocals.current`, and the registered production
changeset. No cross-session trace chain or Demo page was required.

### Framework-owned adjacent-write inventory

| Area | Adjacent publication | Classification and retained action |
| --- | --- | --- |
| Animation | `Transition` mirrors seven segment fields. | One logical segment invariant; grouped in one transaction. |
| Animation | `Animatable` publishes target/running at start and retained target/idle at end; frame samples update value. | Start and end are separate logical boundaries and are each atomic; frame samples remain independent events. |
| Animation | `MutableTransitionState` mirrors current/target/idle; target is also caller writable. | Framework mirror is one invariant and is atomic; a caller target write remains an independent request. |
| Animation | Target-as-state, InfiniteTransition, and AnimatedContent update one state per channel or content identity. | Independent single-value events; no grouping added. |
| Gesture | `AnchoredDraggableState` publishes semantic value, target, offset, and dragging. | One gesture-state invariant per snap/delta/settle/cancel/reconciliation; grouped. |
| Gesture | `ToggleDragCompletion` publishes one immutable completion object. | Already one aggregate value; no grouping needed. |
| Text | `TextFieldState` publishes `TextFieldValue` plus the observable history version backing `canUndo`/`canRedo`. | One edit/undo/redo invariant; grouped. |
| Navigation | `NavHostController` publishes one immutable aggregate stack snapshot; transition specification is a separate rendering input. | Already aggregate or semantically independent; no grouping needed. |
| Session | `produceState` publishes one value; RenderSession lifecycle fields are a thread-confined state machine rather than independently observable Snapshot fields. | No multi-State public invariant; no grouping added. |

## Phase 1: Runtime and Local correctness hard cut — completed

### Nullable Local lookup

Change lookup to distinguish key presence from value nullability. Preserve current provider
nesting, batch provider order, opaque capture/restore, shallow value references, synchronous
ThreadLocal scope, effect-read prohibition, diagnostic formatting, and exception restoration.

Do not introduce a sentinel into public snapshots or make Local values automatically observable.

### Atomic related-state publication

Use `Snapshot.withMutableSnapshot` at the smallest framework mutation boundaries identified in
Phase 0. Cover Transition first, then Animatable and AnchoredDraggableState. Include
MutableTransitionState or another holder only when its actual update path publishes fields that are
documented as one invariant.

Preserve mutation arbitration, cancellation, conflict propagation, writer-thread callback
behavior, nested mutable snapshots, animation frame clocks, and gesture offset clamping. A failed
apply publishes none of the grouped fields and schedules no render.

### One-apply observation delivery

After the related-state tests pass, change invalidation accumulation to stable unique Observation
delivery outside runtime and state locks. This is a public behavior change because the previous
KDoc described one callback per changed observed state. Before implementation, assign the API's Q
level and update canonical KDoc, compiled samples, runtime module documentation, migration
guidance, and tests in the same slice.

Do not debounce across applies, frames, or time. Do not reuse Observation dependency sets.

Phase 1 completed after focused runtime, animation, gesture, text, Local, delayed-session, and
`snapshotFlow` tests passed and public contracts were aligned. Repository-wide gates are recorded
in the evidence ledger for the retained revision.

## Performance implementation gate — satisfied 2026-08-15

Phases 2 and 3 were blocked until the Demo rearchitecture provided:

1. a stable direct scenario ID and workload revision for the affected state/modifier and Local-heavy
   fixtures;
2. locale-independent ready, action, reset, state, and target selectors;
3. an isolated benchmark hierarchy without catalog or human-guidance content;
4. same-device release/R8 baseline results with source revision, device/build, thermal discipline,
   commands, raw paths, P50/P95, frame-overrun, allocation, heap, and RSS evidence; and
5. deterministic test-only or opt-in counters for only the operation targeted by the experiment.

Use the variance and regression thresholds from
[Performance](../../tooling/performance.md) and `tools/performance/benchmark_policy.json`. If the
workload revision changes, establish a new baseline instead of comparing unlike fixtures.

The accepted release state-patch, renderer diagnostics revision 3, list revision 3, and complex-
layout revision 3 results satisfy this gate. Diagnostics-theme, collection-stress, and shadow
comparisons provide additional representative coverage. Navigation revision 6 and design-bundle
revision 3 remain unaccepted because the current device cannot hold a stable clock policy, but
those workloads do not exercise modifier-only binding or LocalSnapshot allocation and therefore do
not block these experiments.

## Phase 2: Modifier-only Android View patch experiment

**Implemented and retained on 2026-08-15.** `ModifierOnly` is an explicit internal binding plan.
The renderer reuses the existing modifier-family appliers, resolves the next chain once, replaces
LayoutParams only for layout or parent-data changes, and keeps type/environment/spec incompatibility
and cross-owner reuse on full rebind. The path remains inside renderer preflight and rollback,
continues child reconciliation, replays changed native configuration keys, and does not run
AndroidView update, reset, commit, or release callbacks.

Focused transaction evidence records zero rebound nodes and one patched node for the target update.
A visual-only update preserves the exact LayoutParams object, while a width change replaces it
without semantic rebinding. The renderer's complete unit suite covers the existing draw/property,
semantics/interaction, insets, nested-scroll, focus, decoration, and removal cleanup appliers;
new route-level tests cover native configuration, AndroidView lifecycle, and failed-patch rollback.

The post-change five-iteration `runtime.view-patch` release run started at thermal status `NONE` and
ended at `MODERATE`; frame CPU P50/P95 were `2.507/4.457 ms`, versus `2.864/4.470 ms` in the accepted
pre-change result. This is a non-regression signal, not a replacement longitudinal baseline: the old
result predates the explicit clock-policy identity, and AndroidX still reports that this non-rooted
device cannot clear its Runtime Image. The deterministic operation-count target and full renderer
suite are therefore the keep authority; the physical result supplies the required affected-workload
check without rewriting the accepted baseline.

Introduce one explicit modifier-only binding plan only if the baseline reaches the current Rebind
path. The implementation must:

1. reuse `ViewModifierApplier` family comparison rather than duplicate Node binder logic;
2. resolve the next modifier chain once;
3. rebuild or replace LayoutParams only when resolved layout or parent data changes;
4. preserve cleanup when a modifier family is removed;
5. replay stable native configuration only when its existing semantic key requires it;
6. keep full rebind for type or environment changes, incompatible specs, cross-owner reuse, and any
   family whose equivalence cannot be proved; and
7. preserve preflight, transaction checkpoints, commit effects, AndroidView reset/release, and
   failure rollback.

Required evidence includes draw/property, semantics/interaction, layout, insets, nested scroll,
focus, decoration, native configuration, AndroidView, and failed-patch cases. The target scenario
must report zero full binds and zero LayoutParams replacement for a visual-only modifier update.

Keep the change only when the operation-count target is met and no representative scenario regresses
beyond policy. If the plan grows type-specific binder branches or incomplete rollback, revert it and
retain only independently useful tests.

## Phase 3: LocalSnapshot identity-reuse experiment

**Implemented and retained on 2026-08-15.** `LocalContext` now stores the installed immutable
`LocalSnapshot`, returns that exact instance from repeated same-scope captures, creates one snapshot
for a `ProvideLocals` batch, and restores the exact prior identity after nested or exceptional
execution. Public `UiLocalSnapshot` captures remain fresh opaque wrappers around the installed
delegate, so no public identity or mutability contract was introduced.

The first implementation removed the ThreadLocal slot when the outer provider exited and fetched
the prior snapshot twice at provider entry. A same-device diagnostic run then showed an anomalous
`52.945 ms` frame-CPU P95. Review found that `remove()` forced a new `ThreadLocalMap.Entry` at the
next provider boundary, unlike the previous Map-based runtime, which retained its empty value. The
retained implementation restores the singleton empty snapshot instead and passes the already-read
prior identity into the installer.

Under the same adjacent `LIGHT` thermal and OEM-capped-frequency condition, the Phase 2-only build
reported `6.172/42.697 ms` frame-CPU P50/P95, while the corrected Phase 3 build reported
`5.874/40.070 ms`; its five run-P50 values had population CV `0.0178`. This adjacent comparison is
attribution evidence, not a replacement longitudinal baseline: the device held CPU ceilings at
`1.586/1.352 GHz`, AndroidX reported that it could not clear the Runtime Image, and the active
performance contract rejects changing frequency ceilings. The keep authority is therefore the
deterministic identity/allocation model plus the complete UI Foundation test suite; the device run
shows no adjacent-version regression signal without weakening the accepted baseline.

After the nullable correctness fix, test the smallest representation change:

1. store the current immutable `LocalSnapshot` in the ThreadLocal;
2. return that installed instance from `snapshot()`;
3. create one new snapshot at a provider boundary and restore the exact prior snapshot on exit; and
4. install the supplied snapshot object directly during `withSnapshot`.

Preserve Map equality, opaque public wrappers, nesting, batch providers, effect boundaries,
exception restoration, diagnostics redaction, delayed-session refresh, and shallow reference
semantics. Do not add a persistent collection or cache.

The deterministic keep gate is that LocalSnapshot creation scales with provider boundaries and
explicit public captures rather than emitted-node count. Also require no regression in the
revisioned Local-heavy and representative state/list/layout scenarios. Revert if identity reuse
requires mutable snapshots, hidden ownership, or broader caching.

## Work removed from the active sequence

The following items are not later phases of this plan:

- shared Choreographer batching across Sessions;
- cross-session state-to-View correlation infrastructure;
- public or Demo productization of experimental counters;
- persistent Local maps;
- Observation dependency-set reuse;
- derived-state equal-result suppression;
- tracked UiLocals;
- field-specific environment patches;
- VNode, NodeSpec, LocalSnapshot, State, Session, or process-global View pools;
- merged Lazy, Pager, overlay, or navigation Sessions; and
- nullable mutation-policy merge redesign.

A future proposal must begin from a new reproducible product or benchmark trigger and must not cite
their presence in the 2026-08-05 plan as approval.

## Validation matrix

| Area | Required validation |
| --- | --- |
| Snapshot/runtime | automatic and explicit snapshots, nested apply, conflict/merge, history pruning, one-apply observation delivery, callback thread, disposal race, composition consistency, and `snapshotFlow` tests |
| Animation/gesture | Transition retarget/frame, Animatable arbitration/cancellation/failure, MutableTransitionState where classified, and anchored-drag snap/settle/anchor/cancel tests |
| Locals | absent/nullable/nested/batch/capture/restore/exception tests plus resource, lazy, pager, overlay, and navigation refresh evidence |
| Renderer | binding plan, modifier families, LayoutParams identity, rollback, reused-View cleanup, AndroidView, semantics, insets, focus, decoration, and nested-scroll tests |
| Collections | equal revision skip, changed revision, cross-key physical reuse, detached activation, reset/release, parent rollback, and long-fling regression coverage |
| Performance | stable scenario and workload revision, R8 release build, targeted counters, same-device before/after data, representative paired workloads, and policy gate |
| Documentation | canonical active pages, affected module manuals, required Chinese mirrors, API-quality/sample gates, and `verifyDocumentationStructure` |

Minimum repository gates for each retained implementation slice include focused changed-module tests
followed by:

```bash
./gradlew qaQuick
./gradlew qaRelease
./gradlew verifyDocumentationStructure
```

Run `qaFull` for visible interaction, collection, lifecycle, input, animation, or gesture changes.
Run the revisioned connected benchmark scenario on an unlocked, thermally controlled device for
every performance-only phase.

## Documentation and release impact

This planning-only re-audit changes no public API and no Maven artifact.

Implemented durable changes have these same-change obligations:

- Snapshot or Observation semantics: canonical runtime KDoc, assigned Q level, compiled samples
  when required, runtime module manual, state architecture, migration comparison, and required
  locales;
- Local lookup or propagation: UI Foundation KDoc and samples when required, module manual,
  architecture and environment migration documents, and required locales;
- renderer patch or rollback behavior: renderer module manual, performance specification, relevant
  architecture documents, and required locales; and
- benchmark identity or metric semantics: Demo scenario contract, capability-verification and
  performance documentation, with no public framework API added solely for Demo automation.

Every pull request that changes production source or other publication input for a published
artifact adds one immutable `release/changes/<unique>.json` file and classifies each detected
artifact. Never hand-write reverse-dependency propagation.

## Completion criteria

This plan is complete when all of the following are true:

1. the nullable Local and related-state atomicity defects have focused executable evidence and are
   fixed with aligned public contracts;
2. one-apply observation delivery has either been retained with its hard-cut contract and tests or
   explicitly rejected with compatibility evidence;
3. the replacement Demo scenarios and workload revisions provide a valid performance baseline;
4. modifier-only binding and LocalSnapshot identity reuse each have a recorded keep/revert decision
   against deterministic operation counts and the performance policy;
5. no removed speculative item has entered production without a new trigger and separate approved
   design;
6. all retained production changes have required release changesets, API documentation, compiled
   samples, module manuals, architecture/performance/migration updates, and reviewed Chinese
   mirrors;
7. focused tests, repository quality gates, documentation gates, and required device/benchmark
   gates pass at the final retained revision;
8. durable conclusions move into current architecture, performance, migration, and module
   documentation; and
9. this plan and its final evidence move to `docs/archive/` with the active-plan index updated.

## Evidence ledger

| Date | Revision | Evidence | Result and decision |
| --- | --- | --- | --- |
| 2026-08-05 | Working tree | Initial source and active-document review; no performance command run | Broad five-phase optimization plan created. |
| 2026-08-14 | Current working tree | Current source, tests, architecture, module documentation, and post-2026-08-05 change history audited | Confirmed nullable Local and related-state atomicity defects; confirmed modifier-only rebind and per-node LocalSnapshot allocation; removed shared scheduling and broad diagnostics from the active sequence. |
| 2026-08-14 | Current working tree | `snapshotFlow`, configuration-aware resources, resource/environment revisions, transactional effects, delayed-session activation, and collection ownership/reuse evidence | Marked these as independently implemented foundations rather than unfinished Runtime/Patch work. |
| 2026-08-14 | Current working tree | Demo benchmark and automation audit | Performance experiments blocked on direct scenario IDs and explicit workload revisions; correctness work remains unblocked. |
| 2026-08-14 | Current working tree | Focused RuntimeObservation, nullable Local/delayed Session, Transition, Animatable, MutableTransitionState, anchored drag, TextFieldState, and snapshotFlow tests | Phase 0 and Phase 1 correctness hard cut implemented; modifier-only Patch and LocalSnapshot experiments remain gated by the replacement Demo baseline. |
| 2026-08-15 | Demo revisioned release, renderer, list, complex-layout, diagnostics, collection, and shadow results | All affected and representative Runtime/Patch workloads pass the repository stability gate; only unrelated navigation/design matrices remain device-blocked | Unlock Phase 2 and Phase 3 independently; each still requires deterministic operation-count improvement and no accepted-workload policy regression. |
| 2026-08-15 | `NodeBindingDifferTest`, `ViewTreePatchPipelinePlanTest`, `ViewTreeRenderTransactionTest`, and the complete renderer unit suite | Pure visual modifier update reports zero rebound/one patch and preserves LayoutParams identity; layout changes replace only LayoutParams; native configuration rollback and AndroidView lifecycle isolation pass | Retain Phase 2 `ModifierOnly`; it removes the measured redundant operations without new binder branches or lifecycle escape paths. |
| 2026-08-15 | Five-iteration release `runtime.view-patch` post-change run on SM-G991B / Android 13 | Start thermal `NONE`, end `MODERATE`; frame CPU P50/P95 `2.507/4.457 ms` versus accepted pre-change `2.864/4.470 ms`; explicit clock policy present, but the legacy baseline identity and Runtime Image warning prevent replacement-baseline status | No regression signal; retain the deterministic Phase 2 decision and leave the accepted longitudinal baseline unchanged. |
| 2026-08-15 | `LocalValueTest` identity, nesting, batch, public-wrapper, and exceptional-restoration cases plus the complete UI Foundation unit suite | Same-scope capture returns the installed immutable snapshot; each provider boundary creates one identity; nested and failed execution restore the exact caller; one `ProvideLocals` batch installs one snapshot | Retain the Phase 3 representation hard cut without a persistent collection, mutable cache, or public identity contract. |
| 2026-08-15 | Adjacent Phase 2-only and corrected Phase 3 five-iteration `performance.complex-layout@3` update runs on SM-G991B / Android 13 | Phase 2-only P50/P95 `6.172/42.697 ms`; corrected Phase 3 `5.874/40.070 ms`, run-P50 CV `0.0178`; both used the explicit clock policy, but the OEM frequency ceiling was capped and Runtime Image clearing failed | No adjacent-version regression signal. Treat as attribution evidence only, keep the accepted longitudinal baseline unchanged, and reject the first `remove()` implementation whose diagnostic P95 reached `52.945 ms`. |
| 2026-08-15 | Final `qaQuick`, `qaRelease`, and documentation structure gates | `qaQuick` passed 1,619 tasks; `qaRelease` passed 837 tasks; focused UI Foundation and renderer suites and the documentation mirror/fingerprint checks passed | All retained phases satisfy their repository gates; archive the plan and continue from current architecture and module contracts. |

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-05 | Preserve ThreadLocal-based synchronous dynamic scope | Replacement does not address Local lookup correctness or measured work. |
| 2026-08-05 | Prefer existing Snapshot transactions and immutable render inputs | They preserve conflict, observation, rollback, and subtree-skip semantics. |
| 2026-08-14 | Narrow immediate work to nullable Local lookup, atomic related-state publication, and one-apply observation delivery | These are current contract defects or deterministic transaction-level redundancy. |
| 2026-08-14 | Retain modifier-only binding and LocalSnapshot identity reuse behind the replacement Demo baseline | Their redundant-work paths are concrete, but end-to-end keep decisions require stable workload identity. |
| 2026-08-15 | Retain the implemented modifier-only binding plan | It reaches zero full binds and zero LayoutParams replacement for visual-only changes, preserves renderer rollback and AndroidView lifecycle, passes the full renderer suite, and shows no affected-workload regression signal. |
| 2026-08-15 | Retain LocalSnapshot identity reuse but restore, rather than remove, the empty ThreadLocal value | The retained form makes allocation follow provider boundaries, preserves exact nesting/restoration identity, avoids per-composition ThreadLocal entry churn, and has no adjacent-version benchmark regression signal. |
| 2026-08-14 | Remove shared Session scheduling from this plan | Callback batching does not reduce independent render work, and no evidence justifies its global lifecycle complexity. |
| 2026-08-14 | Remove broad trace correlation and diagnostics productization | Existing focused diagnostics are sufficient for retained work; more hot-path instrumentation is not a product goal. |
| 2026-08-14 | Reject persistent maps, dependency-set reuse, derived suppression, tracked Locals, environment partial patches, and object pools from the active sequence | Their current benefit is unproven and their semantic or maintenance risk is disproportionate. |
| 2026-08-14 | Correct stale capability assumptions | `snapshotFlow`, resource/environment propagation, transactional effects, delayed activation, and logical/physical collection reuse already exist. |
