# Runtime Data Propagation and Android View Patch Optimization Plan

## Status

Active and ready for scheduling. No implementation phase has started.

This plan records the approved optimization direction, the evidence required before each risky
change, explicit rollback gates, and the alternatives that are deliberately not scheduled. It is a
temporary execution plan and remains canonical English-only under the documentation-governance
policy. When the work is complete or intentionally cancelled, durable conclusions move into the
owning architecture, performance, migration, and module documents before this file moves to
`docs/archive/`.

Last verified: 2026-08-05.

Next action: execute Phase 0 without changing runtime behavior, capture the first same-device
baseline, and update the status and evidence ledger in this plan before scheduling Phase 1.

## Objective

Improve ViewCompose state publication, local propagation, Android View patching, and multi-session
frame scheduling while preserving the architecture's current strengths:

1. `ThreadLocal` remains the synchronous dynamic-scope carrier for locals, observation, composer,
   and snapshot contexts where it is already appropriate.
2. `UiLocalSnapshot` remains an explicit opaque boundary for delayed content.
3. `RenderSession` instances remain independently owned and independently disposable.
4. VNodes, NodeSpecs, modifiers, and captured environments remain immutable render inputs.
5. Snapshot writes continue through the MVCC transaction and conflict path.
6. Android View creation, mutation, layout, and disposal remain main-thread work.
7. Renderer apply and composition preparation retain their current commit/abort and rollback
   boundaries.

The goal is not feature parity with Jetpack Compose. The goal is to remove demonstrable redundant
work and strengthen consistency inside the native Android View architecture.

## Scope

The planned work may affect these implementation areas:

- `viewcompose-runtime`: snapshot apply, observation delivery, diagnostics, and transaction tests;
- `viewcompose-widget-core`: local snapshot representation and render diagnostics;
- `viewcompose-animation` and `viewcompose-gesture`: atomic publication of related state holders;
- `viewcompose-renderer`: modifier-only binding plans and Android View patch classification;
- `viewcompose-host-android`: shared frame batching for independent render sessions;
- `viewcompose-benchmark` and the Demo benchmark surfaces: targeted workloads and trace metrics;
- active architecture, performance, migration, module, and localized documentation affected by an
  implemented behavior or public-contract change.

## Non-goals

This plan does not include:

- removing or replacing `ThreadLocal` merely to use a different context mechanism;
- introducing a compiler plugin, compiler-generated restart groups, change masks, or stability
  inference;
- concurrent or background composition;
- merging delayed child sessions into a parent composition or weakening their independent
  lifecycle;
- replacing VNode immutability with mutable pooled nodes;
- adding a process-global View, Session, VNode, LocalSnapshot, or state-object cache;
- making every `UiLocal` read automatically observable;
- adding snapshot collections or a `snapshotFlow` equivalent without an independent product use
  case;
- changing RecyclerView pool ownership or motion defaults;
- optimizing rare configuration changes before measured render or allocation hot paths;
- bypassing Snapshot apply, merge, conflict, observation, or history-pruning semantics;
- keeping a complex optimization whose targeted work reduction or end-to-end result cannot be
  demonstrated.

## Current baseline

Verified from the current main worktree on 2026-08-05:

1. `SnapshotRuntime` supplies MVCC read versions, buffered mutable snapshots, atomic global apply,
   policy-based conflict merging, observer notification, and history pruning.
2. A framework write outside an explicit mutable snapshot creates and applies an automatic mutable
   snapshot.
3. One successful global apply currently accumulates observer callbacks in a list. The same
   `Observation` can therefore be invoked multiple times when it read several states changed by the
   same transaction.
4. `Transition.syncFromCore()` publishes seven related state holders as separate writes.
   `AnchoredDraggableState` publishes related semantic and offset states as separate writes, and
   `Animatable` has similar multi-holder transitions.
5. `NodeBindingDiffer` chooses `Rebind` when the NodeSpec is unchanged and only the Modifier
   changed, even though `ViewModifierApplier` already compares resolved modifier families before
   mutating the View.
6. `LocalContext` stores a Map in a `ThreadLocal`; every `snapshot()` call creates a new
   `LocalSnapshot` wrapper, and every `UiTreeBuilder.emit()` captures one snapshot.
7. `LocalContext.current()` uses an Elvis fallback and does not distinguish an absent binding from
   a present nullable binding whose value is `null`.
8. Every Android render session owns its own frame dispatcher and Choreographer-backed frame clock.
   Invalidations coalesce inside one session but not across independent sessions.
9. `ComposerLite` creates a candidate `Observation` for every recomposed scope and commits or
   abandons it transactionally. This is intentionally simple but may create subscription churn.
10. Diagnostics already report composition reasons, tree structure, rebind/patch/skip outcomes,
    and layout metrics. Cross-session correlation, targeted runtime counters, and per-node time are
    not complete.
11. Release macrobenchmarks already cover cold start, state patches, lists, complex layouts,
    navigation, animation-adjacent workloads, frame timing, heap, and RSS, with Compose controls for
    selected scenarios.

Authoritative current documents:

- [State snapshot architecture](../../architecture/state-snapshots.md)
- [Architecture overview](../../architecture/overview.md)
- [Delayed-session container checklist](../../architecture/session-containers.md)
- [Performance specification](../../tooling/performance.md)
- [State and recomposition migration boundary](../../migration/compose-state-recomposition-and-restoration.md)
- [Layout, Modifier, and environment migration boundary](../../migration/compose-layout-modifier-and-environment.md)

## Decision principles

### 1. Correctness outranks benchmark improvement

Atomic publication of fields that form one public state and correct nullable-local lookup may stay
even when frame timing does not move, provided the implementation remains small and does not cause
a regression. A performance-only experiment does not receive this exception.

### 2. Operation-count evidence comes before timing claims

Each optimization first proves that it removes the exact redundant work it targets. Examples
include seven automatic state applies becoming one explicit apply, modifier-only changes producing
zero full node rebinds, or N invalidated sessions using one Choreographer callback. A noisy frame
timing result cannot replace a failed operation-count assertion.

### 3. Complex work requires a before/after experiment

Modifier-only patching, persistent local maps, shared session scheduling, observation subscription
diffing, and derived-state conditional invalidation must not begin with an unmeasured production
rewrite. The required sequence is:

1. add correctness and workload coverage;
2. capture a release/R8 baseline;
3. implement on a separately revertible commit or branch;
4. repeat the same-device and same-build workload;
5. keep, simplify, or revert according to the phase gate;
6. retain useful tests and diagnostics even if the optimization is reverted.

### 4. Complexity must be proportional to durable benefit

A small internal change may remain when it deterministically removes redundant work and causes no
regression. A new shared runtime, dependency, cache, public API, or transactional state machine must
also show a stable end-to-end benefit above the repository's noise floor.

### 5. Preserve ownership and rollback boundaries

No optimization may share mutable composition data between sessions, retain a View beyond its
owner, run View work away from the main thread, publish effects before renderer commit, or make a
failed frame harder to roll back.

## Priority and scheduling decision

| Priority | Work item | Expected value | Complexity | Scheduling decision |
| --- | --- | --- | --- | --- |
| P0 | Measurement and diagnostic baseline | Enables every later decision and strengthens current diagnostics | Medium | Required before behavior changes |
| P1 | Atomic publication of related framework state plus transaction-level observer coalescing | High consistency and hot-path reduction for animation, gesture, and state patches | Low to medium | Schedule after Phase 0 |
| P1 | Modifier-only Android View patching | High native-View binding and layout benefit | Medium | Benchmark-gated implementation |
| P2 | LocalSnapshot correctness and low-risk allocation reduction | Medium allocation benefit plus one correctness fix | Low | Schedule after P1 work |
| P2 | Shared frame batching for independent sessions | Potentially high benefit in Lazy, Pager, overlay, and navigation workloads | High | Baseline first; revert without stable benefit |
| Conditional | Persistent local map | Unknown until Local provider copying is measured | Medium | Do not schedule unless Phase 3 trigger is met |
| Deferred | Observation dependency-set reuse | Possible allocation benefit but high transaction/rollback risk | High | Do not schedule in this plan |
| Deferred | Derived-state equal-result suppression | Workload-specific benefit and difficult thread semantics | High | Do not schedule in this plan |
| Deferred | Environment-specific partial rebind | Low-frequency path with broad semantic impact | Medium | Do not schedule in this plan |

## Measurement and rollback policy

### Required build and device discipline

1. Measure an R8-optimized, resource-shrunk target through the existing release benchmark path.
2. Run before and after results on the same device model, Android build, thermal state, power state,
   compilation mode, workload, iteration count, and benchmark APK inputs.
3. Treat a run coefficient of variation above `0.15` as unstable and rerun it.
4. Apply the raw and normalized regression policy in
   `tools/performance/benchmark_policy.json`; never interpret cross-device division as a speedup.
5. Record the source revision, device identity, system fingerprint, command, raw result path, and
   summarized counters in this plan's evidence ledger.

### Required phase evidence

Every implementation phase must provide all of the following:

- deterministic unit or instrumentation assertions for the targeted operation-count reduction;
- correctness and failure-path coverage, including rollback or disposal where applicable;
- no regression beyond the existing P50, P95, heap, and RSS policy;
- at least one representative end-to-end workload, not only a synthetic loop;
- an explicit keep/revert conclusion recorded in this plan.

### Additional gate for high-complexity experiments

Shared session scheduling, a persistent-map dependency, observation subscription reuse, or another
new runtime state machine is kept only when:

1. the targeted operation count is reduced as designed;
2. at least one stable representative scenario improves beyond measurement noise in frame CPU,
   frame overrun, allocation, heap, or RSS;
3. no paired scenario regresses beyond policy;
4. the new code does not require a broader ownership or public-contract exception;
5. the maintenance cost is documented and judged proportional to the measured result.

If these conditions are not met, revert the implementation. Keep only independently useful tests,
trace points, benchmark workloads, and documented findings.

### Revert mechanics

- Land baseline diagnostics and each optimization in separate commits whenever practical.
- Do not mix public API expansion, module dependency changes, and the optimization experiment in
  one inseparable commit.
- Do not preserve dead compatibility branches after reverting an experiment.
- Update this plan with the rejected result and evidence so the same experiment is not repeated
  without a changed premise.

## Phase 0: Diagnostics and benchmark baseline

### Goal

Create enough low-overhead evidence to distinguish state publication, composition, session
scheduling, renderer binding, and Android layout work before changing behavior.

### Runtime and renderer counters

Add benchmark/test-visible internal counters or trace fields for:

- state writes, automatic mutable snapshots, explicit mutable snapshots, successful applies,
  conflicts, changed state objects, and unique notified observations;
- invalidated scopes, queued scopes, scheduled render requests, direct renders, and actual frame
  renders;
- LocalSnapshot creation, installation, capture, and identity reuse;
- full node rebinds, spec patches, modifier-only patches, skipped nodes, skipped subtrees, and
  LayoutParams rebuilds;
- frame callbacks requested, cancelled, drained, and the number of sessions drained per callback.

Instrumentation must be disabled or allocation-free on the normal release path unless an existing
diagnostic or trace mode is enabled. Do not add an always-growing event list.

### Trace correlation

Add an internal correlation chain that can connect:

```text
state apply
    -> observation invalidation
    -> recompose scope
    -> render session and frame
    -> VNode patch
    -> Android View mutation/layout request
```

Every delayed child session should expose an opaque parent/session correlation identifier to
diagnostics without granting access to another session's local map or lifecycle. Correlation IDs
are process-local diagnostics, not persistence or public identity.

### Benchmark workloads

Establish or extend workloads for:

1. one Transition frame that updates multiple channels and mirror states;
2. one anchored-drag state transition that updates semantic and offset holders;
3. modifier-only updates separated into draw/property, semantics/interaction, and layout families;
4. a Local-heavy tree with nested providers, hundreds of emitted nodes, unchanged renders, and one
   provider change;
5. multiple visible independent item/page sessions observing one shared State;
6. a representative existing state-patch, list, complex-layout, and navigation workload so a
   synthetic improvement cannot hide an application regression.

### Phase 0 completion gate

- Counters are deterministic in unit or instrumentation tests.
- Trace/counter collection does not change output, scheduling, or lifecycle behavior.
- Release builds with diagnostics disabled show no policy regression.
- The first baseline is recorded in the evidence ledger.

## Phase 1: Atomic state publication and observation coalescing

### Goal

Publish fields that form one logical framework state in one Snapshot transaction and deliver at
most one callback to the same Observation for one successful global apply.

### Ordered work

1. Inventory framework-owned consecutive writes in animation, gesture, navigation, text, and
   session state. Classify each set as one invariant or independent events.
2. Wrap only invariant-related writes in `Snapshot.withMutableSnapshot` or an internal helper that
   delegates to it. Do not create a separate transaction mechanism.
3. Cover Transition mirror fields, `Animatable` mutation boundaries, and anchored-drag semantic and
   offset publication first.
4. Change global apply invalidation accumulation from duplicate callbacks to stable unique
   Observation delivery.
5. Preserve callback delivery outside runtime and state locks.
6. Update `RuntimeObservation` KDoc and tests from per-changed-state callback count to at-most-once
   per successful apply if that public contract change is retained.
7. Add concurrent apply, conflict, nested snapshot, writer-thread callback, composition consistency,
   and failed-apply coverage.

### Required assertions

- One Transition synchronization advances the global snapshot at most once when values change.
- Readers never observe a committed mixed tuple of related Transition or anchored-drag fields.
- One Observation reading multiple changed states is invoked once for that apply.
- Two independent successful applies still produce two invalidation opportunities.
- A conflict applies none of the related fields and produces no invalidation.
- Frame scheduling still coalesces repeated invalidations.

### Keep or revert rule

Atomic publication is retained for its correctness value when the implementation remains a small
use of the existing Snapshot API and causes no regression. Transaction-level observation
coalescing is reverted or narrowed if public consumers require per-state callback counts or if
concurrent tests reveal lost invalidations. It must not be emulated with a timer or frame-only
debounce.

## Phase 2: Modifier-only Android View patches

### Goal

Avoid complete NodeSpec binding when only Modifier data changed, while preserving View reuse,
LayoutParams behavior, native configuration replay, and renderer rollback.

### Ordered work

1. Capture separate baselines for draw/property, semantics/interaction, insets, native
   configuration, and layout Modifier changes.
2. Add a modifier-only binding plan instead of manufacturing an empty NodeViewPatch.
3. Reuse `ViewModifierApplier` family comparisons so an unchanged family performs no setter work.
4. Re-resolve modifiers only when their ordered chain changed.
5. Rebuild LayoutParams only when resolved layout or parent-data fields changed.
6. Replay `nativeView` configuration only when its stable semantic key requires it.
7. Keep full rebind for node-type changes, incompatible NodeSpec classes, density/font-scale changes,
   and any family whose patch equivalence cannot be proven.
8. Record modifier-only outcomes separately from spec patches and full rebinds.

### Required assertions

- Modifier-only draw/property changes perform zero NodeSpec full binds.
- A visual-only patch does not replace LayoutParams or request parent constraint regeneration.
- A layout Modifier change produces the correct LayoutParams and layout request.
- Removing a Modifier family restores the original reused-View state.
- Semantics, nested scroll, insets listeners, click/focus state, decoration, z order, and
  `nativeView` retain their current cleanup and rollback behavior.
- A failed patch restores the previously committed View and diagnostic state.

### Keep or revert rule

Keep the plan only when targeted full rebinds become modifier-only patches and representative
state-patch or complex-layout results do not regress. If the plan grows special cases that duplicate
Node binder logic, or if rollback cannot remain complete, revert it and retain only any safe
family-specific patch discovered during the experiment.

## Phase 3: LocalSnapshot correctness and allocation reduction

### Goal

Reduce per-node local snapshot allocation without changing lookup, provider nesting, explicit
capture/restore, delayed-session refresh, or snapshot equality semantics.

### Required correctness fix

Make `LocalContext.current()` distinguish:

- no binding: evaluate the Local default factory;
- present non-null binding: return it;
- present nullable binding with `null`: return `null`.

Add public-behavior tests using a nullable Local whose default is non-null, nested overrides, batch
providers, captured snapshots, and delayed child sessions.

### Low-risk representation experiment

1. Store the current `LocalSnapshot` in the `ThreadLocal` instead of storing only its Map.
2. Let `snapshot()` return the installed snapshot instance.
3. Create one new snapshot at a provider boundary and restore the exact previous snapshot on exit.
4. Install the supplied snapshot object directly in `withSnapshot`.
5. Preserve opaque values, diagnostic formatting, Map equality, nesting, and exception restoration.

Expected deterministic result: snapshot-wrapper creation scales with provider boundaries and
explicit captures rather than emitted-node count.

### Conditional persistent-map experiment

Do not add a persistent-collection dependency or custom persistent trie during the initial change.
Open that experiment only if Phase 0 and the low-risk representation result show that provider Map
copying remains a material allocation or render-CPU contributor in at least two representative
workloads.

If triggered, compare:

- the current copy-on-provider Map;
- a small internal structurally shared map optimized for the expected Local count;
- a maintained persistent-collection dependency that satisfies module-boundary and delivery-cost
  constraints.

Reject the persistent-map change if it adds dependency or lookup complexity without a stable
allocation, heap, or frame benefit.

### Required assertions

- Nested provide/restore behavior is unchanged across normal and exceptional exit.
- Explicit `captureUiLocalSnapshot` and `withUiLocalSnapshot` remain opaque and synchronous.
- Lazy, Pager, tab, overlay, and navigation sessions refresh the latest snapshot and closure.
- Mutable values remain shallow references; no implementation claims to freeze or make them
  thread-safe.
- Diagnostics never expose original sensitive Local objects.

## Phase 4: Shared frame batching for independent sessions

### Goal

Allow main-looper render sessions to share one Choreographer callback per frame without sharing
their composition, state, local map, mounted View tree, or lifecycle.

### Mandatory pre-implementation baseline

Before creating a shared scheduler, demonstrate all of the following in the multi-session workload:

- more than one frame callback is currently registered for one logical shared-state change;
- the affected sessions are active and independently require rendering;
- callback or dispatcher work is visible in trace counts or stable frame/memory data;
- direct session rendering and ordinary single-session rendering are not the dominant cost being
  misclassified as scheduling cost.

If the baseline does not show duplicated scheduling as a material contributor, stop Phase 4 and
record the result. Do not implement the shared scheduler for conceptual neatness.

### Experimental design

1. Add one main-looper batch scheduler owned by the installed Android render platform.
2. Queue opaque session runtime handles in stable insertion order.
3. Register at most one platform frame callback while the queue is non-empty.
4. Remove a session from the queue before direct `render()` and before disposal.
5. Keep one pending invalidation while a session is inactive; enqueue it only after reactivation.
6. Send invalidations created while draining to the next frame unless the owning session's current
   contract explicitly requires synchronous rendering.
7. Continue rendering other queued sessions if one session reports a recoverable frame failure;
   preserve existing failure propagation at the owning session boundary.
8. Hold no strong reference after session disposal and retain no Activity, Fragment, container, or
   View in a process-global queue.

### Required assertions

- N requesting active sessions produce one platform frame callback and N independent render calls.
- Repeated requests from one session coalesce.
- A disposed or inactive session is not rendered.
- Reactivation preserves one pending invalidation.
- Direct render cancels only the target session's queued frame work.
- Reentrant invalidation during drain schedules the correct next frame.
- One session failure does not corrupt or dispose another session.
- Lazy holder recycle, Pager detach/attach, overlay dismissal, and navigation removal leave no
  queued handle.

### Keep or revert rule

This is a high-complexity optimization. Keep it only if the high-complexity gate in this plan is
met. If callback count falls but representative frame, allocation, heap, and RSS results remain
below noise, prefer the simpler per-session dispatcher and revert the shared scheduler.

## Phase 5: Productize proven diagnostics

### Goal

Retain diagnostic capabilities that materially improve investigation after the optimization phases,
without turning benchmark-only implementation details into permanent public API.

### Ordered work

1. Review every Phase 0 counter and remove those that did not influence a decision.
2. Keep internal trace fields needed for regression triage and benchmark automation.
3. Add bounded cross-session correlation to `RenderTreeResult` or a separate opt-in diagnostic type
   only if it is useful in Lazy, overlay, or navigation investigations.
4. Add per-node or per-phase timing only in opt-in diagnostics; cap records and do not call a clock
   for every node when diagnostics are disabled.
5. Extend the Demo inspector only for stable concepts that application developers can act on.
6. Update the performance specification with the retained metric definitions, collection overhead,
   and interpretation rules.

### Public API quality

Initial experiments use internal counters and trace events. If a later phase changes public
diagnostic types, assign at least Q2 and document:

- exact unit and aggregation boundary;
- whether the value is a count, duration, maximum, or sampled estimate;
- session and frame ownership;
- opt-in overhead and record bounds;
- thread, ordering, and persistence limitations;
- one compiled diagnostic sample when interpretation is non-trivial.

Do not expose raw Snapshot IDs, mutable state objects, Local values, View references, callback
instances, or benchmark-only class names.

## Explicitly deferred or rejected work

These decisions are part of the plan so future architecture work can understand why the alternatives
were not selected.

### Keep ThreadLocal dynamic scope

`ThreadLocal` remains appropriate for synchronous current-context selection. Replacing it with a
coroutine context, global registry, explicit parameter on every DSL call, or another mechanism does
not itself improve Local equality, state observation, or View patching. Explicit capture/restore
continues at delayed and cross-session boundaries.

### Do not make all UiLocals tracked

The current Local model is intended for tree-scoped environment and service lookup. Automatic
tracking without compiler-provided component scopes would often move invalidation to a broad parent
scope while adding hidden subscription state. Frequently changing business values should continue
to use ViewCompose State or Flow and immutable Local values with meaningful equality.

A separate tracked-Local design requires an independent use case, invalidation model, public API
review, benchmarks, and plan. It is not a fallback for poor State ownership.

### Do not schedule Observation dependency-set reuse

Reusing one Observation and diffing old/new dependencies could reduce allocations, but it reaches
deeply into prepared composition commit/abort, concurrent invalidation, removed scopes, and observer
lifetime. Phase 0 may measure subscription churn, but this plan does not implement the optimization.

Reconsider only if retained Observation allocation and subscription add/remove work is a material
share of render CPU or allocation in at least two representative workloads. A triggered
investigation requires its own plan and rollback proof.

### Do not schedule derived-state equal-result suppression

Correct suppression requires deciding where and when a dirty derived calculation may run. Eager
calculation on the writer or Snapshot applying thread can block arbitrary producers and violates the
current lazy model. Deferring calculation without losing a required invalidation needs a broader
conditional-invalidation design.

Continue using explicit stable MutableState publication for expensive or equality-sensitive
derived values. Reconsider only for a demonstrated application workload and a separate design.

### Do not optimize environment changes independently

Density, font scale, locale, and layout direction changes are rare and can affect layout, text,
resources, constraints, and native View state. The current full rebind is conservative. Reusing one
`UiEnvironmentValues` instance per LocalSnapshot may be considered as part of Phase 3 allocation
work, but field-specific environment patching is not scheduled.

### Do not add VNode, NodeSpec, or LocalSnapshot object pools

Pooling would introduce mutable ownership, stale-data, rollback, and retention risks into objects
whose immutability and reference identity currently enable safe subtree skipping. Allocation work
must first use structural sharing and fewer creations, not recycled mutable instances.

### Do not merge independent RenderSessions

Lazy item, Pager page, overlay, and navigation sessions intentionally isolate remembered state,
effects, owners, mounted trees, failures, and disposal. Phase 4 may share only the platform frame
callback queue. It must not create shared Composer, SlotTable, Local map, renderer transaction, or
container ownership.

### Do not add snapshot collections or snapshotFlow in this optimization plan

Those are product capabilities, not necessary optimizations of current State and View rendering.
Use immutable collection values and external Flow ownership until a public use case justifies a
separate API and performance design.

### Do not redesign nullable mutation-policy merge here

The existing `null`-means-conflict protocol cannot express a successful merge to `null`, but fixing
it changes a public runtime contract and has little relationship to the measured hot paths in this
plan. Review a sealed merge-result contract separately before API stabilization; do not bundle it
with performance experiments.

### Do not keep a persistent-map dependency without evidence

The expected number of simultaneously active Locals is small. Phase 3 first removes per-node
snapshot-wrapper allocation. A new dependency or custom persistent data structure is rejected if
provider Map copying is not still material afterward.

## Validation matrix

| Area | Required validation |
| --- | --- |
| Snapshot/runtime | state, snapshot, conflict, merge, history pruning, nested apply, observation-thread, and composition-consistency unit tests |
| Animation/gesture | transition retarget/frame tests, Animatable arbitration/cancellation tests, anchored-drag state tests, and representative device animation/gesture checks |
| Locals | default/nullable/nested/batch/capture/restore tests plus lazy, overlay, and navigation refresh tests |
| Renderer | binding-plan, modifier-family, LayoutParams, transaction rollback, reused-View cleanup, AndroidView, semantics, insets, focus, and nested-scroll tests |
| Host scheduling | dispatcher, inactive/reactivate, direct render, disposal, cross-thread request, reentrancy, failure-isolation, and multi-session tests |
| Containers | delayed-session checklist scenarios, including empty diff, changed closure, changed Local, reorder, recycle, and disposal |
| Performance | R8 release build, targeted trace/counters, existing state/list/complex/navigation workloads, same-device before/after report, and policy gate |
| Documentation | canonical active pages, required Chinese mirrors for implemented durable changes, language and translation gates, site build where applicable, and `verifyDocumentationStructure` |

Minimum repository gates for each completed implementation slice:

```bash
./gradlew qaQuick
./gradlew qaRelease
./gradlew verifyDocumentationStructure
```

Run the focused module tests for the changed source before those aggregate gates. Run
`benchmarkRelease` or the narrower connected benchmark class on an unlocked, thermally controlled
device for every benchmark-gated phase. Run `qaFull` for visible, interaction, container, overlay,
navigation, input, animation, or lifecycle changes before declaring that phase complete.

## Documentation and release impact

This plan itself is temporary English-only project documentation. Implemented durable changes have
the following same-change documentation obligations:

- Snapshot or Observation semantics: runtime KDoc, compiled samples when Q3, runtime module manual,
  state architecture, state migration comparison, and both required locales.
- Local lookup or propagation semantics: widget-core KDoc, widget-core module manual, architecture
  environment rules, layout/environment migration comparison, and both required locales.
- Renderer patch or rollback behavior: renderer module manual, performance specification, relevant
  architecture pages, and both required locales.
- Host frame scheduling behavior: host-android KDoc/module manual, performance and session-container
  documents, and both required locales.
- Public diagnostics: canonical KDoc, Q-level classification, compiled sample when required,
  tooling/performance documentation, and both required locales.

Every pull request that changes production source or other publication input for a published
artifact adds one immutable `release/changes/<unique>.json` file and classifies each detected
artifact. Never hand-write dependency propagation.

## Completion criteria

This plan is complete when all of the following are true:

1. Phase 0 baseline evidence exists and retained diagnostics are documented.
2. Phases 1 through 3 have a recorded keep/revert decision and all retained changes pass their
   correctness and performance gates.
3. Phase 4 either passes its high-complexity keep gate or is explicitly stopped/reverted with
   evidence.
4. Phase 5 removes unused experimental diagnostics and documents the retained model.
5. Every deferred/rejected direction still reflects the final decision or links a superseding plan
   or ADR.
6. All production changes have required Changesets, public API documentation, samples, module
   manuals, active architecture/tooling/migration updates, and reviewed Chinese mirrors.
7. `qaQuick`, `qaRelease`, documentation gates, relevant focused tests, and required device gates
   pass at the final retained revision.
8. Durable conclusions are moved into current active documentation.
9. This plan and its final evidence ledger move to `docs/archive/`, and
   `docs/project/plans/README.md` is updated in the same change.

## Evidence ledger

Update this table at every phase decision. Do not replace failed or reverted evidence with a later
clean narrative.

| Date | Revision | Phase | Device/build and command | Result | Decision and next action |
| --- | --- | --- | --- | --- | --- |
| 2026-08-05 | Working tree | Planning | Repository source and active documentation review; no performance command run | Plan created; implementation unscheduled | Run Phase 0 baseline before any behavior change |

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-05 | Preserve ThreadLocal-based synchronous dynamic scope | It fits the current ownership model; replacement does not address measured work |
| 2026-08-05 | Prioritize atomic related-state publication and modifier-only View patches | They improve consistency or remove concrete redundant work without weakening boundaries |
| 2026-08-05 | Put diagnostics and representative baselines before complex implementation | High-complexity optimizations must be reversible and evidence-backed |
| 2026-08-05 | Permit shared frame scheduling but not shared RenderSession ownership | One platform callback may be shared; composition, locals, Views, failure, and disposal stay isolated |
| 2026-08-05 | Defer Observation reuse, derived-state suppression, environment partial patching, and tracked Locals | Their benefit is uncertain or their transaction/invalidation risk is disproportionate today |
| 2026-08-05 | Reject pooling and unmeasured persistent-map dependencies | Immutability, rollback, and maintainability are more valuable than speculative allocation savings |
